-- 实现判断秒杀库存与校验一人一单两个操作的原子性
-- 库存id 用于从redis中获取库存信息
local stockKey = KEYS[1]
-- 优惠券id 用于校验一人一单 这里最好不要加上业务id，不然后续从消息队列里取数据的时候会比较麻烦。
--local voucherKey = KEYS[2]

-- 库存不足，返回错误信息1
if (tonumber(redis.call("get", stockKey)) <= 0) then
    return 1
end

-- 用户重复下单，返回错误信息2
local voucherKey = ARGV[1]
local userId = ARGV[2]
if (redis.call("sismember", "seckill:voucher:" .. voucherKey, userId) == 1) then
    return 2
end

-- 当前用户可以下单，返回0
-- 1. 扣减库存
redis.call("incrby", stockKey, -1)

-- 2. 记录该用户未下过单
redis.call("sadd", "seckill:voucher:" .. voucherKey, userId)

-- 3. 将订单信息加入到消息队列中 xadd stream.orders * key value key value ...
-- 订单id
local idKey = ARGV[3]
redis.call("xadd", "stream.orders", "*", "voucher_id", voucherKey, "userId", userId, "id", idKey)
return 0

