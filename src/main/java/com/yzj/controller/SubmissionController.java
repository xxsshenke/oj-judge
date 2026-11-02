package com.yzj.controller;

import com.yzj.exception.BusinessException;
import com.yzj.pojo.Result;
import com.yzj.pojo.SubmitRequest;
import com.yzj.service.SubmissionService;
import com.yzj.utils.RateLimiter;
import com.yzj.utils.RedisConstant;
import com.yzj.utils.UserHolder;
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

    /**
     * 提交代码。
     *
     * 这个接口在 WebConfig 里**不在**公开列表内，所以能被走到就说明已登录，
     * UserHolder 里一定有 userId。
     */
    @PostMapping
    public Result submit(@RequestBody SubmitRequest req, HttpServletRequest request) {
        Integer userId = UserHolder.get();

        // 按用户限流：同一账号每分钟最多 10 次
        if (!rateLimiter.tryAcquire("oj:rate:submit:user:" + userId, 60, 10)) {
            throw new BusinessException("提交太频繁，请 1 分钟后再试");
        }

        // 判题中互斥：同一用户同时只允许一个提交在判题。
        // 用 userId 而不是 IP —— 校园网 NAT 下多人共用出口 IP，
        // 按 IP 会让别人判题时你交不了。
        // 靠 TTL 自动释放，代价是判完后最多多锁 60 秒。
        String judgingKey = RedisConstant.SUBMIT_LOCK_KEY + "user:" + userId;
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
