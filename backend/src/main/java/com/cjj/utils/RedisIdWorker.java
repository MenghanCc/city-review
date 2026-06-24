package com.cjj.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * city-review Redis 全局唯一 ID 生成器
 *
 * ID 组成（64 bit）：
 *   1 bit  符号位（恒 0）
 *  31 bit  时间戳（秒级，可用 69 年）
 *  32 bit  序列号（秒内计数器，每秒 2^32 个）
 *
 * Key: "icr:{prefix}:{yyyy:MM:dd}"  按天隔离，便于统计
 */
@Component
public class RedisIdWorker {

    /** 起始时间戳：2022-01-01 00:00:00 */
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    /** 序列号位数 */
    private static final int COUNT_BITS = 32;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // 1. 时间戳（31 bit）
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2. 序列号（32 bit），按天隔离
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3. 拼接：时间戳左移 32 位 | 序列号
        return timestamp << COUNT_BITS | count;
    }
}
