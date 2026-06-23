package com.cjj.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cjj.dto.Result;
import com.cjj.entity.Shop;

/**
 * city-review 商户服务接口
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

    /**
     * 查询附近商户（Redis GEO）
     * @param x       用户当前经度
     * @param y       用户当前纬度
     * @param radius  搜索半径（米），默认5000
     * @param current 页码
     */
    Result queryNearbyShops(Double x, Double y, Integer radius, Integer current);

    /**
     * 记录 UV（Redis HyperLogLog）
     * @param shopId 商户ID
     */
    Result recordUV(Long shopId);
}
