package com.cjj.utils;

/**
 * city-review Redis 常量
 * 统一定义所有 Redis Key 前缀和 TTL，方便维护
 */
public class RedisConstants {

    // ==================== 功能一：短信登录 ====================
    /** 验证码 Key 前缀，后接手机号 */
    public static final String LOGIN_CODE_KEY = "login:code:";
    /** 验证码 TTL（分钟） */
    public static final Long LOGIN_CODE_TTL = 5L;
    /** 用户 Token Key 前缀，后接 token 值 */
    public static final String LOGIN_USER_KEY = "login:token:";
    /** 用户 Token TTL（分钟） */
    public static final Long LOGIN_USER_TTL = 120L;

    // ==================== 功能二：商户缓存 ====================
    /** 缓存空对象 TTL（分钟）—— 防止缓存穿透 */
    public static final Long CACHE_NULL_TTL = 2L;
    /** 商户缓存 Key 前缀 */
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    /** 商户缓存基础 TTL（分钟），实际会加随机偏移防雪崩 */
    public static final Long CACHE_SHOP_TTL = 30L;
    /** 分布式锁 Key 前缀（缓存击穿重建） */
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    /** 分布式锁 TTL（秒） */
    public static final Long LOCK_SHOP_TTL = 10L;
    /** 商户类型缓存 Key */
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shopType:";
    /** 商户类型缓存 TTL（分钟） */
    public static final Long CACHE_SHOP_TYPE_TTL = 30L;

    // ==================== 功能三：秒杀 ====================
    /** 秒杀库存 Key 前缀，后接 voucherId */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    /** 秒杀一人一单 Key 前缀，后接 voucherId:userId */
    public static final String SECKILL_ORDER_KEY = "seckill:order:";
    /** 秒杀分布式锁 Key 前缀 */
    public static final String LOCK_SECKILL_KEY = "lock:seckill:";
    /** 秒杀异步下单 List 队列 Key */
    public static final String SECKILL_ORDER_LIST_KEY = "seckill:order:list";
    /** 秒杀 Stream 队列 Key */
    public static final String SECKILL_ORDER_STREAM_KEY = "seckill:order:stream";
    /** Stream 消费者组名称 */
    public static final String SECKILL_ORDER_GROUP = "seckill-order-group";
    /** Stream 消费者名称 */
    public static final String SECKILL_ORDER_CONSUMER = "seckill-order-consumer";
    /** 秒杀 Pub/Sub 频道 */
    public static final String SECKILL_ORDER_CHANNEL = "seckill:order:channel";

    // ==================== 功能四：附近商户 GEO ====================
    /** 商户 Geo 坐标 Key */
    public static final String SHOP_GEO_KEY = "shop:geo:";

    // ==================== 功能五：UV 统计 HyperLogLog ====================
    /** 商户 UV 统计 Key 前缀，后接 shopId:yyyyMMdd */
    public static final String UV_SHOP_KEY = "uv:shop:";

    // ==================== 功能六：用户签到 BitMap ====================
    /** 用户签到 Key 前缀，后接 userId:yyyyMM */
    public static final String USER_SIGN_KEY = "sign:";

    // ==================== 功能七：好友关注 Set ====================
    /** 关注集合 Key 前缀 */
    public static final String FOLLOW_KEY = "follow:";
    /** 粉丝集合 Key 前缀 */
    public static final String FANS_KEY = "fans:";

    // ==================== 功能八：达人探店 ====================
    /** 探店笔记点赞 List Key 前缀（按时间倒序） */
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    /** 探店笔记点赞排行榜 SortedSet Key 前缀（score=时间戳） */
    public static final String BLOG_LIKED_RANK_KEY = "blog:liked:rank:";
    /** 用户收件箱 Feed 流 SortedSet Key 前缀 */
    public static final String FEED_KEY = "feed:";
    /** 探店笔记点赞用户 Set（用于去重判断是否已点赞） */
    public static final String BLOG_LIKED_USER_KEY = "blog:liked:user:";
}
