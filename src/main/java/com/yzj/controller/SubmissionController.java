package com.yzj.controller;

import com.yzj.exception.BusinessException;
import com.yzj.pojo.PageResult;
import com.yzj.pojo.Result;
import com.yzj.pojo.Submission;
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

    /**
     * 按题目分页查询提交记录。
     *
     * 返回结构从"数组"改成"分页对象"，前端需要同步调整：
     *   { records: [...], total, pageNum, pageSize, pages, hasNext, hasPre }
     *
     * pageNum / pageSize 都用 @RequestParam(required = false) + 默认值。
     * 不用 defaultValue + 包装类型，是因为要能在服务层区分"没传"和"传了非法值"，
     * 而两者最终都收敛到默认值，所以这里直接给 int 更省事。
     */
    @GetMapping
    public Result pageByProblem(@RequestParam Integer problemId,
                                @RequestParam(required = false, defaultValue = "1") int pageNum,
                                @RequestParam(required = false, defaultValue = "20") int pageSize) {
        PageResult<Submission> page = submissionService.pageByProblem(problemId, pageNum, pageSize);
        return Result.success(page);
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
