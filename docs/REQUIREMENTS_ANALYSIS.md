# 城市点评（city-review）需求分析文档

> **项目名称**：city-review（城市点评系统）  
> **技术栈**：Spring Boot 2.3.12 + MyBatis-Plus 3.4.3 + Redis + MySQL 8.0 + Thymeleaf/静态HTML  
> **部署方式**：前后端一体化（Spring Boot 内置 Tomcat :8081 统一承载）  
> **文档版本**：v1.0  
> **最后更新**：2026-06-22

---

## 目录

1. [项目概述](#1-项目概述)
2. [技术架构](#2-技术架构)
3. [数据库设计](#3-数据库设计)
4. [八大核心功能模块](#4-八大核心功能模块)
5. [Redis 数据结构与功能映射表](#5-redis-数据结构与功能映射表)
6. [API 接口设计](#6-api-接口设计)
7. [前端页面规划](#7-前端页面规划)
8. [部署与配置说明](#8-部署与配置说明)

---

## 1. 项目概述

### 1.1 业务背景

城市点评（city-review）是一个基于 LBS（地理位置服务）的城市生活消费点评平台。用户可以浏览所在城市的各类商户（美食、KTV、美容、健身等），查看商户详情，领取优惠券并参与秒杀活动，发布探店笔记，关注好友并互动。平台通过 Redis 提供高性能缓存、分布式 Session、实时统计与排行榜等能力。

### 1.2 业务目标

- 为城市消费者提供商户发现、点评、优惠领取的一站式服务
- 通过秒杀活动提升用户活跃度与商户曝光
- 利用 LBS 能力实现"附近的商户"推荐
- 构建社交化内容生态（探店笔记 + 好友关注）

### 1.3 用户角色

| 角色 | 描述 |
|------|------|
| 普通用户 | 浏览商户、领取优惠券、参与秒杀、发布探店笔记、签到、关注好友 |
| 商户管理员 | 管理商户信息、发布优惠券（本期暂不实现后台管理界面） |
| 系统管理员 | 管理用户、审核内容（本期暂不实现后台管理界面） |

---

## 2. 技术架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      浏览器 (Browser)                        │
│          Vue 3 SPA  (Vite 构建, 部署于 static/)              │
├─────────────────────────────────────────────────────────────┤
│              Spring Boot 内置 Tomcat (:8081)                  │
│  ┌─────────────────────┐   ┌─────────────────────────────┐  │
│  │  静态资源 (static/)   │   │   REST API (/api/**)        │  │
│  │  - Vue SPA (index.html)│  │   - Controller 层           │  │
│  │  - JS/CSS/图片       │   │   - Interceptor (Token校验)  │  │
│  └─────────────────────┘   └──────────┬──────────────────┘  │
│                                       │                      │
│  ┌────────────────────────────────────┼──────────────────┐  │
│  │                          Service 层                    │  │
│  │  ┌─────────────────────────────────────────────────┐  │  │
│  │  │  CacheClient (缓存穿透/击穿/雪崩)                 │  │  │
│  │  │  SeckillService (Lua脚本 + 分布式锁 + 消息队列)   │  │  │
│  │  │  BlogService / FollowService / SignService ...   │  │  │
│  │  └─────────────────────────────────────────────────┘  │  │
│  └──────────┬──────────────────────────────┬────────────┘  │
│             │                              │                │
│  ┌──────────▼──────────┐    ┌──────────────▼─────────────┐ │
│  │   MySQL (city_review)│    │   Redis (127.0.0.1:6379)   │ │
│  │   - tb_user          │    │   - String (验证码/Token)   │ │
│  │   - tb_shop          │    │   - Hash (用户Session)     │ │
│  │   - tb_voucher       │    │   - Geo (附近商户)         │ │
│  │   - tb_blog          │    │   - BitMap (签到)          │ │
│  │   - tb_follow        │    │   - HyperLogLog (UV)      │ │
│  │   - tb_voucher_order │    │   - Set (关注/粉丝)        │ │
│  │   - tb_sign          │    │   - List (消息队列/点赞)    │ │
│  │   ...                │    │   - SortedSet (排行榜)     │ │
│  └─────────────────────┘    │   - Stream (可靠消息队列)   │ │
│                              └────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 关键约定

| 约定项 | 说明 |
|--------|------|
| 部署方式 | **舍弃 Nginx**，前后端统一由 Spring Boot 内置 Tomcat（端口 8081）承载 |
| 前端框架 | **Vue 3**（Composition API + `<script setup>`），Vite 构建，SPA 单页应用 |
| 前端路由 | Vue Router 4（Hash 模式），后端不渲染页面，仅提供 `/api/**` 数据接口 |
| 静态资源 | Vite build 输出到 `src/main/resources/static/`，Spring Boot 自动托管 |
| API 前缀 | 所有后端 API 统一前缀 `/api/` |
| 前端 Ajax | Axios 实例 baseURL 为 `/api`，拦截器自动注入 Authorization 头 |
| 统一响应格式 | `{ "code": 200, "msg": "ok", "data": {} }` |
| 表前缀 | 沿用现有骨架 `tb_` 前缀 |
| 数据库密码 | 代码标注 `// TODO: 请在此处修改为你的 MySQL/Redis 实际密码` |

---

## 3. 数据库设计

### 3.1 现有核心表（tb_ 前缀，直接复用）

| 表名 | 对应实体 | 说明 | 关键字段 |
|------|----------|------|----------|
| `tb_user` | User | 用户表 | id, phone, password, nick_name, icon |
| `tb_user_info` | UserInfo | 用户详情表 | user_id(PK), city, introduce, fans, followee, gender, birthday, credits |
| `tb_shop` | Shop | 商户表 | id, name, type_id, area, address, x(经度), y(纬度), avg_price, sold, score |
| `tb_shop_type` | ShopType | 商户类型表 | id, name, icon, sort |
| `tb_blog` | Blog | 探店笔记表 | id, shop_id, user_id, title, images, content, liked, comments |
| `tb_blog_comments` | BlogComments | 笔记评论表 | id, user_id, blog_id, parent_id, answer_id, content, liked, status |
| `tb_follow` | Follow | 关注关系表 | id, user_id, follow_user_id |
| `tb_voucher` | Voucher | 优惠券表 | id, shop_id, title, pay_value, actual_value, type, status |
| `tb_seckill_voucher` | SeckillVoucher | 秒杀券表 | voucher_id(PK), stock, begin_time, end_time |
| `tb_voucher_order` | VoucherOrder | 订单表 | id, user_id, voucher_id, pay_type, status, create_time |
| `tb_sign` | (无实体类) | 签到表 | id, user_id, year, month, date, is_backup |

### 3.2 需新建实体类

| 表名 | 建议实体类名 | 说明 |
|------|-------------|------|
| `tb_sign` | Sign | 签到记录（已有表结构，缺少实体类） |


## 4. 八大核心功能模块

### 功能一：短信登录（Redis 共享 Session）

#### 5.1 业务流程

```
用户输入手机号 → 后端生成6位验证码 → log.info()打印到控制台（不做真实发送）
→ 验证码以 String 类型存入 Redis（Key: login:code:手机号, TTL: 5分钟）
→ 用户输入验证码登录 → 后端校验 → 创建/查询用户
→ 生成随机 Token → 用户信息以 Hash 类型存入 Redis（Key: login:token:xxx, TTL: 120分钟）
→ 前端收到 Token 存入 localStorage → 后续请求携带 Authorization 头
→ RefreshTokenInterceptor 拦截请求 → 从 Redis Hash 读取用户 → 刷新 TTL → 存入 ThreadLocal
```

#### 5.2 Redis 技术

| Redis 数据结构 | Key 格式 | 用途 | TTL |
|----------------|----------|------|-----|
| **String** | `login:code:{phone}` | 存储短信验证码 | 5 分钟 |
| **Hash** | `login:token:{token}` | 存储用户信息（id, nickName, icon）作为分布式 Session | 120 分钟 |

#### 5.3 关键设计决策

- **为什么不使用 HttpSession？** — 分布式场景下多台服务器 Session 不共享，Redis Hash 天然支持集群共享
- **为什么 Token 存 Hash 而不是 String？** — Hash 可以单独读取某个字段（如只取 nickName），比序列化整个对象更灵活
- **验证码不真实发送** — 通过 `log.info()` 打印到控制台，开发人员复制使用

#### 5.4 现状评估

- ✅ `UserServiceImpl.sendCode()` / `login()` 已实现基础逻辑
- ⚠️ 验证码 TTL 当前是 2 分钟，需求要求 5 分钟
- ⚠️ Controller 缺少 `/api/` 前缀
- ⚠️ `Result` 格式需要改造为 `{code, msg, data}`

---

### 功能二：商户查询缓存（解决三大缓存问题）

#### 2.1 业务流程

```
客户端请求商户详情 → Controller → Service → CacheClient
    ├─ 缓存穿透防护：查询 DB 不存在的 ID → 缓存空对象 ""（TTL 2分钟）→ 返回 null
    ├─ 缓存击穿防护：热点 Key 失效 → setnx 分布式锁 → 单线程重建缓存 → 释放锁
    └─ 缓存雪崩防护：所有 Key TTL 加随机偏移量（±随机分钟数）
```

#### 2.2 Redis 技术

| 问题 | 解决方案 | Redis 命令 | Key 格式 |
|------|----------|------------|----------|
| **缓存穿透** | 缓存空对象 + 短 TTL | `SET key "" EX 120` | `cache:shop:{id}` |
| **缓存击穿** | 互斥锁（setnx）控制重建 | `SET lock:shop:{id} 1 EX 10 NX` | `lock:shop:{id}` |
| **缓存击穿** | 逻辑过期 + 异步重建 | `SET key json_data` + 逻辑过期字段 | `cache:shop:{id}` |
| **缓存雪崩** | TTL 随机偏移 | — | — |

#### 2.3 关键设计

- **CacheClient** 已封装三种策略：`queryWithPassThrough`、`queryWithMutex`、`queryWithLogicalExpire`
- **空对象缓存**：`""` 空字符串，TTL 2 分钟，命中时需判断 `json != null && isNotBlank` 区分"空缓存"和"不存在"
- **互斥锁**：基于 `setnx`，锁 TTL 10 秒，获取失败休眠 50ms 递归重试
- **逻辑过期**：缓存永不过期（Redis TTL = -1），在 Value 中存过期时间字段，异步线程池重建

#### 2.4 现状评估

- ✅ `CacheClient` 完整实现三种策略
- ✅ `ShopServiceImpl.queryById()` 已集成 PassThrough
- ⚠️ 当前注释掉了 Mutex 和 LogicalExpire 方案，需要放开
- ⚠️ 需要在 `ShopServiceImpl` 中增加随机 TTL 防雪崩

---

### 功能三：优惠券秒杀（Lua + 分布式锁 + 三种消息队列）

#### 3.1 业务流程

```
┌──────────────────────────────────────────────────────────────┐
│                     秒杀核心流程                              │
├──────────────────────────────────────────────────────────────┤
│  1. 前端点击"抢购" → POST /api/voucher-order/seckill/{id}      │
│  2. 后端执行 Lua 脚本（原子性操作）：                           │
│     a. 判断秒杀是否开始/结束                                   │
│     b. 判断库存是否充足（seckill:stock:{voucherId}）           │
│     c. 判断是否已下单（一人一单：seckill:order:{voucherId}:{userId}）│
│     d. 扣减库存 → 标记已下单                                   │
│  3. Lua 返回结果码 → Java 判断                                 │
│  4. 异步下单：将订单消息推入消息队列                            │
│  5. 异步消费者：从队列取消息 → 写 MySQL（VoucherOrder）          │
│  6. 通知用户下单结果（Pub/Sub 或 轮询）                         │
└──────────────────────────────────────────────────────────────┘
```

#### 3.2 Redis 技术

| 用途 | Redis 数据结构 | Key/操作 | 说明 |
|------|---------------|----------|------|
| 库存计数 | **String (数值)** | `seckill:stock:{voucherId}` | 预加载秒杀库存，Lua `incrby -1` 扣减 |
| 一人一单 | **String (setnx)** | `seckill:order:{voucherId}:{userId}` | 用户下单标记，防止重复秒杀 |
| 分布式锁 | **String (set nx ex)** | `lock:seckill:{voucherId}` | 防止库存预加载并发问题 |
| Lua 原子操作 | **Lua Script** | — | 将"判断+扣减+标记"三步封装为原子操作，避免网络往返 |
| 异步下单队列① | **List** | `seckill:order:list` | `lpush` 生产 / `brpop` 消费，简单但不支持消息确认 |
| 异步下单队列② | **Pub/Sub** | `seckill:order:channel` | 发布订阅模式通知下单状态，不持久化 |
| 异步下单队列③ | **Stream** | `seckill:order:stream` + `group1` | 消费者组 + `XACK` 确认 + Pending 处理，可靠消费 |

#### 3.3 Lua 脚本设计

```lua
-- seckill.lua
-- KEYS[1]: 库存 Key (seckill:stock:{voucherId})
-- KEYS[2]: 订单 Key (seckill:order:{voucherId}:{userId})
-- ARGV[1]: 用户ID
-- 返回值: 0=成功, 1=库存不足, 2=重复下单

local stock = redis.call('get', KEYS[1])
if tonumber(stock) <= 0 then
    return 1  -- 库存不足
end
if redis.call('exists', KEYS[2]) == 1 then
    return 2  -- 重复下单
end
redis.call('incrby', KEYS[1], -1)
redis.call('set', KEYS[2], '1', 'EX', 3600)
return 0
```

#### 3.4 三种消息队列对比

| 特性 | List (lpush/brpop) | Pub/Sub | Stream |
|------|---------------------|---------|--------|
| 消息持久化 | ✅ 支持（Redis RDB/AOF） | ❌ 不持久化 | ✅ 支持 |
| 消息确认(ACK) | ❌ brpop即删 | ❌ 无ACK | ✅ XACK 确认 |
| 消费者组 | ❌ 不支持 | ❌ 不支持 | ✅ XREADGROUP |
| Pending 处理 | ❌ 无 | ❌ 无 | ✅ XPENDING + XCLAIM |
| 适用场景 | 简单异步解耦 | 实时通知（允许丢失） | 可靠消费（不允许丢失） |
| 本项目用途 | 异步下单（基础版） | 下单状态通知 | 异步下单（生产版，推荐） |

#### 3.5 现状评估

- ❌ `VoucherOrderController` 仅返回 "功能未完成"
- ❌ `VoucherOrderServiceImpl` 空实现
- ❌ 缺少 Lua 脚本文件
- ❌ 缺少三种队列的消费者代码
- ❌ 缺少库存预加载逻辑
- ✅ 已有 `SECKILL_STOCK_KEY` 常量定义

---

### 功能四：附近的商户（Redis GEO）

#### 3.1 业务流程

```
商户信息存入时 → GEOADD shop:geo 经度 纬度 商户ID
用户搜索附近 → GEOSEARCH shop:geo FROMLONLAT 用户经度 用户纬度 BYRADIUS 5000 m
→ 返回附近商户ID列表 → 批量查询商户详情 → 计算距离并展示
```

#### 3.2 Redis 技术

| Redis 命令 | 说明 |
|------------|------|
| `GEOADD shop:geo x y member` | 存储商户经纬度（member=shopId） |
| `GEOSEARCH shop:geo FROMLONLAT x y BYRADIUS 5000 m WITHDIST` | 按距离搜索附近商户，返回距离 |
| `GEODIST shop:geo member1 member2` | 计算两个商户间距离 |

#### 3.3 关键设计

- **Key 设计**: `shop:geo` — 一个 Key 存储所有商户的经纬度
- **距离单位**: 使用米(m)
- **分页**: GEOSEARCH 不支持分页 → 先查全部附近商户ID → Java 层做分页
- **数据初始化**: 应用启动时或商户新增时将 MySQL 数据加载到 Geo

#### 4.4 现状评估

- ❌ 未实现
- ✅ 已有 `SHOP_GEO_KEY` 常量定义
- ✅ `Shop` 实体已有 `x(经度)`, `y(纬度)`, `distance` 字段

---

### 功能五：UV 统计（Redis HyperLogLog）

#### 5.1 业务流程

```
用户访问商户详情页 → PFADD uv:shop:{shopId}:{yyyyMMdd} userId
查询商户UV → PFCOUNT uv:shop:{shopId}:{yyyyMMdd}
→ 返回今日UV数（误差约 0.81%）
```

#### 5.2 Redis 技术

| Redis 命令 | 说明 |
|------------|------|
| `PFADD uv:shop:{shopId}:{date} userId` | 记录独立访客（自动去重，内存固定 12KB/Key） |
| `PFCOUNT uv:shop:{shopId}:{date}` | 统计 UV 数量 |
| `PFMERGE uv:shop:{shopId}:total uv:shop:{shopId}:{date1} uv:shop:{shopId}:{date2}` | 合并多天 UV |

#### 5.3 为什么用 HyperLogLog 而不是 Set？

- **内存对比**：Set 存 100 万用户 ≈ 80MB；HyperLogLog 存 100 万 ≈ 12KB
- **UV 场景**：允许 0.81% 统计误差，无需精确数字
- **Key 设计**：按天分片 `uv:shop:{id}:{yyyyMMdd}`，默认 30 天滚动

#### 5.4 现状评估

- ❌ 完全未实现
- 需新建 `UvStatistics` 相关工具类或 Service

---

### 功能六：用户签到（Redis BitMap）

#### 6.1 业务流程

```
用户每日签到 → 计算当月第几天(offset) → SETBIT sign:{userId}:{yyyyMM} offset 1
查询连续签到天数 → BITFIELD sign:{userId}:{yyyyMM} GET u{本月天数} 0
→ 从末位遍历连续1的个数
查询本月签到日历 → BITFIELD sign:{userId}:{yyyyMM} GET u{本月天数} 0 → 前端渲染方格
```

#### 6.2 Redis 技术

| Redis 命令 | 说明 |
|------------|------|
| `SETBIT sign:{userId}:{yyyyMM} offset 1` | 设置第 offset 天签到状态为 1 |
| `GETBIT sign:{userId}:{yyyyMM} offset` | 查询第 offset 天是否签到 |
| `BITFIELD sign:{userId}:{yyyyMM} GET u{dayOfMonth} 0` | 获取当月所有签到位（无符号整型） |
| `BITCOUNT sign:{userId}:{yyyyMM}` | 统计当月签到总天数 |

#### 6.3 为什么用 BitMap 而不是 Set 或 DB？

- **内存极度压缩**：365 天 ≈ 46 字节（vs Set 的 365×20B ≈ 7KB）
- **天然适合签到**：签到只有"已签到/未签到"两种状态（1 bit）
- **高效统计**：BITCOUNT 是 O(N) 但 N 极小（最多 365 位=46字节）
- **MySQL tb_sign 表**：作为持久化备份（每月末同步或用户查询历史时回查）

#### 6.4 Key 设计

```
sign:{userId}:{yyyyMM}

示例：
sign:1001:202606  → 2026年6月用户1001的签到BitMap
offset = 6月22日 → 第22位
SETBIT sign:1001:202606 21 1  (offset 从0开始)
```

#### 6.5 连续签到计算

```
1. 获取本月 BITFIELD GET u{本月总天数}
2. 从右往左（今天→月初）遍历每一位
3. 统计连续为 1 的天数，遇到 0 停止
4. 返回连续天数
```

#### 6.6 现状评估

- ❌ 未实现
- ✅ 已有 `USER_SIGN_KEY` 常量定义
- ✅ 已有 `tb_sign` 表（MySQL 备份用）
- ⚠️ 缺少 `Sign` 实体类

---

### 功能七：好友关注（Redis Set）

#### 7.1 业务流程

```
关注 → SADD follow:{userId} targetUserId
      → SADD fans:{targetUserId} userId（维护反向粉丝集合）
取关 → SREM follow:{userId} targetUserId
      → SREM fans:{targetUserId} userId
查询共同关注 → SINTER follow:{userId1} follow:{userId2}
判断是否关注 → SISMEMBER follow:{userId} targetUserId
关注列表 → SMEMBERS follow:{userId}
```

#### 7.2 Redis 技术

| Redis 命令 | Key | 说明 |
|------------|-----|------|
| `SADD follow:{userId} targetId` | `follow:{userId}` | 关注集合 |
| `SADD fans:{targetId} userId` | `fans:{targetId}` | 粉丝集合 |
| `SREM follow:{userId} targetId` | — | 取关时同时操作两个 Set |
| `SISMEMBER follow:{userId} targetId` | — | 判断是否已关注 |
| `SINTER follow:{userA} follow:{userB}` | — | 求交集 = 共同关注 |
| `SCARD follow:{userId}` | — | 关注数 |
| `SCARD fans:{userId}` | — | 粉丝数 |

#### 7.3 为什么用 Set 而不是关系型DB查询？

- **O(1) 判断是否关注**：`SISMEMBER` 时间复杂度 O(1)，MySQL 需要索引扫描
- **共同关注天然是交集**：`SINTER` 一行命令，MySQL 需要 JOIN 或子查询
- **粉丝列表动态聚合**：高并发场景下 Redis 抗压能力强

#### 7.4 数据双写策略

```
关注操作：
  1. 先写 MySQL (tb_follow) — 持久化
  2. 再写 Redis (SADD follow + SADD fans) — 缓存
  
查询操作：
  1. 优先读 Redis
  2. Redis 不存在则查 MySQL 并回写 Redis
```

#### 7.5 现状评估

- ❌ `FollowController` 空壳
- ❌ `FollowServiceImpl` 空实现
- ❌ 未实现任何关注/取关/共同关注逻辑
- ✅ 已有 `tb_follow` 表结构和 `Follow` 实体

---

### 功能八：达人探店（List + SortedSet）

#### 8.1 业务流程

**8.1.1 笔记点赞（List 实现）**

```
用户点赞 → LPUSH blog:liked:{blogId} userId    （按点赞时间倒序）
取消点赞 → LREM blog:liked:{blogId} 1 userId    （移除指定元素）
查询前5个点赞用户 → LRANGE blog:liked:{blogId} 0 4
判断是否已点赞 → LLEN blog:liked:{blogId} + 遍历 → 替换为 SISMEMBER 更优
```

**8.1.2 点赞排行榜（SortedSet 实现）**

```
用户点赞 → ZADD blog:liked:rank:{blogId} timestamp userId  （score = 毫秒时间戳）
取消点赞 → ZREM blog:liked:rank:{blogId} userId
查询排行榜 → ZREVRANGE blog:liked:rank:{blogId} 0 9 WITHSCORES  （时间倒序 Top10）
判断是否已点赞 → ZSCORE blog:liked:rank:{blogId} userId != nil
```

#### 8.2 Redis 技术

| 功能 | Redis 数据结构 | Key 格式 | 核心操作 |
|------|---------------|----------|----------|
| 点赞列表（时间倒序） | **List** | `blog:liked:{blogId}` | `LPUSH` / `LRANGE` / `LREM` |
| 点赞排行榜 | **SortedSet** | `blog:liked:rank:{blogId}` | `ZADD` (score=时间戳) / `ZREVRANGE` |
| 收件箱（推模式） | **SortedSet** | `feed:{userId}` | `ZADD` (score=时间戳) / `ZREVRANGEBYSCORE` |

#### 8.3 收件箱推送（Feed 流）

```
用户A发布笔记 → 查询用户A的粉丝列表(SMEMBERS fans:{userA})
→ 遍历粉丝 → ZADD feed:{粉丝ID} 笔记ID 时间戳（推模式）
→ 粉丝查看收件箱 → ZREVRANGEBYSCORE feed:{粉丝ID} max min WITHSCORES LIMIT offset count
```

#### 8.4 为什么用 SortedSet 做排行榜？

- **天然按分数排序**：ZADD score=时间戳毫秒，ZREVRANGE 即按时间倒序
- **去重**：同一用户多次点赞自动覆盖（Score 更新）
- **范围查询**：支持分页展示 Top N

#### 8.5 为什么用 List 做点赞展示？

- **FIFO 先入先出**：LPUSH 新点赞到头，LRANGE 取前 N 个
- **简单够用**：展示"最新点赞的5个用户"不需要排序、不需要去重（List 允许重复）
- 但**更推荐 SortedSet**：兼具排序 + 去重 + 分页能力，一个结构搞定

#### 8.6 现状评估

- ❌ `BlogController.likeBlog()` 仅做了 SQL `liked = liked + 1`，未使用 Redis
- ❌ 缺少 SortedSet 排行榜、Feed 流推送
- ✅ 已有 `BLOG_LIKED_KEY`、`FEED_KEY` 常量
- ✅ 已有 `ScrollResult` DTO（滚动分页结果）
- ✅ `Blog` 实体已有 `isLike` 字段（@TableField exist=false）

---

## 5. Redis 数据结构与功能映射表

> 这是本文档最重要的章节，明确标注每种 Redis 高级特性在 city-review 项目中的落地位置。

| Redis 数据结构 | 功能模块 | 业务场景 | Redis Key 示例 | 核心命令 | 选择理由 |
|---------------|----------|----------|----------------|----------|----------|
| **String** | 短信登录 | 验证码存储 | `login:code:13686869696` | `SET key code EX 300` | 简单 KV，5分钟过期，天然 TTL 支持 |
| **String** | 短信登录 | 分布式锁 | `lock:seckill:10` | `SET key 1 EX 10 NX` | setnx 原子性互斥，防止超卖和缓存击穿 |
| **String** | 秒杀 | 库存计数 | `seckill:stock:10` | `GET` / `INCRBY -1` | 数值型操作，Lua 中原子扣减 |
| **String** | 秒杀 | 一人一单标记 | `seckill:order:10:1001` | `SET key 1 EX 3600 NX` | setnx 去重，防止同用户重复秒杀 |
| **String** | 商户缓存 | 缓存空对象 | `cache:shop:99` | `SET key "" EX 120` | 空值缓存防止缓存穿透，短 TTL 减少内存占用 |
| **Hash** | 短信登录 | 用户 Session | `login:token:uuid-token` | `HSET` / `HGETALL` / `EXPIRE` | 存储用户信息，支持按字段读取，替代 HttpSession 实现分布式 Session |
| **List** | 秒杀 | 异步下单队列① | `seckill:order:list` | `LPUSH` / `BRPOP` | 生产者-消费者解耦，异步落库，简单队列 |
| **List** | 达人探店 | 点赞用户列表 | `blog:liked:25` | `LPUSH` / `LRANGE 0 4` | 按时间倒序展示最新点赞的 N 个用户 |
| **Set** | 好友关注 | 关注集合 | `follow:1001` | `SADD` / `SREM` / `SMEMBERS` | 无序不重复，O(1) 判断是否关注 |
| **Set** | 好友关注 | 粉丝集合 | `fans:1002` | `SADD` / `SCARD` | 反向维护粉丝列表，方便推送通知 |
| **Set** | 好友关注 | 共同关注 | `SINTER follow:A follow:B` | `SINTER` | 天然求交集，MySQL 需 JOIN 开销大 |
| **SortedSet** | 达人探店 | 点赞排行榜 | `blog:liked:rank:25` | `ZADD score(timestamp)` / `ZREVRANGE` | 按时间排行，自动去重，支持分页，比 List+遍历 更高效 |
| **SortedSet** | 达人探店 | 收件箱(Feed流) | `feed:1001` | `ZADD` / `ZREVRANGEBYSCORE` | 推模式实现关注流，按时间排序，支持滚动分页 |
| **Geo** | 附近的商户 | 商户经纬度 | `shop:geo` | `GEOADD` / `GEOSEARCH` / `GEODIST` | 原生地理位置计算，替代 MySQL 经纬度公式计算（高效） |
| **BitMap** | 用户签到 | 签到打卡 | `sign:1001:202606` | `SETBIT offset 1` / `BITFIELD GET` / `BITCOUNT` | 1 bit 存一天，极度省内存（365天≈46字节），天然适合二值状态 |
| **HyperLogLog** | UV 统计 | 独立访客统计 | `uv:shop:10:20260622` | `PFADD` / `PFCOUNT` | 内存固定 12KB，百万级 UV 允许 0.81% 误差，比 Set 省 99.9% 内存 |
| **Stream** | 秒杀 | 可靠异步下单 | `seckill:order:stream` | `XADD` / `XREADGROUP` / `XACK` / `XPENDING` | 消费者组 + ACK 机制保证消息不丢，Pending 处理死信，生产级方案 |
| **Pub/Sub** | 秒杀 | 下单状态通知 | `seckill:order:channel` | `PUBLISH` / `SUBSCRIBE` | 实时推送下单结果给前端（WebSocket 或轮询），允许消息丢失（非关键路径） |
| **Lua Script** | 秒杀 | 原子扣库存+一人一单 | `seckill.lua` | `EVAL script 2 key1 key2 arg1` | 将"判断库存→判断重复→扣库存→标记已下单"封装为原子操作，避免网络往返和并发竞争 |

### 5.1 数据结构选型决策树

```
需要存储什么？
├── 简单 KV（验证码、锁、计数） → String
├── 对象多字段（用户Session） → Hash
├── 有序列表（点赞时间线、排行榜） → SortedSet
├── 无序集合（关注/粉丝，去重但不排序） → Set
├── 简单队列（异步下单基础版） → List
├── 可靠队列（异步下单生产版） → Stream
├── 实时通知（允许丢失） → Pub/Sub
├── 地理位置（附近商户） → Geo
├── 二值状态（签到/未签到） → BitMap
└── 海量去重计数（UV，允许误差） → HyperLogLog
```

---

## 6. API 接口设计

### 6.1 统一响应格式

```json
{
  "code": 200,
  "msg": "ok",
  "data": {}
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 401 | 未登录 |
| 403 | 无权限 |
| 500 | 服务器异常 |

### 6.2 接口清单（全部前缀 `/api/`）

#### 用户模块
| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/user/code` | 发送验证码 | 否 |
| POST | `/api/user/login` | 登录 | 否 |
| POST | `/api/user/logout` | 登出 | 是 |
| GET | `/api/user/me` | 获取当前用户 | 是 |
| GET | `/api/user/info/{id}` | 获取用户详情 | 否 |

#### 商户模块
| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/shop/{id}` | 商户详情（含UV统计） | 否 |
| POST | `/api/shop` | 新增商户 | 是 |
| PUT | `/api/shop` | 更新商户 | 是 |
| GET | `/api/shop/of/type` | 按类型分页 | 否 |
| GET | `/api/shop/of/name` | 按名称搜索 | 否 |
| GET | `/api/shop/of/nearby` | 附近商户（Geo） | 否 |
| GET | `/api/shop-type/list` | 商户类型列表 | 否 |

#### 优惠券模块
| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/voucher` | 新增普通券 | 是 |
| POST | `/api/voucher/seckill` | 新增秒杀券 | 是 |
| GET | `/api/voucher/list/{shopId}` | 店铺券列表 | 否 |

#### 秒杀下单模块
| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/voucher-order/seckill/{id}` | 秒杀下单 | 是 |
| GET | `/api/voucher-order/result/{id}` | 查询秒杀结果 | 是 |

#### 探店笔记模块
| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/blog` | 发布笔记 | 是 |
| PUT | `/api/blog/like/{id}` | 点赞/取消点赞 | 是 |
| GET | `/api/blog/likes/{id}` | 点赞排行榜 | 否 |
| GET | `/api/blog/of/me` | 我的笔记 | 是 |
| GET | `/api/blog/hot` | 热门笔记 | 否 |
| GET | `/api/blog/of/follow` | 关注流(Feed) | 是 |
| GET | `/api/blog/{id}` | 笔记详情 | 否 |

#### 好友关注模块
| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| PUT | `/api/follow/{id}/{isFollow}` | 关注/取关 | 是 |
| GET | `/api/follow/or/not/{id}` | 是否关注 | 是 |
| GET | `/api/follow/common/{id}` | 共同关注 | 是 |
| GET | `/api/follow/list/{id}` | 关注列表 | 否 |
| GET | `/api/follow/fans/{id}` | 粉丝列表 | 否 |

#### 签到模块（新增 Controller）
| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/sign` | 签到 | 是 |
| GET | `/api/sign/count` | 签到天数 | 是 |
| GET | `/api/sign/calendar` | 签到日历 | 是 |

#### 上传模块
| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/upload/blog` | 上传图片 | 是 |
| GET | `/api/upload/blog/delete` | 删除图片 | 是 |

---

## 7. 前端页面规划

所有前端页面放入 `src/main/resources/static/`，无 Nginx。

### 7.1 页面清单

项目采用 **Vue 3 单页应用（SPA）** 模式，通过 Vue Router 管理路由，所有页面均为 Vue 组件。

| 路由路径 | 页面名称 | 功能说明 |
|----------|----------|----------|
| `/` | 首页 | 商户搜索、类型导航、热门推荐 |
| `/shop/:id` | 商户详情 | 商户信息、优惠券列表、点评、附近位置 |
| `/login` | 登录 | 手机号 + 验证码登录 |
| `/blog` | 笔记列表 | 探店笔记列表（热门 / 关注流） |
| `/blog/:id` | 笔记详情 | 笔记内容、评论、点赞排行榜 |
| `/voucher` | 优惠券列表 | 优惠券与秒杀券展示 |
| `/seckill` | 秒杀活动 | 秒杀活动页，实时抢购 |
| `/user` | 个人中心 | 用户信息、签到、积分、我的笔记 |
| `/follow` | 好友关注 | 关注 / 粉丝管理 |

### 7.2 前端技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| **Vue 3** | 3.x | 渐进式框架，Composition API + `<script setup>` |
| **Vue Router** | 4.x | 前端路由（Hash 模式），SPA 页面导航 |
| **Axios** | — | HTTP 客户端，统一 `/api/` 前缀，拦截器注入 Token |
| **Vite** | 5.x | 构建工具，开发热更新，打包输出到 `static/` |
| **LocalStorage** | — | 持久化 Token 与用户信息 |
| **CSS3** | — | 响应式布局，移动端适配 |

### 7.3 前端项目结构

```
src/main/resources/static/          ← Vite build 输出目录
├── index.html                      ← SPA 入口（Spring Boot 默认首页）
├── assets/                         ← 打包后的 JS/CSS/图片
└── ...

frontend/                           ← Vue 3 源码目录（开发目录）
├── vite.config.js                  ← Vite 配置（输出到 ../src/main/resources/static/）
├── package.json
├── index.html                      ← Vite 开发入口
├── src/
│   ├── main.js                     ← Vue 挂载 + Router + Axios 配置
│   ├── App.vue                     ← 根组件
│   ├── router/
│   │   └── index.js                ← 路由配置
│   ├── views/                      ← 页面级组件
│   │   ├── Home.vue                ← 首页
│   │   ├── ShopDetail.vue          ← 商户详情
│   │   ├── Login.vue               ← 登录页
│   │   ├── BlogList.vue            ← 笔记列表
│   │   ├── BlogDetail.vue          ← 笔记详情
│   │   ├── VoucherList.vue         ← 优惠券列表
│   │   ├── Seckill.vue             ← 秒杀活动
│   │   ├── UserCenter.vue          ← 个人中心
│   │   └── Follow.vue              ← 好友关注
│   ├── components/                 ← 公共组件
│   │   ├── NavBar.vue
│   │   ├── ShopCard.vue
│   │   └── ...
│   ├── api/                        ← API 请求封装
│   │   ├── request.js              ← Axios 实例（baseURL: /api）
│   │   ├── user.js
│   │   ├── shop.js
│   │   └── ...
│   └── utils/                      ← 工具函数
│       └── auth.js                 ← Token 管理
└── public/                         ← 静态资源（不参与打包）

---

## 8. 部署与配置说明

### 8.1 application.yaml 关键配置

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/city_review?useSSL=false&serverTimezone=UTC
    username: root
    password: TODO  # 请替换为实际密码

  redis:
    host: 127.0.0.1
    port: 6379
    # password: TODO  # 如果 Redis 设置了密码，请取消注释并替换
```

### 8.2 启动步骤

1. 修改 `application.yaml` 中 MySQL/Redis 密码
2. 执行 `docs/init.sql` 初始化数据库
3. **构建前端**：进入 `frontend/` 目录，执行 `npm install && npm run build`（Vite 将输出到 `src/main/resources/static/`）
4. IDE 中运行 `CityReviewApplication.main()`
5. 访问 `http://localhost:8081/`

### 8.3 SPA 路由回退配置

Vue Router 使用 Hash 模式（`/#/path`），无需 Spring Boot 额外配置。若后续改为 History 模式，需在 `MvcConfig` 中增加：

```java
// 将非 /api/** 的 404 请求转发到 index.html，交由 Vue Router 处理
@Override
public void addViewControllers(ViewControllerRegistry registry) {
    registry.addViewController("/{spring:^(?!api).*$}")
            .setViewName("forward:/index.html");
}
```

### 8.4 验证码获取方式

启动后在控制台查找日志：
```log
===== city-review 登录验证码为：123456 =====
```

---

## 附录：待办事项清单

| 序号 | 任务 | 优先级 | 依赖 |
|------|------|--------|------|
| 1 | 改造 Result.java → `{code, msg, data}` | P0 | — |
| 2 | 修改 Controller `@RequestMapping` 添加 `/api/` 前缀 | P0 | 1 |
| 3 | 修改 SystemConstants.IMAGE_UPLOAD_DIR 为项目路径 | P0 | — |
| 4 | 实现秒杀完整链路（Lua + 锁 + 三种队列） | P0 | 2 |
| 5 | 实现附近商户（Geo） | P1 | 2 |
| 6 | 实现 UV 统计（HyperLogLog） | P1 | 2 |
| 7 | 实现签到（BitMap） | P1 | 2 |
| 8 | 实现好友关注（Set） | P1 | 2 |
| 9 | 实现探店点赞（SortedSet）+ Feed 流 | P1 | 2 |
| 10 | 新建 Sign 实体类 | P1 | — |
| 11 | 编写所有前端 HTML 页面 | P1 | 2-9 |
| 12 | 生成 docs/init.sql | P1 | — |
| 13 | 生成 Lua 脚本文件 | P0 | — |

---

> **文档维护者**：Claude Code (AI Agent)  
> **项目仓库**：`d:\Code\city-review`  
> **下一步**：等待用户回复"确认文档"或"继续编码"后，进入代码实现阶段
