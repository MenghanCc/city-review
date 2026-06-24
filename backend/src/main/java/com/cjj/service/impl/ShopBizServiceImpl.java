package com.cjj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cjj.dto.Result;
import com.cjj.dto.UserDTO;
import com.cjj.entity.*;
import com.cjj.mapper.*;
import com.cjj.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ShopBizServiceImpl {

    @Resource private ProductMapper productMapper;
    @Resource private UserVoucherMapper userVoucherMapper;
    @Resource private OrderMapper orderMapper;
    @Resource private WalletMapper walletMapper;
    @Resource private WalletTransactionMapper walletTxMapper;
    @Resource private VoucherMapper voucherMapper;
    @Resource private com.cjj.mapper.ShopMapper shopMapper;

    // ==================== 钱包 ====================

    private Wallet getOrCreateWallet(Long userId) {
        Wallet w = walletMapper.selectOne(
                Wrappers.<Wallet>lambdaQuery().eq(Wallet::getUserId, userId));
        if (w == null) {
            w = new Wallet();
            w.setUserId(userId);
            w.setBalance(BigDecimal.ZERO);
            walletMapper.insert(w);
        }
        return w;
    }

    public Result getBalance() {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");
        Wallet w = getOrCreateWallet(me.getId());
        return Result.ok(Collections.singletonMap("balance", w.getBalance()));
    }

    @Transactional
    public Result recharge(BigDecimal amount) {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            return Result.fail("充值金额必须大于0");

        Wallet w = getOrCreateWallet(me.getId());
        w.setBalance(w.getBalance().add(amount));
        walletMapper.updateById(w);

        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(me.getId());
        tx.setTransactionType(1);
        tx.setAmount(amount);
        tx.setBalanceAfter(w.getBalance());
        tx.setNote("充值");
        tx.setCreatedAt(LocalDateTime.now());
        walletTxMapper.insert(tx);

        log.info("city-review 充值成功 → userId={}, amount={}", me.getId(), amount);
        return Result.ok(Collections.singletonMap("balance", w.getBalance()));
    }

    public Result getTransactions(Integer page, Integer size) {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");

        Page<WalletTransaction> p = walletTxMapper.selectPage(
                new Page<>(page == null ? 1 : page, size == null ? 10 : size),
                Wrappers.<WalletTransaction>lambdaQuery()
                        .eq(WalletTransaction::getUserId, me.getId())
                        .orderByDesc(WalletTransaction::getCreatedAt));
        return Result.ok(p.getRecords());
    }

    // ==================== 商品 ====================

    public Result getProducts(Long shopId) {
        LambdaQueryWrapper<Product> q = Wrappers.<Product>lambdaQuery().gt(Product::getStock, 0);
        if (shopId != null) q.eq(Product::getShopId, shopId);
        return Result.ok(productMapper.selectList(q));
    }

    @Transactional
    public Result purchaseProduct(Long productId, Long userVoucherId) {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");

        Product p = productMapper.selectById(productId);
        if (p == null || p.getStock() <= 0) return Result.fail("商品库存不足");

        BigDecimal finalPrice = p.getPrice();
        String note = "购买商品：" + p.getName();

        // 如果选择了优惠券，校验并使用
        if (userVoucherId != null) {
            UserVoucher uv = userVoucherMapper.selectById(userVoucherId);
            if (uv == null || !uv.getUserId().equals(me.getId()))
                return Result.fail("优惠券不存在");
            if (uv.getStatus() != 0)
                return Result.fail("优惠券已使用或已过期");
            if (uv.getExpireTime() != null && uv.getExpireTime().isBefore(LocalDateTime.now()))
                return Result.fail("优惠券已过期");
            if (!uv.getShopId().equals(p.getShopId()))
                return Result.fail("该优惠券不适用于本商户");

            Voucher v = voucherMapper.selectById(uv.getVoucherId());
            if (v == null) return Result.fail("优惠券信息异常");

            // 抵扣金额 = 优惠券面额，最低抵扣至 0
            BigDecimal discount = new BigDecimal(v.getActualValue()).divide(new BigDecimal(100));
            if (discount.compareTo(finalPrice) > 0) discount = finalPrice;
            finalPrice = finalPrice.subtract(discount);
            if (finalPrice.compareTo(BigDecimal.ZERO) < 0) finalPrice = BigDecimal.ZERO;

            // 标记卡券已使用
            uv.setStatus(1);
            uv.setUseTime(LocalDateTime.now());
            userVoucherMapper.updateById(uv);

            note = "购买商品：" + p.getName() + " (使用优惠券抵扣¥" + discount + ")";
        }

        Wallet w = getOrCreateWallet(me.getId());
        if (w.getBalance().compareTo(finalPrice) < 0)
            return Result.fail("余额不足，请先充值");

        // 扣款
        w.setBalance(w.getBalance().subtract(finalPrice));
        walletMapper.updateById(w);

        // 订单
        Order o = new Order();
        o.setUserId(me.getId());
        o.setOrderType(2);
        o.setTargetId(productId);
        o.setShopId(p.getShopId());
        o.setAmount(finalPrice);
        o.setStatus(1);
        o.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(o);

        // 流水
        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(me.getId()); tx.setTransactionType(2);
        tx.setAmount(finalPrice.negate()); tx.setBalanceAfter(w.getBalance());
        tx.setOrderId(o.getId()); tx.setNote(note);
        tx.setCreatedAt(LocalDateTime.now());
        walletTxMapper.insert(tx);

        // 扣库存
        p.setStock(p.getStock() - 1);
        productMapper.updateById(p);

        log.info("city-review 购买商品成功 → userId={}, productId={}, amount={}", me.getId(), productId, p.getPrice());
        return Result.ok(o.getId());
    }

    // ==================== 优惠券购买 ====================

    @Transactional
    public Result purchaseVoucher(Long voucherId) {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");

        Voucher v = voucherMapper.selectById(voucherId);
        if (v == null || v.getStatus() != 1) return Result.fail("优惠券不存在或已下架");

        // 特价券需校验库存
        if (v.getType() == 1 && v.getStock() != null && v.getStock() <= 0)
            return Result.fail("该优惠券已售罄");

        BigDecimal price = new BigDecimal(v.getPayValue()).divide(new BigDecimal(100));
        Wallet w = getOrCreateWallet(me.getId());
        if (w.getBalance().compareTo(price) < 0)
            return Result.fail("余额不足，请先充值");

        // 扣款
        w.setBalance(w.getBalance().subtract(price));
        walletMapper.updateById(w);

        // 订单
        Order o = new Order();
        o.setUserId(me.getId()); o.setOrderType(1);
        o.setTargetId(voucherId); o.setShopId(v.getShopId());
        o.setAmount(price); o.setStatus(1);
        o.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(o);

        // 流水
        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(me.getId()); tx.setTransactionType(2);
        tx.setAmount(price.negate()); tx.setBalanceAfter(w.getBalance());
        tx.setOrderId(o.getId()); tx.setNote("购买优惠券：" + v.getTitle());
        tx.setCreatedAt(LocalDateTime.now());
        walletTxMapper.insert(tx);

        // 卡券实例
        UserVoucher uv = new UserVoucher();
        uv.setUserId(me.getId()); uv.setVoucherId(voucherId);
        uv.setShopId(v.getShopId()); uv.setStatus(0);
        uv.setBuyTime(LocalDateTime.now());
        uv.setExpireTime(LocalDateTime.now().plusDays(30)); // 默认30天有效
        userVoucherMapper.insert(uv);

        // 特价券扣库存
        if (v.getType() == 1 && v.getStock() != null) {
            v.setStock(v.getStock() - 1);
            voucherMapper.updateById(v);
        }

        log.info("city-review 购买优惠券成功 → userId={}, voucherId={}", me.getId(), voucherId);
        return Result.ok(o.getId());
    }

    // ==================== 卡券 ====================

    public Result getUserVouchers(Integer status) {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");

        LambdaQueryWrapper<UserVoucher> q = Wrappers.<UserVoucher>lambdaQuery()
                .eq(UserVoucher::getUserId, me.getId());
        if (status != null) q.eq(UserVoucher::getStatus, status);
        q.orderByDesc(UserVoucher::getBuyTime);

        List<UserVoucher> list = userVoucherMapper.selectList(q);
        Set<Long> vIds = list.stream().map(UserVoucher::getVoucherId).collect(Collectors.toSet());
        Set<Long> sIds = list.stream().map(UserVoucher::getShopId).collect(Collectors.toSet());

        Map<Long, Voucher> vMap = new HashMap<>();
        Map<Long, Shop> sMap = new HashMap<>();
        if (!vIds.isEmpty()) vMap = voucherMapper.selectBatchIds(vIds).stream()
                .collect(Collectors.toMap(Voucher::getId, x -> x));
        if (!sIds.isEmpty()) sMap = shopMapper.selectBatchIds(sIds).stream()
                .collect(Collectors.toMap(Shop::getId, x -> x));

        List<Map<String, Object>> result = new ArrayList<>();
        for (UserVoucher uv : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", uv.getId());
            m.put("voucherId", uv.getVoucherId());
            m.put("shopId", uv.getShopId());
            m.put("status", uv.getStatus());
            m.put("buyTime", uv.getBuyTime());
            m.put("expireTime", uv.getExpireTime());
            m.put("useTime", uv.getUseTime());
            Voucher vv = vMap.get(uv.getVoucherId());
            m.put("voucherTitle", vv != null ? vv.getTitle() : "");
            m.put("faceValue", vv != null ? vv.getActualValue() : 0);
            Shop ss = sMap.get(uv.getShopId());
            m.put("shopName", ss != null ? ss.getName() : "");
            result.add(m);
        }
        return Result.ok(result);
    }

    @Transactional
    public Result useVoucher(Long userVoucherId) {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");

        UserVoucher uv = userVoucherMapper.selectById(userVoucherId);
        if (uv == null || !uv.getUserId().equals(me.getId()))
            return Result.fail("卡券不存在");
        if (uv.getStatus() != 0) return Result.fail("卡券已使用或已过期");
        if (uv.getExpireTime() != null && uv.getExpireTime().isBefore(LocalDateTime.now()))
            return Result.fail("卡券已过期");

        uv.setStatus(1);
        uv.setUseTime(LocalDateTime.now());
        userVoucherMapper.updateById(uv);

        log.info("city-review 使用卡券 → userVoucherId={}", userVoucherId);
        return Result.ok();
    }

    // ==================== 订单 ====================

    public Result getMyOrders(Integer orderType, Integer page, Integer size) {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");

        LambdaQueryWrapper<Order> q = Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, me.getId());
        if (orderType != null) q.eq(Order::getOrderType, orderType);
        q.orderByDesc(Order::getCreatedAt);

        Page<Order> p = orderMapper.selectPage(
                new Page<>(page == null ? 1 : page, size == null ? 10 : size), q);

        // 填充名称
        Set<Long> shopIds = new HashSet<>();
        for (Order o : p.getRecords()) shopIds.add(o.getShopId());
        Map<Long, Shop> sMap = shopMapper.selectBatchIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, x -> x));

        for (Order o : p.getRecords()) {
            Shop ss = sMap.get(o.getShopId());
            o.setShopName(ss != null ? ss.getName() : "");
            if (o.getOrderType() == 1 && o.getTargetId() != null) {
                Voucher v = voucherMapper.selectById(o.getTargetId());
                o.setTargetName(v != null ? v.getTitle() : "优惠券");
            } else if (o.getOrderType() == 2 && o.getTargetId() != null) {
                Product prod = productMapper.selectById(o.getTargetId());
                o.setTargetName(prod != null ? prod.getName() : "商品");
            }
        }
        return Result.ok(p.getRecords());
    }
}
