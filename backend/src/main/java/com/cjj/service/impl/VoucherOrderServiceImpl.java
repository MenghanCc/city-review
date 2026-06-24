package com.cjj.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cjj.dto.Result;
import com.cjj.dto.UserDTO;
import com.cjj.entity.SeckillVoucher;
import com.cjj.entity.VoucherOrder;
import com.cjj.mapper.VoucherOrderMapper;
import com.cjj.service.ISeckillVoucherService;
import com.cjj.service.IVoucherOrderService;
import com.cjj.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static com.cjj.utils.RedisConstants.*;

/**
 * city-review 秒杀订单服务实现
 *
 * 核心链路：
 *   1. 预加载库存到 Redis（@PostConstruct）
 *   2. 用户请求 → 执行 Lua 脚本（原子性：判断库存 + 一人一单 + 扣库存）
 *   3. Lua 成功 → 订单消息推入 Stream 队列
 *   4. 异步消费者 → Stream 消费 → 写入 MySQL
 *   5. 用户轮询查询秒杀结果
 *
 * 三种消息队列均有演示：
 *   - List（lpush/brpop）：基础异步解耦
 *   - Pub/Sub：实时通知
 *   - Stream（消费者组 + XACK）：生产级可靠消费（默认启用）
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private com.cjj.utils.RedisIdWorker redisIdWorker;

    @Resource
    private com.cjj.mapper.OrderMapper newOrderMapper;

    @Resource
    private com.cjj.mapper.UserVoucherMapper userVoucherMapper;

    @Resource
    private com.cjj.mapper.VoucherMapper voucherMapper;

    @Resource
    private com.cjj.mapper.WalletMapper walletMapper;

    @Resource
    private com.cjj.mapper.WalletTransactionMapper walletTxMapper;

    // ==================== Lua 脚本加载 ====================
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // ==================== 异步下单线程池 ====================
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // ==================== 启动时预加载库存 & 启动 Stream 消费者 ====================
    @PostConstruct
    public void init() {
        try {
            preloadSeckillStock();
        } catch (Exception e) {
            log.warn("city-review 秒杀库存预加载失败，跳过: {}", e.getMessage());
        }
        // 创建 Stream 消费者组：XGROUP CREATE stream group 0 MKSTREAM
        try {
            stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                connection.execute("XGROUP",
                        "CREATE".getBytes(),
                        SECKILL_ORDER_STREAM_KEY.getBytes(),
                        SECKILL_ORDER_GROUP.getBytes(),
                        "0".getBytes(), "MKSTREAM".getBytes());
                return null;
            });
            log.info("city-review Stream 消费者组创建成功");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("BUSYGROUP") || msg.contains("already exists")) {
                log.info("city-review Stream 消费者组已存在，跳过创建");
            } else {
                log.warn("city-review Stream 消费者组创建异常: {}", msg);
            }
        }
        SECKILL_ORDER_EXECUTOR.submit(new StreamOrderHandler());
    }

    /**
     * 将 MySQL 中未过期且库存 > 0 的秒杀券库存同步到 Redis
     */
    private void preloadSeckillStock() {
        List<SeckillVoucher> seckillVouchers = seckillVoucherService.getBaseMapper().selectList(
                Wrappers.<SeckillVoucher>lambdaQuery()
                        .gt(SeckillVoucher::getEndTime, LocalDateTime.now())
                        .gt(SeckillVoucher::getStock, 0));
        for (SeckillVoucher sv : seckillVouchers) {
            String key = SECKILL_STOCK_KEY + sv.getVoucherId();
            // 只在 Redis 不存在时设置，避免覆盖已扣减的库存
            Boolean absent = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, String.valueOf(sv.getStock()));
            if (Boolean.TRUE.equals(absent)) {
                log.info("city-review 预加载秒杀库存 → voucherId={}, stock={}", sv.getVoucherId(), sv.getStock());
            }
        }
    }

    // ==================== 核心秒杀接口 ====================

    @Override
    public Result seckillVoucher(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(401, "请先登录");
        }
        Long userId = user.getId();

        // --- 校验秒杀时间 ---
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("该优惠券不是秒杀券");
        }
        if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())) {
            return Result.fail("秒杀尚未开始");
        }
        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
            return Result.fail("秒杀已结束");
        }

        // --- 执行 Lua 脚本（原子操作）---
        // KEYS[1]: 库存 Key, KEYS[2]: 一人一单 Set Key
        // ARGV[1]: 用户 ID
        List<String> keys = Arrays.asList(
                SECKILL_STOCK_KEY + voucherId,
                SECKILL_ORDER_KEY + voucherId
        );
        Long r = stringRedisTemplate.execute(SECKILL_SCRIPT, keys, userId.toString());

        if (r == null) {
            return Result.fail("系统繁忙，请稍后再试");
        }

        int code = r.intValue();
        if (code == 1) {
            return Result.fail("库存不足");
        }
        if (code == 2) {
            return Result.fail("您已经参与过本次秒杀");
        }

        // --- Lua 返回 0：秒杀资格获取成功，异步下单 ---
        long orderId = redisIdWorker.nextId("order");

        // 构建订单消息
        Map<String, String> message = new HashMap<>();
        message.put("orderId", String.valueOf(orderId));
        message.put("userId", userId.toString());
        message.put("voucherId", voucherId.toString());

        // =================================================
        // 方式三（生产推荐）：推入 Stream 队列
        // 选用 Stream 的原因：消费者组 + XACK 确认机制保证消息不丢
        // =================================================
        Map<String, String> streamBody = new HashMap<>();
        streamBody.put("payload", JSONUtil.toJsonStr(message));
        stringRedisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .ofMap(streamBody)
                        .withStreamKey(SECKILL_ORDER_STREAM_KEY)
        );

        // --- 方式一（参考）：推入 List 队列 ---
        // 选用 List 的原因：最简单的生产者-消费者模型，lpush/brpop
        // stringRedisTemplate.opsForList().leftPush(SECKILL_ORDER_LIST_KEY, JSONUtil.toJsonStr(message));

        // --- 方式二（参考）：Pub/Sub 通知 ---
        // 选用 Pub/Sub 的原因：实时推送给所有订阅者，适合状态通知
        // stringRedisTemplate.convertAndSend(SECKILL_ORDER_CHANNEL, JSONUtil.toJsonStr(message));

        log.info("city-review 秒杀成功 → voucherId={}, userId={}, orderId={}", voucherId, userId, orderId);

        // 返回订单 ID 给前端，前端可轮询 /result 接口获取最终结果
        return Result.ok(orderId);
    }

    @Override
    public Result querySeckillResult(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(401, "请先登录");
        }
        Long userId = user.getId();

        // 查询是否已生成 MySQL 订单
        VoucherOrder order = getBaseMapper().selectOne(
                Wrappers.<VoucherOrder>lambdaQuery()
                        .eq(VoucherOrder::getUserId, userId)
                        .eq(VoucherOrder::getVoucherId, voucherId));

        if (order != null) {
            return Result.ok(order.getId());
        }

        // 检查是否还在 Redis 的已下单 Set 中（Lua 标记了但 MySQL 尚未落库）
        Boolean inSet = stringRedisTemplate.opsForSet()
                .isMember(SECKILL_ORDER_KEY + voucherId, userId.toString());
        if (Boolean.TRUE.equals(inSet)) {
            return Result.ok(0);  // 0 表示排队中
        }

        return Result.fail("未参与秒杀");
    }

    // ==================== Stream 消费者（异步下单） ====================

    private class StreamOrderHandler implements Runnable {
        @Override
        public void run() {
            String streamKey = SECKILL_ORDER_STREAM_KEY;
            String group = SECKILL_ORDER_GROUP;
            String consumer = SECKILL_ORDER_CONSUMER;

            // 1. 尝试创建消费者组
            try {
                stringRedisTemplate.opsForStream().createGroup(streamKey, group);
            } catch (Exception e) {
                // 消费者组已存在
            }

            // 2. 先处理 Pending 消息
            handlePending(streamKey, group, consumer);

            // 3. 循环消费新消息
            while (true) {
                try {
                    // 阻塞读取新消息
                    List<MapRecord<String, Object, Object>> records =
                            stringRedisTemplate.opsForStream().read(
                                    Consumer.from(group, consumer),
                                    StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                                    StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                            );

                    if (records == null || records.isEmpty()) {
                        handlePending(streamKey, group, consumer);
                        continue;
                    }

                    for (MapRecord<String, Object, Object> record : records) {
                        Map<Object, Object> map = record.getValue();
                        Object payloadObj = map.get("payload");
                        if (payloadObj == null) continue;

                        @SuppressWarnings("unchecked")
                        Map<String, String> orderMsg = JSONUtil.toBean(
                                payloadObj.toString(), Map.class);

                        Long orderId = Long.valueOf(orderMsg.get("orderId"));
                        Long userId = Long.valueOf(orderMsg.get("userId"));
                        Long voucherId = Long.valueOf(orderMsg.get("voucherId"));

                        try {
                            saveOrderToDB(orderId, userId, voucherId);
                            // XACK 确认
                            stringRedisTemplate.opsForStream()
                                    .acknowledge(streamKey, group, record.getId().getValue());
                            log.info("city-review Stream 消费成功 → orderId={}", orderId);
                        } catch (Exception e) {
                            log.error("city-review Stream 消费失败 → orderId={}", orderId, e);
                        }
                    }
                } catch (Exception e) {
                    log.error("city-review Stream 消费异常", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        /**
         * 处理 Pending 列表（Stream 死信恢复）
         * 这是 Stream 相比 List/PubSub 的核心优势：消息不丢
         */
        @SuppressWarnings("unchecked")
        private void handlePending(String streamKey, String group, String consumer) {
            try {
                // 读取 Pending 列表（最早未确认的消息从头开始读）
                List<MapRecord<String, Object, Object>> pendingRecords =
                        stringRedisTemplate.opsForStream().read(
                                Consumer.from(group, consumer),
                                StreamReadOptions.empty().count(10),
                                StreamOffset.create(streamKey, ReadOffset.from("0"))
                        );

                if (pendingRecords == null || pendingRecords.isEmpty()) {
                    return;
                }

                for (MapRecord<String, Object, Object> record : pendingRecords) {
                    Map<Object, Object> map = record.getValue();
                    Object payloadObj = map.get("payload");
                    if (payloadObj == null) continue;

                    Map<String, String> orderMsg = JSONUtil.toBean(
                            payloadObj.toString(), Map.class);

                    Long orderId = Long.valueOf(orderMsg.get("orderId"));
                    Long userId = Long.valueOf(orderMsg.get("userId"));
                    Long voucherId = Long.valueOf(orderMsg.get("voucherId"));

                    try {
                        saveOrderToDB(orderId, userId, voucherId);
                        stringRedisTemplate.opsForStream()
                                .acknowledge(streamKey, group, record.getId().getValue());
                        log.info("city-review Pending 恢复成功 → orderId={}", orderId);
                    } catch (Exception e) {
                        log.error("city-review Pending 恢复失败 → orderId={}", orderId, e);
                    }
                }
            } catch (Exception e) {
                log.debug("city-review Pending 处理异常", e);
            }
        }
    }

    // ==================== 数据持久化 ====================

    @Transactional(rollbackFor = Exception.class)
    public void saveOrderToDB(Long orderId, Long userId, Long voucherId) {
        // 0. MySQL 乐观锁扣减秒杀库存（防超卖双重保障）
        //    核心逻辑：WHERE stock > 0 确保不会扣成负数
        boolean stockOk = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)  // ← 乐观锁：库存>0才执行，防止超卖
                .update();
        if (!stockOk) {
            log.error("city-review 秒杀库存不足（MySQL乐观锁） → voucherId={}", voucherId);
            throw new RuntimeException("库存不足");
        }

        // 1. 旧表秒杀订单
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setPayType(1);
        order.setStatus(2);
        order.setCreateTime(LocalDateTime.now());
        order.setPayTime(LocalDateTime.now());
        save(order);

        // 2. 优惠券信息（payValue 单位是分，转元）
        com.cjj.entity.Voucher v = voucherMapper.selectById(voucherId);
        if (v == null) return;

        java.math.BigDecimal price = new java.math.BigDecimal(v.getPayValue())
                .divide(new java.math.BigDecimal(100));

        // 3. 写入新订单表 tb_order（我的订单列表查这里）
        com.cjj.entity.Order newOrder = new com.cjj.entity.Order();
        newOrder.setUserId(userId);
        newOrder.setOrderType(1);
        newOrder.setTargetId(voucherId);
        newOrder.setShopId(v.getShopId());
        newOrder.setAmount(price);
        newOrder.setStatus(1);
        newOrder.setCreatedAt(LocalDateTime.now());
        newOrderMapper.insert(newOrder);

        // 4. 写入卡券表 tb_user_voucher（我的卡券查这里）
        com.cjj.entity.UserVoucher uv = new com.cjj.entity.UserVoucher();
        uv.setUserId(userId);
        uv.setVoucherId(voucherId);
        uv.setShopId(v.getShopId());
        uv.setStatus(0);
        uv.setBuyTime(LocalDateTime.now());
        uv.setExpireTime(LocalDateTime.now().plusDays(30));
        userVoucherMapper.insert(uv);

        // 5. 钱包扣款 + 流水
        com.cjj.entity.Wallet w = walletMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers
                        .<com.cjj.entity.Wallet>lambdaQuery()
                        .eq(com.cjj.entity.Wallet::getUserId, userId));
        if (w != null && w.getBalance().compareTo(price) >= 0) {
            w.setBalance(w.getBalance().subtract(price));
            walletMapper.updateById(w);

            com.cjj.entity.WalletTransaction tx = new com.cjj.entity.WalletTransaction();
            tx.setUserId(userId);
            tx.setTransactionType(2);
            tx.setAmount(price.negate());
            tx.setBalanceAfter(w.getBalance());
            tx.setOrderId(newOrder.getId());
            tx.setNote("秒杀优惠券：" + v.getTitle());
            tx.setCreatedAt(LocalDateTime.now());
            walletTxMapper.insert(tx);
        }

        log.info("city-review 秒杀订单落库完成 → orderId={}, userId={}, voucherId={}", orderId, userId, voucherId);
    }

}
