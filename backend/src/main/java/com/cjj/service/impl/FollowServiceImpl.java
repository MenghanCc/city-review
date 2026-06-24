package com.cjj.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cjj.dto.Result;
import com.cjj.dto.UserDTO;
import com.cjj.entity.Follow;
import com.cjj.mapper.FollowMapper;
import com.cjj.service.IFollowService;
import com.cjj.service.IUserService;
import com.cjj.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.cjj.utils.RedisConstants.*;

/**
 * city-review 好友关注服务实现
 *
 * Redis 数据结构选型说明：
 *   - follow:{userId} → Set   → 存储该用户关注的所有用户 ID
 *   - fans:{userId}   → Set   → 存储该用户的所有粉丝 ID
 *
 * 为什么用 Set 而不是 SortedSet？
 *   关注关系只需"是/否"，无序，Set 的 SISMEMBER O(1) 判断
 *   比 SortedSet 节省 score 字段内存。
 *
 * 为什么用 Set 而不是纯 MySQL？
 *   1. SISMEMBER O(1) vs MySQL 索引扫描
 *   2. SINTER 一行求共同关注 vs MySQL JOIN
 *   3. 高并发下 Redis 抗压远优于 MySQL
 *
 * 数据双写策略：先写 MySQL（持久化），再写 Redis（缓存加速）
 */
@Slf4j
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    /**
     * 关注 / 取关
     * @param followUserId 目标用户ID
     * @param isFollow     true=关注, false=取关
     */
    @Transactional(rollbackFor = Exception.class)
    public Result follow(Long followUserId, Boolean isFollow) {
        UserDTO me = UserHolder.getUser();
        if (me == null) {
            return Result.fail(401, "请先登录");
        }
        Long userId = me.getId();

        if (userId.equals(followUserId)) {
            return Result.fail("不能关注自己");
        }

        if (isFollow) {
            // --- 关注 ---
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            follow.setCreateTime(LocalDateTime.now());
            // 1. 先写 MySQL
            save(follow);
            // 2. 再写 Redis
            //    SADD follow:{userId} followUserId —— 我的关注列表
            stringRedisTemplate.opsForSet().add(FOLLOW_KEY + userId, followUserId.toString());
            //    SADD fans:{followUserId} userId —— 对方的粉丝列表
            stringRedisTemplate.opsForSet().add(FANS_KEY + followUserId, userId.toString());
            log.info("city-review 关注成功 → userId={} follow={}", userId, followUserId);
        } else {
            // --- 取关 ---
            // 1. 先删 MySQL
            remove(Wrappers.<Follow>lambdaQuery()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId));
            // 2. 再删 Redis
            stringRedisTemplate.opsForSet().remove(FOLLOW_KEY + userId, followUserId.toString());
            stringRedisTemplate.opsForSet().remove(FANS_KEY + followUserId, userId.toString());
            log.info("city-review 取关成功 → userId={} unfollow={}", userId, followUserId);
        }
        return Result.ok();
    }

    /**
     * 判断是否已关注
     */
    public Result isFollow(Long followUserId) {
        UserDTO me = UserHolder.getUser();
        if (me == null) {
            return Result.fail(401, "请先登录");
        }
        // SISMEMBER O(1) 时间复杂度
        Boolean isMember = stringRedisTemplate.opsForSet()
                .isMember(FOLLOW_KEY + me.getId(), followUserId.toString());
        return Result.ok(Boolean.TRUE.equals(isMember));
    }

    /**
     * 查询共同关注
     * 为什么用 SINTER？
     *   Redis 一行命令求集合交集，MySQL 需要自关联子查询（两个人都关注的人）
     */
    public Result commonFollow(Long targetUserId) {
        UserDTO me = UserHolder.getUser();
        if (me == null) {
            return Result.fail(401, "请先登录");
        }
        // SINTER follow:me follow:targetUser — 交集 = 共同关注
        Set<String> commonSet = stringRedisTemplate.opsForSet()
                .intersect(FOLLOW_KEY + me.getId(), FOLLOW_KEY + targetUserId);

        if (commonSet == null || commonSet.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 批量查询用户信息
        List<Long> ids = commonSet.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(u -> BeanUtil.copyProperties(u, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(users);
    }

    /**
     * 查询关注列表
     */
    public Result followList(Long userId) {
        // 优先查 Redis
        Set<String> followSet = stringRedisTemplate.opsForSet().members(FOLLOW_KEY + userId);
        if (followSet == null || followSet.isEmpty()) {
            // Redis 为空，查 MySQL 并回写
            List<Follow> follows = getBaseMapper().selectList(
                    Wrappers.<Follow>lambdaQuery().eq(Follow::getUserId, userId));
            if (follows.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }
            for (Follow f : follows) {
                stringRedisTemplate.opsForSet().add(FOLLOW_KEY + userId, f.getFollowUserId().toString());
            }
            followSet = follows.stream().map(f -> f.getFollowUserId().toString()).collect(Collectors.toSet());
        }

        List<Long> ids = followSet.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(u -> BeanUtil.copyProperties(u, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(users);
    }

    /**
     * 查询粉丝列表
     */
    public Result fansList(Long userId) {
        Set<String> fansSet = stringRedisTemplate.opsForSet().members(FANS_KEY + userId);
        // Redis 为空则查 MySQL followers who follow this user
        if (fansSet == null || fansSet.isEmpty()) {
            List<Follow> fans = getBaseMapper().selectList(
                    Wrappers.<Follow>lambdaQuery().eq(Follow::getFollowUserId, userId));
            if (fans.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }
            for (Follow f : fans) {
                stringRedisTemplate.opsForSet().add(FANS_KEY + userId, f.getUserId().toString());
            }
            fansSet = fans.stream().map(f -> f.getUserId().toString()).collect(Collectors.toSet());
        }

        List<Long> ids = fansSet.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(u -> BeanUtil.copyProperties(u, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(users);
    }
}
