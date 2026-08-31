-- 1.参数列表
-- 1.1.优惠券id
local voucherId = ARGV[1]
-- 1.2.用户id
local userId = ARGV[2]
-- 1.3.订单id
local orderId = ARGV[3]
-- 1.4 当前时间戳（毫秒）
local now = tonumber(ARGV[4])

-- 2.数据key
-- 2.1.库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2.订单key
local orderKey = 'seckill:order:' .. voucherId
local beginKey = 'seckill:begin:' .. voucherId
local endKey = 'seckill:end:' .. voucherId

-- 3.脚本业务
-- 3.1.判断库存是否充足 get stockKey
local stock = tonumber(redis.call('get', stockKey))
local beginTime = tonumber(redis.call('get', beginKey))
local endTime = tonumber(redis.call('get', endKey))
if(not stock or not beginTime or not endTime) then
    return 3
end
if(now < beginTime) then
    return 4
end
if(now > endTime) then
    return 5
end
if(stock <= 0) then
    -- 3.2.库存不足，返回1
    return 1
end
-- 3.2.判断用户是否下单 SISMEMBER orderKey userId
if(redis.call('sismember', orderKey, userId) == 1) then
    -- 3.3.存在，说明是重复下单，返回2
    return 2
end
-- 3.4.扣库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)
-- 3.5.下单（保存用户）sadd orderKey userId
redis.call('sadd', orderKey, userId)
return 0
