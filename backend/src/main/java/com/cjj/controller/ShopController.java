package com.cjj.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cjj.dto.Result;
import com.cjj.entity.Shop;
import com.cjj.service.IShopService;
import com.cjj.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * city-review 商户控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    @Resource
    private com.cjj.mapper.ShopTypeMapper shopTypeMapper;

    @Resource
    private com.cjj.mapper.BlogMapper blogMapper;

    @Resource
    private com.cjj.service.IUserService userService;

    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    /**
     * 商户列表（强制城市过滤 + 可选分类/名称筛选）
     * GET /api/shop/list?city=武汉&category=美食&name=茶餐厅
     */
    @GetMapping("/list")
    public Result listShops(
            @RequestParam(value = "city", defaultValue = "武汉") String city,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "name", required = false) String name) {

        log.info("city-review 查询商户 → 城市={}, 分类={}, 名称={}", city, category, name);

        LambdaQueryWrapper<Shop> wrapper = Wrappers.<Shop>lambdaQuery();
        // 城市必过滤
        wrapper.eq(Shop::getCity, city);

        // 分类名 → type_id
        if (category != null && !category.isEmpty()) {
            com.cjj.entity.ShopType st = shopTypeMapper.selectOne(
                    Wrappers.<com.cjj.entity.ShopType>lambdaQuery()
                            .eq(com.cjj.entity.ShopType::getName, category));
            if (st != null) {
                wrapper.eq(Shop::getTypeId, st.getId());
            } else {
                // 无此分类，返回空
                return Result.ok(java.util.Collections.emptyList());
            }
        }

        // 名称模糊搜索
        if (name != null && !name.isEmpty()) {
            wrapper.like(Shop::getName, name);
        }

        List<Shop> shops = shopService.getBaseMapper().selectList(wrapper);
        return Result.ok(shops);
    }

    /**
     * 商户详情（增强版：含分类名 + 关联帖子 + Redis 缓存 30 分钟）
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        // 1. 查 Redis 缓存
        String cacheKey = "shop:detail:" + id;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return Result.ok(cn.hutool.json.JSONUtil.toBean(cached, java.util.Map.class));
        }

        // 2. 查 MySQL
        Shop shop = shopService.getById(id);
        if (shop == null) return Result.fail("商户不存在");

        // 3. 组装数据
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", shop.getId());
        data.put("name", shop.getName());
        data.put("city", shop.getCity());
        data.put("address", shop.getAddress());
        data.put("area", shop.getArea());
        data.put("x", shop.getX());
        data.put("y", shop.getY());
        data.put("avgPrice", shop.getAvgPrice());
        data.put("score", shop.getScore());
        data.put("sold", shop.getSold());
        data.put("openHours", shop.getOpenHours());
        data.put("phone", shop.getPhone());
        data.put("description", shop.getDescription());
        data.put("coverImg", shop.getCoverImg());
        data.put("images", shop.getImages());

        // 分类名
        com.cjj.entity.ShopType st = shopTypeMapper.selectById(shop.getTypeId());
        data.put("categoryName", st != null ? st.getName() : "");
        data.put("typeId", shop.getTypeId());

        // 关联帖子（最新 5 篇）
        java.util.List<com.cjj.entity.Blog> blogs = blogMapper.selectList(
                Wrappers.<com.cjj.entity.Blog>lambdaQuery()
                        .eq(com.cjj.entity.Blog::getShopId, id)
                        .orderByDesc(com.cjj.entity.Blog::getCreateTime)
                        .last("LIMIT 5"));
        java.util.List<java.util.Map<String, Object>> blogList = new java.util.ArrayList<>();
        for (com.cjj.entity.Blog b : blogs) {
            java.util.Map<String, Object> bm = new java.util.HashMap<>();
            bm.put("id", b.getId());
            bm.put("title", b.getTitle());
            bm.put("liked", b.getLiked());
            bm.put("images", b.getImages());
            com.cjj.entity.User u = userService.getById(b.getUserId());
            bm.put("nickname", u != null ? u.getNickName() : "");
            bm.put("avatar", u != null ? u.getIcon() : "");
            blogList.add(bm);
        }
        data.put("blogs", blogList);

        // 4. 写入 Redis 缓存（30 分钟）
        stringRedisTemplate.opsForValue().set(cacheKey,
                cn.hutool.json.JSONUtil.toJsonStr(data), 30, java.util.concurrent.TimeUnit.MINUTES);

        return Result.ok(data);
    }

    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        shopService.save(shop);
        return Result.ok(shop.getId());
    }

    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        return shopService.update(shop);
    }

    /**
     * 按类型分页查询
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<Shop> page = shopService.getBaseMapper().selectPage(
                new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE),
                Wrappers.<Shop>lambdaQuery().eq(Shop::getTypeId, typeId));
        return Result.ok(page.getRecords());
    }

    /**
     * 按名称搜索
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<Shop> page = shopService.getBaseMapper().selectPage(
                new Page<>(current, SystemConstants.MAX_PAGE_SIZE),
                Wrappers.<Shop>lambdaQuery()
                        .like(StrUtil.isNotBlank(name), Shop::getName, name));
        return Result.ok(page.getRecords());
    }

    /**
     * 附近商户（Redis GEO）
     */
    @GetMapping("/of/nearby")
    public Result queryNearbyShops(
            @RequestParam("x") Double x,
            @RequestParam("y") Double y,
            @RequestParam(value = "radius", defaultValue = "5000") Integer radius,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return shopService.queryNearbyShops(x, y, radius, current);
    }

    /**
     * 查询商户今日 UV（HyperLogLog）
     */
    @GetMapping("/uv/{id}")
    public Result queryUV(@PathVariable("id") Long id) {
        return ((com.cjj.service.impl.ShopServiceImpl) shopService).queryUV(id);
    }
}
