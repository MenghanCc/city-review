package com.cjj.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cjj.dto.Result;
import com.cjj.dto.ScrollResult;
import com.cjj.dto.UserDTO;
import com.cjj.entity.Blog;
import com.cjj.entity.Shop;
import com.cjj.entity.User;
import com.cjj.mapper.BlogMapper;
import com.cjj.mapper.ShopMapper;
import com.cjj.service.IBlogService;
import com.cjj.service.IFollowService;
import com.cjj.service.IUserService;
import com.cjj.utils.SystemConstants;
import com.cjj.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.cjj.utils.RedisConstants.*;

/**
 * city-review 探店笔记服务实现
 *
 * Redis 数据结构选型：
 *   点赞排行榜 → SortedSet（blog:liked:rank:{blogId}）
 *     - score = 毫秒时间戳，member = userId
 *     - ZREVRANGE 按时间倒序获取排行榜
 *     - ZSCORE 判断是否已点赞（O(1)）
 *     - ZADD 自动去重
 *
 *   点赞用户列表 → Set（blog:liked:user:{blogId}）
 *     - 快速去重判断 SISMEMBER（O(1)）
 *
 *   Feed 收件箱 → SortedSet（feed:{userId}）
 *     - score = 笔记发布时间戳，member = blogId
 *     - 推模式（Push）：发布笔记时推送到所有粉丝的收件箱
 *     - ZREVRANGEBYSCORE 滚动分页
 */
