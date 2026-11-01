package com.yzj.controller;

import com.yzj.exception.BusinessException;
import com.yzj.pojo.Result;
import com.yzj.pojo.Submission;
import com.yzj.pojo.SubmitRequest;
import com.yzj.service.SubmissionService;
import com.yzj.utils.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;
    private final RateLimiter rateLimiter;

    public SubmissionController(SubmissionService submissionService, RateLimiter rateLimiter) {
        this.submissionService = submissionService;
        this.rateLimiter = rateLimiter;
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

        return Result.success(submissionService.submit(req));
    }
}