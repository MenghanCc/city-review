package com.cjj.controller;

import com.cjj.dto.Result;
import com.cjj.service.impl.ShopBizServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Map;

@RestController
public class ShopBizController {

    @Resource private ShopBizServiceImpl bizService;

    // ---- 钱包 ----
    @GetMapping("/api/wallet/balance")
    public Result getBalance() { return bizService.getBalance(); }

    @PostMapping("/api/wallet/recharge")
    public Result recharge(@RequestBody Map<String, Object> body) {
        return bizService.recharge(new BigDecimal(body.get("amount").toString()));
    }

    @GetMapping("/api/wallet/transactions")
    public Result getTransactions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return bizService.getTransactions(page, size);
    }

    // ---- 商品 ----
    @GetMapping("/api/products/available")
    public Result getProducts(@RequestParam(required = false) Long shopId) {
        return bizService.getProducts(shopId);
    }

    @PostMapping("/api/products/purchase")
    public Result purchaseProduct(@RequestBody Map<String, Object> body) {
        return bizService.purchaseProduct(Long.valueOf(body.get("productId").toString()));
    }

    // ---- 优惠券购买 ----
    @PostMapping("/api/vouchers/purchase")
    public Result purchaseVoucher(@RequestBody Map<String, Object> body) {
        return bizService.purchaseVoucher(Long.valueOf(body.get("voucherId").toString()));
    }

    // ---- 卡券 ----
    @GetMapping("/api/user/vouchers")
    public Result getUserVouchers(@RequestParam(required = false) Integer status) {
        return bizService.getUserVouchers(status);
    }

    @PostMapping("/api/user/vouchers/use/{id}")
    public Result useVoucher(@PathVariable("id") Long id) {
        return bizService.useVoucher(id);
    }

    // ---- 订单 ----
    @GetMapping("/api/orders/my")
    public Result getMyOrders(
            @RequestParam(required = false) Integer orderType,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return bizService.getMyOrders(orderType, page, size);
    }
}
