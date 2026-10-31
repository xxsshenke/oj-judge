package com.yzj.service.impl;

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

        String cache = redis.opsForValue().get(key);
        // 命中 redis
        if (cache != null) {
            if (cache.isEmpty()) {
                // 命中"空值标记" → 说明这题不存在，直接拒绝，不查库
                throw new BusinessException("题目不存在：" + id);
            }
            return JSONUtil.toBean(cache, Problem.class);
        }

        Problem problem = problemMapper.findDetailById(id);
        if (problem == null) {
            //  缓存空串，TTL 短（1 分钟），防止反复打库
            redis.opsForValue().set(key, "", Duration.ofMinutes(1));
            throw new BusinessException("题目不存在：" + id);
        }

        long ttl = 30 * 60 + ThreadLocalRandom.current().nextInt(0, 300);
        redis.opsForValue().set(key, JSONUtil.toJsonStr(problem), Duration.ofSeconds(ttl));
        return problem;

    }
}
