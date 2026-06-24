package com.cjj.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cjj.dto.Result;
import com.cjj.entity.Voucher;
import com.cjj.mapper.VoucherMapper;
import com.cjj.entity.SeckillVoucher;
import com.cjj.service.ISeckillVoucherService;
import com.cjj.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

import static com.cjj.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 陈俊杰
 * @since 2026-6-3
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        save(voucher);
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        // 同步秒杀库存到 Redis，TTL = 秒杀结束时间差值
        long ttlSeconds = java.time.Duration.between(
                java.time.LocalDateTime.now(), voucher.getEndTime()).getSeconds();
        if (ttlSeconds > 0) {
            stringRedisTemplate.opsForValue().set(
                    SECKILL_STOCK_KEY + voucher.getId(),
                    voucher.getStock().toString(),
                    ttlSeconds,
                    java.util.concurrent.TimeUnit.SECONDS);
        }
    }
}
