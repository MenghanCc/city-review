package com.cjj.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.cjj.dto.Result;
import com.cjj.entity.Shop;
import com.cjj.mapper.ShopMapper;
import com.cjj.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cjj.utils.RedisConstants;
import com.cjj.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.management.remote.rmi._RMIConnection_Stub;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.cjj.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 陈俊杰
 * @since 2026-6-3
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);

//        // 缓存击穿(互斥锁)
//        Shop shop = queryWithMutex(id);
//        if (shop == null) {
//            return Result.fail("店铺不存在！");
//        }

        // 逻辑过期
        Shop shop = queryWithLogicalExpire(id);

        return Result.ok(shop);
    }

    public Shop queryWithPassThrough(Long id) {
        // 从redis查询商铺信息缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 判断存在（在redis中是否存在）
        if (StrUtil.isNotBlank(shopJson)) {
//            return Result.ok(JSONUtil.toBean(shopJson,Shop.class));
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断命中的是否是空值（避免缓存穿透）
        if (shopJson != null) {
//            return Result.fail("店铺信息不存在");
            return null;
        }
        // 不存在根据id查询数据库
        Shop shop = getById(id);
        // 数据库中不存在
        if (shop == null) {
            // 将空值写入Redis（避免缓存穿透）
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return Result.fail("店铺不存在");
            return null;
        }
        // 数据库中存在,写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return Result.ok(shop);
        return shop;
    }

    public Shop queryWithMutex(Long id) {
        // 从redis查询商铺信息缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 判断存在（在redis中是否存在）
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断命中的是否是空值（避免缓存穿透）
        if (shopJson != null) {
            return null;
        }
        // 实现缓存重建
        // 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop;
        try {
            boolean islock = tryLock(lockKey);
            // 判断是否获取成功
            if (!islock) {
                // 如果失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 获取锁成功做DoubleCheck检测缓存是否存在
            String shopJson1 = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if (StrUtil.isNotBlank(shopJson1)) {
                return JSONUtil.toBean(shopJson1, Shop.class);
            }

            // 获取互斥锁成功，根据id查询数据库
            shop = getById(id);
            // 模拟重建的延时
//            Thread.sleep(200);
            // 数据库中不存在
            if (shop == null) {
                // 将空值写入Redis（避免缓存穿透）
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 数据库中存在,写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 释放互斥锁
            unlock(lockKey);
        }
        // 返回
        return shop;
    }

    // 获取锁的方法
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag); // 不直接使用flag防止拆箱,安全处理null
    }

    // 释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    // 逻辑过期
    public void saveShop2Redis(Long id, Long expireSeconds) {
        // 查询店铺数据
        Shop shop = getById(id);
        // 封装成逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    // 缓存重建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    // 通过设置逻辑过期时间解决缓存击穿
    public Shop queryWithLogicalExpire(Long id) {
        // 从redis查询商铺信息缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 判断命中，如果没命中
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        // 命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期则直接返回店铺信息
            return shop;
        }
        // 过期，需要缓存重建
        // 缓存重建
        // 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        // 判断是否获取成功
        if (lock) {
            // 获取成功，开启独立线程重建缓存
            // DCL：二次检查缓存是否已被其他线程重建
            try {
                String shopJson1 = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
                if (StrUtil.isNotBlank(shopJson1)) {
                    RedisData redisData1 = JSONUtil.toBean(shopJson1, RedisData.class);
                    JSONObject data1 = (JSONObject) redisData1.getData();
                    Shop newShop = JSONUtil.toBean(data1, Shop.class);
                    LocalDateTime expireTime1 = redisData1.getExpireTime();
                    // 如果缓存未过期，直接返回新缓存的数据
                    if (expireTime1.isAfter(LocalDateTime.now())) {
                        return newShop;
                    }
                }
                // 缓存仍未重建或已过期，开启独立线程重建缓存
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        // 重建缓存
                        saveShop2Redis(id, CACHE_SHOP_TTL);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        // 释放锁
                        unlock(lockKey);
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return shop;
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

        // 更新数据库
        updateById(shop);

        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }
}
