package com.yzj.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
import com.yzj.exception.BusinessException;
import com.yzj.mapper.ProblemMapper;
import com.yzj.pojo.Problem;
import com.yzj.service.ProblemService;
import com.yzj.utils.RedisConstant;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;


@Service
public class ProblemServiceImpl implements ProblemService {

    private final ProblemMapper problemMapper;

    private final StringRedisTemplate redis;


    public ProblemServiceImpl(ProblemMapper problemMapper, StringRedisTemplate redis) {
        this.problemMapper = problemMapper;
        this.redis = redis;
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

        String lockKey = RedisConstant.CACHE_LOCK_KEY + id;
        String token = UUID.randomUUID().toString();

        boolean locked = tryLock(lockKey, token);

        if (!locked) {
            // 没抢到 → 等别人建好
            for (int i = 0; i < 20; i++) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                cached = readCache(key, id);
                if (cached != null) return cached;
            }
            // 等太久，直接查库兜底
            return queryAndCache(key, id);
        }

        try {
            cached = readCache(key, id);       // ⭐ 双重检查
            if (cached != null) return cached;
            return queryAndCache(key, id);
        } finally {
            unLock(lockKey, token);
        }
    }

    private boolean tryLock(String key,String uuid){
        Boolean ok = redis.opsForValue().setIfAbsent(key, uuid, Duration.ofSeconds(10));
        return BooleanUtil.isTrue(ok);
    }

    private void unLock(String key,String uuid){
        if(uuid.equals(redis.opsForValue().get(key))){
            redis.delete(key);
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
