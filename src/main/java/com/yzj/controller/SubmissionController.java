package com.yzj.controller;

import com.yzj.exception.BusinessException;
import com.yzj.pojo.Result;
import com.yzj.pojo.Submission;
import com.yzj.pojo.SubmitRequest;
import com.yzj.service.SubmissionService;
import com.yzj.utils.RateLimiter;
import com.yzj.utils.RedisConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;
    private final RateLimiter rateLimiter;
    private final StringRedisTemplate stringRedisTemplate;

    public SubmissionController(SubmissionService submissionService,
                                RateLimiter rateLimiter,
                                StringRedisTemplate stringRedisTemplate) {
        this.submissionService = submissionService;
        this.rateLimiter = rateLimiter;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @GetMapping("/{id}")
    public Result detail(@PathVariable Integer id) {
        return Result.success(submissionService.detail(id));
    }

    @GetMapping
    public Result listByProblem(@RequestParam Integer problemId) {
        return Result.success(submissionService.listByProblem(problemId));
    }

    @PostMapping
    public Result submit(@RequestBody SubmitRequest req, HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();     // 多级代理时取第一个
        }

        if (!rateLimiter.tryAcquire("oj:rate:submit:" + ip, 60, 10)) {
            throw new BusinessException("提交太频繁，请 1 分钟后再试");
        }

        // 判题中互斥：靠 TTL 自动释放，不落库、不追踪归属。
        // 代价是判完后最多多锁 60 秒；要精确释放得把 submitIp 持久化，
        // 等接入用户系统后改用 userId 再做。
        String judgingKey = RedisConstant.SUBMIT_LOCK_KEY + ip;
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                judgingKey, "1", Duration.ofSeconds(RedisConstant.SUBMIT_LOCK_TTL_SECONDS));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException("上一次提交还在判题中，请等待结果");
        }

        try {
            return Result.success(submissionService.submit(req));
        } catch (RuntimeException e) {
            // 提交本身失败时立刻释放，不让用户白等
            stringRedisTemplate.delete(judgingKey);
            throw e;
        }
    }
}