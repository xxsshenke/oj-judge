-- 固定窗口限流
-- KEYS[1] 限流的 key，例如 oj:rate:127.0.0.1
-- ARGV[1] 窗口秒数
-- ARGV[2] 窗口内允许的最大次数
-- 返回：>0 表示当前是第几次（放行），0 表示超限（拒绝）

local count = redis.call('INCR', KEYS[1])

-- 第一次请求时设置过期，窗口结束后 key 自动消失、计数归零
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end

if count > tonumber(ARGV[2]) then
    return 0
end

return count
