package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);

        // 缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
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
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }finally{
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
