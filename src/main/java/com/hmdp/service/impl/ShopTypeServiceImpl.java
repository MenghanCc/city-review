package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        String typeListJson = stringRedisTemplate.opsForValue().get(key);

        // 如果缓存存在，直接返回
        if (typeListJson != null && !typeListJson.isEmpty()) {
            List<ShopType> typeList = JSONUtil.toList(typeListJson, ShopType.class);
            return Result.ok(typeList);
        }

        // 如果缓存不存在，从数据库查询
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 如果在数据库中存在，将查询结果存入Redis
        stringRedisTemplate.opsForValue()
                .set(key,
                        JSONUtil.toJsonStr(typeList),
                        RedisConstants.CACHE_SHOP_TYPE_TTL,
                        TimeUnit.MINUTES);

        // 返回结果
        return Result.ok(typeList);
    }
}
