-- ============================================================
-- city-review 秒杀 Lua 脚本
-- 功能：原子性完成"判断库存 → 判断重复 → 扣库存 → 标记已下单"
-- 为什么要用 Lua？
--   将 4 步 Redis 操作封装为一个原子事务，避免网络往返带来的竞态条件，
--   确保"一人一单 + 库存不超卖"在高并发下的正确性。
-- ============================================================

-- KEYS[1]: 秒杀库存 Key（seckill:stock:{voucherId}）
-- KEYS[2]: 一人一单 Key（seckill:order:{voucherId}:{userId}）
-- ARGV[1]: 用户 ID（userId）

-- 返回值约定：
--   0  → 秒杀成功
--   1  → 库存不足
--   2  → 该用户已下单（一人一单限制）

local voucherId = KEYS[1]
local orderKey = KEYS[2]
local userId = ARGV[1]

-- 1. 获取当前库存
local stockStr = redis.call('GET', voucherId)
if not stockStr then
    -- 库存未初始化，视为库存不足
    return 1
end

local stock = tonumber(stockStr)
if stock <= 0 then
    -- 库存不足
    return 1
end

-- 2. 判断用户是否已下单（一人一单，使用 SISMEMBER 检查 Set 集合）
--    选用 Set 而非 String 的原因：Set 可以存储该券所有已下单用户，方便后续统计
local exists = redis.call('SISMEMBER', orderKey, userId)
if exists == 1 then
    -- 该用户已下过单
    return 2
end

-- 3. 扣减库存
redis.call('INCRBY', voucherId, -1)

-- 4. 标记用户已下单
redis.call('SADD', orderKey, userId)

-- 5. 返回成功
return 0
