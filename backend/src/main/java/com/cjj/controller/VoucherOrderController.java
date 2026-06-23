package com.cjj.controller;

import com.cjj.dto.Result;
import com.cjj.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * city-review 秒杀订单控制器
 */
@RestController
@RequestMapping("/api/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    /**
     * 秒杀优惠券
     * 核心流程：Lua 原子判断 → 扣库存 → 异步下单（Stream队列）
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 查询秒杀结果
     * 前端轮询此接口获取最终下单状态
     */
    @GetMapping("result/{id}")
    public Result querySeckillResult(@PathVariable("id") Long voucherId) {
        return voucherOrderService.querySeckillResult(voucherId);
    }
}
