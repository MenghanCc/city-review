package com.cjj.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cjj.dto.Result;
import com.cjj.entity.Shop;
import com.cjj.entity.ShopType;
import com.cjj.mapper.ShopTypeMapper;
import com.cjj.service.impl.ShopServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 附近商户控制器（独立于 /api/shop，使用 /api/shops）
 */
@RestController
@RequestMapping("/api/shops")
public class NearbyShopController {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private ShopTypeMapper shopTypeMapper;

    /**
     * 附近商户
     * GET /api/shops/nearby?lng=114.3&lat=30.6&radius=3000&category=美食
     */
    @GetMapping("/nearby")
    public Result nearbyShops(
            @RequestParam("lng") Double lng,
            @RequestParam("lat") Double lat,
            @RequestParam(value = "radius", defaultValue = "3000") Integer radius,
            @RequestParam(value = "category", required = false) String category) {

        // 调用 GEO 查询
        Result geoResult = shopService.queryNearbyShops(lng, lat, radius, 1);

        if (geoResult.getCode() != 200 || geoResult.getData() == null) {
            return geoResult;
        }

        @SuppressWarnings("unchecked")
        List<Shop> shops = (List<Shop>) geoResult.getData();

        // 分类过滤
        if (category != null && !category.isEmpty()) {
            ShopType st = shopTypeMapper.selectOne(
                    Wrappers.<ShopType>lambdaQuery().eq(ShopType::getName, category));
            if (st != null) {
                Long typeId = st.getId();
                shops = shops.stream()
                        .filter(s -> s.getTypeId().equals(typeId))
                        .collect(Collectors.toList());
            }
        }

        // 组装返回
        List<Map<String, Object>> list = new ArrayList<>();
        for (Shop s : shops) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", s.getId());
            item.put("name", s.getName());
            item.put("address", s.getAddress());
            item.put("area", s.getArea());
            item.put("score", s.getScore());
            item.put("avgPrice", s.getAvgPrice());
            item.put("coverImg", s.getCoverImg());
            item.put("images", s.getImages());
            item.put("distance", s.getDistance());
            item.put("x", s.getX());
            item.put("y", s.getY());

            ShopType st2 = shopTypeMapper.selectById(s.getTypeId());
            item.put("categoryName", st2 != null ? st2.getName() : "");

            list.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", list.size());
        result.put("list", list);
        return Result.ok(result);
    }
}
