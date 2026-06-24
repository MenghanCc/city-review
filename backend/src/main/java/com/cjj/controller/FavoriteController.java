package com.cjj.controller;

import com.cjj.dto.Result;
import com.cjj.dto.UserDTO;
import com.cjj.entity.Shop;
import com.cjj.mapper.ShopMapper;
import com.cjj.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.cjj.utils.RedisConstants.FAVORITE_KEY;

@RestController
@RequestMapping("/api/favorite")
public class FavoriteController {

    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private ShopMapper shopMapper;
    @Resource private com.cjj.mapper.FavoriteMapper favoriteMapper;

    /** 收藏/取消收藏切换（双写 MySQL + Redis） */
    @PutMapping("/{shopId}")
    public Result toggle(@PathVariable Long shopId) {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");
        String key = FAVORITE_KEY + me.getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, shopId.toString());
        if (Boolean.TRUE.equals(isMember)) {
            stringRedisTemplate.opsForSet().remove(key, shopId.toString());
            favoriteMapper.delete(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.cjj.entity.Favorite>lambdaQuery()
                    .eq(com.cjj.entity.Favorite::getUserId, me.getId())
                    .eq(com.cjj.entity.Favorite::getShopId, shopId));
            return Result.ok(false);
        } else {
            stringRedisTemplate.opsForSet().add(key, shopId.toString());
            com.cjj.entity.Favorite fav = new com.cjj.entity.Favorite();
            fav.setUserId(me.getId()); fav.setShopId(shopId);
            fav.setCreatedAt(java.time.LocalDateTime.now());
            favoriteMapper.insert(fav);
            return Result.ok(true);
        }
    }

    /** 是否已收藏 */
    @GetMapping("/check/{shopId}")
    public Result check(@PathVariable Long shopId) {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.ok(false);
        String key = FAVORITE_KEY + me.getId();
        Boolean inRedis = stringRedisTemplate.opsForSet().isMember(key, shopId.toString());
        if (Boolean.TRUE.equals(inRedis)) return Result.ok(true);
        // Redis 缺失，查 MySQL 兜底
        long count = favoriteMapper.selectCount(
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.cjj.entity.Favorite>lambdaQuery()
                .eq(com.cjj.entity.Favorite::getUserId, me.getId())
                .eq(com.cjj.entity.Favorite::getShopId, shopId));
        if (count > 0) {
            stringRedisTemplate.opsForSet().add(key, shopId.toString()); // 回写 Redis
            return Result.ok(true);
        }
        return Result.ok(false);
    }

    /** 我的收藏列表（Redis 优先，MySQL 兜底） */
    @GetMapping("/my")
    public Result myFavorites() {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");
        String key = FAVORITE_KEY + me.getId();
        Set<String> ids = stringRedisTemplate.opsForSet().members(key);
        if (ids == null || ids.isEmpty()) {
            // Redis 为空，从 MySQL 恢复
            List<com.cjj.entity.Favorite> favs = favoriteMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.cjj.entity.Favorite>lambdaQuery()
                    .eq(com.cjj.entity.Favorite::getUserId, me.getId()));
            if (favs.isEmpty()) return Result.ok(Collections.emptyList());
            for (com.cjj.entity.Favorite f : favs) {
                stringRedisTemplate.opsForSet().add(key, f.getShopId().toString());
            }
            ids = favs.stream().map(f -> f.getShopId().toString()).collect(Collectors.toSet());
        }

        List<Long> shopIds = ids.stream().map(Long::valueOf).collect(Collectors.toList());
        List<Shop> shops = shopMapper.selectBatchIds(shopIds);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Shop s : shops) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("address", s.getAddress());
            m.put("area", s.getArea());
            m.put("city", s.getCity());
            m.put("score", s.getScore());
            m.put("avgPrice", s.getAvgPrice());
            m.put("coverImg", s.getCoverImg());
            list.add(m);
        }
        return Result.ok(list);
    }
}
