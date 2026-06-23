package com.cjj.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cjj.dto.Result;
import com.cjj.dto.UserDTO;
import com.cjj.entity.Blog;
import com.cjj.entity.Shop;
import com.cjj.entity.User;
import com.cjj.service.IShopService;
import com.cjj.service.IUserService;
import com.cjj.service.impl.BlogServiceImpl;
import com.cjj.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.cjj.utils.RedisConstants.BLOG_LIKED_RANK_KEY;

/**
 * city-review 探店笔记控制器
 */
@RestController
@RequestMapping("/api/blog")
public class BlogController {

    @Resource
    private BlogServiceImpl blogService;

    @Resource
    private IUserService userService;

    @Resource
    private IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 首页帖子流（城市过滤 + 分页 + 关联商户和用户 + 排序）
     * GET /api/blog/list?city=武汉&page=1&size=10&sort=time|likes
     */
    @GetMapping("/list")
    public Result listBlogs(
            @RequestParam(value = "city", defaultValue = "武汉") String city,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "sort", defaultValue = "time") String sort) {

        // 1. 查出当前城市下的所有店铺 ID
        List<Long> cityShopIds = shopService.getBaseMapper().selectList(
                Wrappers.<Shop>lambdaQuery().eq(Shop::getCity, city).select(Shop::getId))
                .stream().map(Shop::getId).collect(Collectors.toList());

        if (cityShopIds.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("records", Collections.emptyList());
            empty.put("total", 0);
            empty.put("pages", 0);
            return Result.ok(empty);
        }

        // 2. 只查这些店铺关联的笔记，支持按时间/点赞排序
        Page<Blog> blogPage;
        if ("likes".equals(sort)) {
            blogPage = blogService.getBaseMapper().selectPage(
                    new Page<>(page, size),
                    Wrappers.<Blog>lambdaQuery()
                            .in(Blog::getShopId, cityShopIds)
                            .orderByDesc(Blog::getLiked));
        } else {
            blogPage = blogService.getBaseMapper().selectPage(
                    new Page<>(page, size),
                    Wrappers.<Blog>lambdaQuery()
                            .in(Blog::getShopId, cityShopIds)
                            .orderByDesc(Blog::getCreateTime));
        }

        // 3. 批量预加载用户和店铺（减少 N+1）
        Set<Long> userIds = new HashSet<>();
        Set<Long> shopIds = new HashSet<>();
        blogPage.getRecords().forEach(b -> { userIds.add(b.getUserId()); shopIds.add(b.getShopId()); });

        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Shop> shopMap = shopService.listByIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));

        List<Map<String, Object>> enriched = blogPage.getRecords().stream().map(blog -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", blog.getId());
            m.put("title", blog.getTitle());
            m.put("content", blog.getContent());
            m.put("images", blog.getImages());
            m.put("liked", blog.getLiked());
            m.put("comments", blog.getComments());
            m.put("createTime", blog.getCreateTime());

            User user = userMap.get(blog.getUserId());
            m.put("userId", blog.getUserId());
            m.put("nickname", user != null ? user.getNickName() : "匿名");
            m.put("avatar", user != null ? user.getIcon() : "");

            Shop shop = shopMap.get(blog.getShopId());
            m.put("shopId", blog.getShopId());
            m.put("shopName", shop != null ? shop.getName() : "未知商户");

            // 当前用户是否已点赞
            UserDTO me = UserHolder.getUser();
            if (me != null) {
                Double score = stringRedisTemplate.opsForZSet()
                        .score(BLOG_LIKED_RANK_KEY + blog.getId(), me.getId().toString());
                m.put("isLiked", score != null);
            } else {
                m.put("isLiked", false);
            }

            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("records", enriched);
        result.put("total", blogPage.getTotal());
        result.put("pages", blogPage.getPages());
        return Result.ok(result);
    }

    /**
     * 发布探店笔记
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 点赞 / 取消点赞（Toggle 模式）
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 点赞排行榜（Top N）
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id,
                                  @RequestParam(value = "top", defaultValue = "5") Integer top) {
        return blogService.queryBlogLikes(id, top);
    }

    /**
     * 关注流（Feed 收件箱）滚动分页
     * @param max    上次查询的最小时间戳（首次不传）
     * @param offset 偏移量（同时间戳的跳过条数）
     */
    @GetMapping("/of/follow")
    public Result queryFeed(@RequestParam(value = "max", required = false) Long max,
                            @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return blogService.queryFeed(max, offset);
    }

    /**
     * 热门笔记
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /**
     * 我的笔记
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyBlog(current);
    }

    /**
     * 笔记详情
     */
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    /**
     * 商户关联帖子（分页，含用户信息）
     * GET /api/blog/shop/{shopId}?page=1&size=10
     */
    @GetMapping("/shop/{shopId}")
    public Result listByShop(@PathVariable("shopId") Long shopId,
                              @RequestParam(value = "page", defaultValue = "1") Integer page,
                              @RequestParam(value = "size", defaultValue = "10") Integer size) {
        Page<Blog> blogPage = blogService.getBaseMapper().selectPage(
                new Page<>(page, size),
                Wrappers.<Blog>lambdaQuery()
                        .eq(Blog::getShopId, shopId)
                        .orderByDesc(Blog::getCreateTime));

        // 批量预加载用户信息
        Set<Long> userIds = blogPage.getRecords().stream()
                .map(Blog::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 检查当前用户点赞状态
        UserDTO me = UserHolder.getUser();

        List<Map<String, Object>> enriched = blogPage.getRecords().stream().map(blog -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", blog.getId());
            m.put("shopId", blog.getShopId());
            m.put("title", blog.getTitle());
            m.put("content", blog.getContent());
            m.put("images", blog.getImages());
            m.put("liked", blog.getLiked());
            m.put("comments", blog.getComments());
            m.put("score", blog.getScore());
            m.put("createTime", blog.getCreateTime());

            User u = userMap.get(blog.getUserId());
            m.put("name", u != null ? u.getNickName() : "匿名");
            m.put("icon", u != null ? u.getIcon() : "");

            if (me != null) {
                Double score = stringRedisTemplate.opsForZSet()
                        .score(BLOG_LIKED_RANK_KEY + blog.getId(), me.getId().toString());
                m.put("isLiked", score != null);
            } else {
                m.put("isLiked", false);
            }

            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("records", enriched);
        result.put("total", blogPage.getTotal());
        result.put("pages", blogPage.getPages());
        return Result.ok(result);
    }
}
