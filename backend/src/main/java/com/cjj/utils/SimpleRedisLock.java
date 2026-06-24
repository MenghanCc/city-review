package com.cjj.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * city-review 基于 Redis 的分布式锁（setnx + Lua 原子释放）
 *
 * 核心设计：
 *   加锁：SET key threadId NX EX timeoutSec  — 互斥 + 防死锁
 *   解锁：Lua 脚本原子性判断归属再 DEL      — 防误删
 *
 * 为什么不用 Redisson？
 *   本模块演示手写分布式锁原理，实际生产建议 Redisson（看门狗续期 + 可重入）
 */
public class SimpleRedisLock {

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lua/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    private final String name;
    private final StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** 尝试获取锁（非阻塞），timeoutSec 秒后自动释放 */
    public boolean tryLock(long timeoutSec) {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    /** 释放锁（Lua 原子操作：判断归属 → 删除） */
    public void unlock() {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                threadId);
    }
}
