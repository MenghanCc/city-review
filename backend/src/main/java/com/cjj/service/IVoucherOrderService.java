package com.cjj.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cjj.dto.Result;
import com.cjj.entity.VoucherOrder;

/**
 * city-review 秒杀订单服务接口
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀优惠券
     * @param voucherId 优惠券ID
     * @return 秒杀结果
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 查询秒杀结果（订单ID）
     * @param voucherId 优惠券ID
     * @return 订单ID，null 表示尚未完成
     */
    Result querySeckillResult(Long voucherId);
}
