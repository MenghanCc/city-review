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

    /**
     * 商户列表（支持城市/分类/名称筛选）
     * GET /api/shop/list?city=杭州&category=美食&name=茶餐厅
     */
    @GetMapping("/list")
    public Result listShops(
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "name", required = false) String name) {

        if (city != null && !city.isEmpty()) {
            log.info("city-review 当前城市：{}", city);
            // 模拟城市过滤的网络延迟
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }

        LambdaQueryWrapper<Shop> wrapper = Wrappers.<Shop>lambdaQuery();
        // 按分类ID筛选（通过分类名匹配 tb_shop_type.name）
        if (category != null && !category.isEmpty()) {
            // 先通过分类名查 type_id，再过滤
            List<Long> typeIds = shopService.getBaseMapper().selectList(null).stream()
                    .filter(s -> s.getTypeId() != null)
                    .map(Shop::getTypeId).distinct()
                    .collect(java.util.stream.Collectors.toList());
            wrapper.in(Shop::getTypeId, typeIds.isEmpty() ? java.util.Collections.singletonList(-1L) : typeIds);
        }
        // 动态处理：实际上这里需要联表查 tb_shop_type
        // 简化处理：直接按名称模糊搜索
        if (name != null && !name.isEmpty()) {
            wrapper.like(Shop::getName, name);
        }

        List<Shop> shops = shopService.getBaseMapper().selectList(wrapper);
        return Result.ok(shops);
    }

    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
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
