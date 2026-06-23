package com.cjj.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cjj.dto.Result;
import com.cjj.entity.Shop;
import com.cjj.service.IShopService;
import com.cjj.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * city-review 商户控制器
 */
@RestController
@RequestMapping("/api/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

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