@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private com.cjj.service.IShopService shopService;

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private IFollowService followService;

    /**
     * 发布探店笔记
     * 1. 校验登录、商户存在、评分合法
     * 2. 保存帖子
     * 3. 更新商户平均评分
     * 4. 推送到粉丝 Feed 收件箱
     */
    public Result saveBlog(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(401, "请先登录");
        }

        // 校验商户是否存在
        if (blog.getShopId() == null) {
            return Result.fail("shopId 不能为空");
        }
        Shop shop = shopService.getById(blog.getShopId());
        if (shop == null) {
            return Result.fail("商户不存在");
        }

        // 校验评分
        if (blog.getScore() != null) {
            double s = blog.getScore().doubleValue();
            if (s < 1.0 || s > 5.0) {
                return Result.fail("评分必须在 1.0 ~ 5.0 之间");
            }
        }

        blog.setUserId(user.getId());
        boolean saved = save(blog);
        if (!saved) {
            return Result.fail("发布失败");
        }

        // 更新商户平均评分
        if (blog.getScore() != null) {
            updateShopAvgScore(blog.getShopId());
        }

        // 清除商户详情缓存，让"大家说"展示最新帖子
        stringRedisTemplate.delete("shop:detail:" + blog.getShopId());

        // --- 推模式 Feed 流：推送到所有粉丝的收件箱 ---
        String fansKey = FANS_KEY + user.getId();
        Set<String> fans = stringRedisTemplate.opsForSet().members(fansKey);
        if (fans != null && !fans.isEmpty()) {
            long timestamp = System.currentTimeMillis();
            for (String fanId : fans) {
                String feedKey = FEED_KEY + fanId;
                stringRedisTemplate.opsForZSet()
                        .add(feedKey, blog.getId().toString(), timestamp);
                stringRedisTemplate.expire(feedKey, FEED_TTL, java.util.concurrent.TimeUnit.DAYS);
            }
        }

        // 也推给自己
        String myFeedKey = FEED_KEY + user.getId();
        stringRedisTemplate.opsForZSet()
                .add(myFeedKey, blog.getId().toString(), System.currentTimeMillis());
        stringRedisTemplate.expire(myFeedKey, FEED_TTL, java.util.concurrent.TimeUnit.DAYS);

        log.info("city-review 笔记发布成功 → blogId={}, userId={}, shopId={}, score={}",
                blog.getId(), user.getId(), blog.getShopId(), blog.getScore());
        return Result.ok(blog.getId());
    }

    /**
     * 更新商户平均评分
     * tb_shop.score 存储为 score × 10 的整数（如 4.8 → 48）
     */
    private void updateShopAvgScore(Long shopId) {
        List<Blog> blogs = getBaseMapper().selectList(
                Wrappers.<Blog>lambdaQuery()
                        .eq(Blog::getShopId, shopId)
                        .isNotNull(Blog::getScore));
        if (blogs.isEmpty()) return;

        double avg = blogs.stream()
                .mapToDouble(b -> b.getScore() != null ? b.getScore().doubleValue() : 0)
                .average().orElse(0);

        // 商户评分存储为 int（×10），如 4.8 → 48
        Shop updateShop = new Shop();
        updateShop.setId(shopId);
        updateShop.setScore((int) Math.round(avg * 10));
        shopMapper.updateById(updateShop);

        log.info("city-review 商户平均评分更新 → shopId={}, avg={}, stored={}", shopId, avg, updateShop.getScore());
    }

    /**
     * 点赞 / 取消点赞
     *
     * 为什么用 SortedSet？
     *   1. 天然按时间排序（score=毫秒时间戳）
     *   2. 自动去重（同一用户重复 ZADD 只更新 score）
     *   3. ZREVRANGE 获取 Top N 排行榜
     *   4. ZSCORE O(1) 判断是否已点赞
     */
    public Result likeBlog(Long blogId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(401, "请先登录");
        }
        Long userId = user.getId();
        String rankKey = BLOG_LIKED_RANK_KEY + blogId;
        String userKey = BLOG_LIKED_USER_KEY + blogId;

        // 判断是否已点赞
        Double score = stringRedisTemplate.opsForZSet().score(rankKey, userId.toString());
        if (score != null) {
            // 已点赞 → 取消点赞
            stringRedisTemplate.opsForZSet().remove(rankKey, userId.toString());
            stringRedisTemplate.opsForSet().remove(userKey, userId.toString());
            // 同步更新 MySQL 点赞数
            update(Wrappers.<Blog>lambdaUpdate()
                    .setSql("liked = liked - 1").eq(Blog::getId, blogId));
            log.info("city-review 取消点赞 → blogId={}, userId={}", blogId, userId);
            return Result.ok("已取消点赞");
        }

        // 未点赞 → 点赞
        long timestamp = System.currentTimeMillis();
        // ZADD blog:liked:rank:{blogId} timestamp userId
        stringRedisTemplate.opsForZSet().add(rankKey, userId.toString(), timestamp);
        // SADD blog:liked:user:{blogId} userId
        stringRedisTemplate.opsForSet().add(userKey, userId.toString());
        // 同步更新 MySQL 点赞数
        update(Wrappers.<Blog>lambdaUpdate()
                .setSql("liked = liked + 1").eq(Blog::getId, blogId));
        log.info("city-review 点赞成功 → blogId={}, userId={}", blogId, userId);
        return Result.ok("点赞成功");
    }

    /**
     * 查询点赞排行榜（Top N，按时间倒序）
     */
    public Result queryBlogLikes(Long blogId, Integer topN) {
        if (topN == null || topN <= 0) {
            topN = 5;
        }
        String rankKey = BLOG_LIKED_RANK_KEY + blogId;
        // ZREVRANGE 按 score 从高到低取前 N 个
        Set<String> topUserIds = stringRedisTemplate.opsForZSet()
                .reverseRange(rankKey, 0, topN - 1);

        if (topUserIds == null || topUserIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = topUserIds.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(u -> BeanUtil.copyProperties(u, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(users);
    }

    /**
     * 查询关注流（Feed 收件箱）
     * 滚动分页：每次返回最后一条的时间戳作为下次查询的 offset
     */
    public Result queryFeed(Long max, Integer offset) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(401, "请先登录");
        }
        Long userId = user.getId();
        String feedKey = FEED_KEY + userId;

        // ZREVRANGEBYSCORE：按 score 从大到小（最新的在前）
        Set<ZSetOperations.TypedTuple<String>> typedTuples =
                stringRedisTemplate.opsForZSet()
                        .reverseRangeByScoreWithScores(feedKey, 0, max == null ? Long.MAX_VALUE : max,
                                offset == null ? 0 : offset, SystemConstants.DEFAULT_PAGE_SIZE);

        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> blogIds = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 0;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            blogIds.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }

        // 批量查笔记并填充用户信息
        List<Blog> blogs = listByIds(blogIds);
        for (Blog blog : blogs) {
            User u = userService.getById(blog.getUserId());
            if (u != null) {
                blog.setName(u.getNickName());
                blog.setIcon(u.getIcon());
            }
            // 判断当前用户是否已点赞
            Double score = stringRedisTemplate.opsForZSet()
                    .score(BLOG_LIKED_RANK_KEY + blog.getId(), userId.toString());
            blog.setIsLiked(score != null);
        }

        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(os);
        return Result.ok(scrollResult);
    }

    /**
     * 查询热门笔记（按点赞数排序）
     */
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = getBaseMapper().selectPage(
                new Page<>(current, SystemConstants.MAX_PAGE_SIZE),
                Wrappers.<Blog>lambdaQuery().orderByDesc(Blog::getLiked));

        List<Blog> records = page.getRecords();
        UserDTO me = UserHolder.getUser();

        for (Blog blog : records) {
            User user = userService.getById(blog.getUserId());
            if (user != null) {
                blog.setName(user.getNickName());
                blog.setIcon(user.getIcon());
            }
            // 判断是否已点赞
            if (me != null) {
                Double score = stringRedisTemplate.opsForZSet()
                        .score(BLOG_LIKED_RANK_KEY + blog.getId(), me.getId().toString());
                blog.setIsLiked(score != null);
            }
        }
        return Result.ok(records);
    }

    /**
     * 查询我的笔记
     */
    public Result queryMyBlog(Integer current) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(401, "请先登录");
        }
        Page<Blog> page = getBaseMapper().selectPage(
                new Page<>(current, SystemConstants.MAX_PAGE_SIZE),
                Wrappers.<Blog>lambdaQuery().eq(Blog::getUserId, user.getId()));
        return Result.ok(page.getRecords());
    }

    /**
     * 查询笔记详情
     */
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        User user = userService.getById(blog.getUserId());
        if (user != null) {
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
        // 关联商户名
        com.cjj.entity.Shop shop = shopService.getById(blog.getShopId());
        if (shop != null) {
            blog.setShopName(shop.getName());
        }
        UserDTO me = UserHolder.getUser();
        if (me != null) {
            Double score = stringRedisTemplate.opsForZSet()
                    .score(BLOG_LIKED_RANK_KEY + id, me.getId().toString());
            blog.setIsLiked(score != null);
        }
        return Result.ok(blog);
    }
}
