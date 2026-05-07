-- 秒杀脚本
-- 参数说明：
-- KEYS[1]: 库存key (seckill:stock:voucherId)
-- KEYS[2]: 订单key (seckill:orders:voucherId) - 用于记录已购买用户
-- ARGV[1]: 用户ID
-- ARGV[2]: 订单ID
-- 键名参数列表
local stockKey = KEYS[1]
local orderKey = KEYS[2]
-- 附加参数列表
local userId = ARGV[1]
local orderId = ARGV[2]

-- 1. 判断用户是否已购买（幂等性检查）
--redis.call()：执行Redis命令，返回结果,sismember命令用于判断元素是否在集合中
local userExists = redis.call('sismember', orderKey, userId)
if userExists == 1 then
    -- 用户已购买，返回2表示重复购买
    return 2
end

-- 2. 获取当前库存
local stock = redis.call('get', stockKey)
if stock == false or stock == nil then
    -- 库存未初始化，返回3表示库存异常
    return 3
end

-- 3. 将库存转换为数字
local stockNum = tonumber(stock)
if stockNum == nil then
    -- 库存值异常，返回3
    return 3
end

-- 4. 判断库存是否充足
if stockNum <= 0 then
    -- 库存不足，返回0
    return 0
end

-- 5. 扣减库存
--redis.call()：执行Redis命令，返回结果,decr命令用于递减键值的整数
redis.call('decr', stockKey)

-- 6. 记录用户已购买
redis.call('sadd', orderKey, userId)

-- 7. 返回1表示秒杀成功
return 1
