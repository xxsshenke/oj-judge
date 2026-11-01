package com.yzj.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Redis + Lua 的固定窗口限流。
 *
 * 为什么用 Lua：INCR 和 EXPIRE 必须原子执行。
 * 如果分两次调用，进程在中间崩溃会让 key 永不过期，用户被永久限流。
 */
@Slf4j
@Component
public class RateLimiter {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("script/limiter.lua"));
        this.script.setResultType(Long.class);
    }

    /**
     * @param key           限流 key
     * @param windowSeconds 窗口秒数
     * @param limit         窗口内允许的最大次数
     * @return true 表示放行
     */
    public boolean tryAcquire(String key, int windowSeconds, int limit) {
        Long result = redis.execute(script, List.of(key),
                String.valueOf(windowSeconds), String.valueOf(limit));
        return result != null && result > 0;
    }
}
