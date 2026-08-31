-- Kafka 发送失败、订单消息进入 DLT 或订单取消时，幂等恢复 Redis 资格和库存。
local voucherId = ARGV[1]
local userId = ARGV[2]
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

if(redis.call('sismember', orderKey, userId) == 0) then
    return 0
end

redis.call('incrby', stockKey, 1)
redis.call('srem', orderKey, userId)
return 1
