package com.cjj.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cjj.dto.Result;
import com.cjj.entity.Shop;
import com.cjj.mapper.ShopMapper;
import com.cjj.service.IShopService;
import com.cjj.utils.CacheClient;
import com.cjj.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.cjj.utils.RedisConstants.*;

/**
 * city-review 商户服务实现
 *
 * 三大缓存策略（CacheClient 封装）：
 *   缓存穿透 → 空对象缓存（短 TTL）
 *   缓存击穿 → setnx 分布式锁互斥重建
 *   缓存雪崩 → TTL 随机偏移
 *
 * Redis GEO（附近商户）：
 *   为什么用 GEO 而不是 MySQL 经纬度计算？
 *     GEOADD + GEOSEARCH 是 Redis 原生地理位置操作，底层使用 GeoHash 编码，
 *     计算效率远超 MySQL 的 SIN/COS 球面距离公式。
 *
 * Redis HyperLogLog（UV 统计）：
 *   为什么用 HyperLogLog 而不是 Set？
 *     100 万 UV，Set ≈ 80MB，HyperLogLog ≈ 12KB（节省 99.9% 内存）
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 启动时自动将商户坐标加载到 Redis GEO
     */
    @PostConstruct
    public void initGeoData() {
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(SHOP_GEO_KEY))) {
                log.info("city-review GEO 数据已存在，跳过初始化");
                return;
            }
            loadAllShopsToGeo();
            log.info("city-review GEO 初始化完成");
        } catch (Exception e) {
            log.warn("city-review GEO 初始化失败（Redis 未就绪），将延迟加载", e);
        }
    }

    // ==================== 缓存查询 ====================

    @Override
    public Result queryById(Long id) {
        // 使用 CacheClient 防缓存穿透（当前默认策略）
        // 可选策略：
        //   - queryWithMutex()  → 防缓存击穿（setnx 分布式锁）
        //   - queryWithLogicalExpire() → 防缓存击穿（逻辑过期 + 异步重建）
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class,
                        this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }

        // 记录 UV（每次查询商户详情时异步记录）
        recordUVAsync(id);

        return Result.ok(shop);
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
        // 删除缓存（写操作使缓存失效）
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        // 更新 Geo 坐标（如果经纬度发生了变化）
        if (shop.getX() != null && shop.getY() != null) {
            addShopToGeo(shop);
        }
        return Result.ok();
    }

    // ==================== 功能四：附近商户（Redis GEO） ====================

    /**
     * 查询附近商户
     *
     * GEO 核心流程：
     *   1. GEOSEARCH 按距离搜索（geoHash 高效计算）
     *   2. 返回商户 ID + 距离
     *   3. Java 层分页
     */
    @Override
    public Result queryNearbyShops(Double x, Double y, Integer radius, Integer current) {
        if (x == null || y == null) {
            return Result.fail("请提供经纬度信息");
        }
        if (radius == null || radius <= 0) {
            radius = SystemConstants.DEFAULT_GEO_RADIUS;
        }
        if (current == null || current <= 0) {
            current = 1;
        }

        String geoKey = SHOP_GEO_KEY;
        // 如果 GEO Key 不存在，从 MySQL 加载所有商户坐标
        Boolean hasKey = stringRedisTemplate.hasKey(geoKey);
        if (!Boolean.TRUE.equals(hasKey)) {
            loadAllShopsToGeo();
        }

        // GEOSEARCH：按经纬度搜索半径范围内的商户，返回距离
        // 注意：Spring Data Redis 2.3.x 中 GEOSEARCH 可能不可用，使用 GEORADIUS 替代
        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                stringRedisTemplate.opsForGeo().radius(
                        geoKey,
                        new Circle(new Point(x, y), new Distance(radius, Metrics.KILOMETERS)),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance()
                                .sortAscending()
                );

        if (results == null || results.getContent().isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 收集结果并按距离分页
        List<Map<String, Object>> shopList = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results.getContent()) {
            String shopIdStr = result.getContent().getName();
            double distance = result.getDistance().getValue();
            Map<String, Object> item = new HashMap<>();
            item.put("shopId", Long.valueOf(shopIdStr));
            item.put("distance", Math.round(distance * 1000.0 * 100.0) / 100.0); // 转为米，保留2位小数
            shopList.add(item);
        }

        // Java 层分页
        int offset = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = Math.min(offset + SystemConstants.DEFAULT_PAGE_SIZE, shopList.size());
        if (offset >= shopList.size()) {
            return Result.ok(Collections.emptyList());
        }
        List<Map<String, Object>> pageList = shopList.subList(offset, end);

        // 批量查询商户详情
        List<Long> shopIds = pageList.stream()
                .map(m -> (Long) m.get("shopId"))
                .collect(Collectors.toList());
        List<Shop> shops = listByIds(shopIds);

        // 填充距离
        for (Shop shop : shops) {
            for (Map<String, Object> m : pageList) {
                if (m.get("shopId").equals(shop.getId())) {
                    shop.setDistance((Double) m.get("distance"));
                    break;
                }
            }
        }

        return Result.ok(shops);
    }

    /**
     * 加载所有商户到 Redis Geo
     */
    public void loadAllShopsToGeo() {
        List<Shop> shops = list();
        String geoKey = SHOP_GEO_KEY;
        for (Shop shop : shops) {
            if (shop.getX() != null && shop.getY() != null) {
                addShopToGeo(shop);
            }
        }
        log.info("city-review Geo 初始化完成 → 加载 {} 个商户坐标", shops.size());
    }

    private void addShopToGeo(Shop shop) {
        stringRedisTemplate.opsForGeo().add(
                SHOP_GEO_KEY,
                new Point(shop.getX(), shop.getY()),
                shop.getId().toString()
        );
    }

    // ==================== 功能五：UV 统计（Redis HyperLogLog） ====================

    /**
     * 记录商户 UV
     * 使用 HyperLogLog 而非 Set：
     *   - 100 万独立用户，Set ≈ 80MB，HyperLogLog ≈ 12KB
     *   - 允许 0.81% 统计误差
     * Key 按天分片：uv:shop:{shopId}:{yyyyMMdd}
     */
    @Override
    public Result recordUV(Long shopId) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uvKey = UV_SHOP_KEY + shopId + ":" + today;
        // PFADD：记录独立访客，已存在则忽略
        stringRedisTemplate.opsForHyperLogLog().add(uvKey, "0");
        stringRedisTemplate.expire(uvKey, UV_SHOP_TTL, TimeUnit.DAYS);
        return Result.ok();
    }

    /**
     * 异步记录 UV（不阻塞主流程）
     */
    private void recordUVAsync(Long shopId) {
        new Thread(() -> {
            try {
                String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                String uvKey = UV_SHOP_KEY + shopId + ":" + today;
                stringRedisTemplate.opsForHyperLogLog().add(uvKey, "0");
                stringRedisTemplate.expire(uvKey, UV_SHOP_TTL, TimeUnit.DAYS);
            } catch (Exception ignored) {
            }
        }).start();
    }

    /**
     * 查询商户今日 UV
     */
    public Result queryUV(Long shopId) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uvKey = UV_SHOP_KEY + shopId + ":" + today;
        // PFCOUNT：返回近似独立访客数
        Long uv = stringRedisTemplate.opsForHyperLogLog().size(uvKey);
        return Result.ok(uv == null ? 0 : uv);
    }
}
