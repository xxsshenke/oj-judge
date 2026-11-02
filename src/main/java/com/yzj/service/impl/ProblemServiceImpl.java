package com.yzj.service.impl;

import cn.hutool.json.JSONUtil;
import com.yzj.exception.BusinessException;
import com.yzj.mapper.ProblemMapper;
import com.yzj.pojo.Problem;
import com.yzj.service.ProblemService;
import com.yzj.utils.RedisConstant;
import java.util.concurrent.ThreadLocalRandom;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Service
public class ProblemServiceImpl implements ProblemService {

    private final ProblemMapper problemMapper;

    private final StringRedisTemplate redis;

    private final RedissonClient redissonClient;

    public ProblemServiceImpl(ProblemMapper problemMapper, StringRedisTemplate redis, RedissonClient redissonClient) {
        this.problemMapper = problemMapper;
        this.redis = redis;
        this.redissonClient = redissonClient;
    }

    @Override
    public List<Problem> list() {
        return problemMapper.findAll();
    }

    @Override
    public Problem detail(Integer id) {
        String key = RedisConstant.CACHE_OJ_KEY + id;

        Problem cached = readCache(key, id);
        if (cached != null) return cached;

        RLock lock = redissonClient.getLock(RedisConstant.CACHE_LOCK_KEY + id);

        boolean locked;
        try {
            // 只等 200ms：大约是一次查库的时间。等不到就说明别人正在重建，
            // 直接读缓存即可，不必陪着阻塞 5 秒
            locked = lock.tryLock(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("请求被中断");
        }

        if (!locked) {
            // 等满了还没拿到：先看是不是别人已经建好了
            cached = readCache(key, id);
            if (cached != null) return cached;
            throw new BusinessException("系统繁忙，请稍后重试");
        }

        try {
            cached = readCache(key, id);       // 双重检查
            if (cached != null) return cached;
            return queryAndCache(key, id);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private Problem readCache(String key, Integer id) {
        String cache = redis.opsForValue().get(key);
        if (cache == null) return null;
        if (cache.isEmpty()) throw new BusinessException("题目不存在：" + id);
        return JSONUtil.toBean(cache, Problem.class);
    }
    private Problem queryAndCache(String key, Integer id) {
        Problem problem = problemMapper.findDetailById(id);

        if (problem == null) {
            // 防穿透：缓存空串 1 分钟
            redis.opsForValue().set(key, "", Duration.ofMinutes(1));
            throw new BusinessException("题目不存在：" + id);
        }

        // 防雪崩：TTL 加随机
        long ttl = 30 * 60 + ThreadLocalRandom.current().nextInt(0, 300);
        redis.opsForValue().set(key, JSONUtil.toJsonStr(problem), Duration.ofSeconds(ttl));

        return problem;
    }
}
