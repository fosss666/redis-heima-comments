-- 秒杀下单脚本

-- 1 编写所需变量
-- 1.1 优惠券id
local voucherId = ARGV[1];
-- 1.2 用户id
local userId = ARGV[2]
-- 1.5 订单id
local orderId = ARGV[3]
-- 1.3库存key
local stockKey = "seckill:stock:" .. voucherId
-- 1.4订单key
local orderKey = "seckill:order:" .. voucherId

-- 2 判断库存是否充足(获取到的结果时string,需要转成int)
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 2.1 库存不足，返回1
    return 1
end

-- 3 库存充足，判断是否重复下单
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 3.1 重复下单，返回2
    return 2
end

-- 3.2 不是重复下单，减库存，保存用户返回0
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)

-- 4 存入提前在redis创建的stream组中
redis.call('xadd', 'steam.orders', '*', 'voucherId', voucherId, 'userId', userId, 'id', orderId) -- 名称和VoucherOrder对象中一致
return 0











