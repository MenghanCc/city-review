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
