package com.yzj.controller;

import com.yzj.exception.BusinessException;
import com.yzj.pojo.PageResult;
import com.yzj.pojo.Result;
import com.yzj.pojo.SolutionDetailVO;
import com.yzj.pojo.SolutionSaveRequest;
import com.yzj.pojo.SolutionVO;
import com.yzj.service.SolutionService;
import com.yzj.utils.RateLimiter;
import com.yzj.utils.RedisConstant;
import com.yzj.utils.UserHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题解接口。
 *
 * 路径刻意分两组：
 *   /problems/{problemId}/solutions   按题目列题解、给题目发题解（语义上属于题目）
 *   /solutions/{id}                   单篇的读 / 改 / 删（语义上属于题解自己）
 *
 * 注意：/problems/* 这个已有白名单规则**匹配不到** /problems/1/solutions
 * （Ant 的 * 不跨 /），所以 WebConfig 里单独加了规则。
 */
@RestController
public class SolutionController {

    private static final int RATE_WINDOW_SECONDS = 60;
    private static final int RATE_LIMIT_PER_WINDOW = 3;

    private final SolutionService solutionService;
    private final RateLimiter rateLimiter;

    public SolutionController(SolutionService solutionService, RateLimiter rateLimiter) {
        this.solutionService = solutionService;
        this.rateLimiter = rateLimiter;
    }

    /** 某题下的题解列表（免登录） */
    @GetMapping("/problems/{problemId}/solutions")
    public Result pageByProblem(@PathVariable Integer problemId,
                                @RequestParam(required = false, defaultValue = "1") int pageNum,
                                @RequestParam(required = false, defaultValue = "10") int pageSize,
                                @RequestParam(required = false, defaultValue = "new") String sort) {
        PageResult<SolutionVO> page = solutionService.pageByProblem(
                problemId, sort, pageNum, pageSize, UserHolder.get());
        return Result.success(page);
    }

    /** 题解详情（免登录） */
    @GetMapping("/solutions/{id}")
    public Result detail(@PathVariable Integer id) {
        SolutionDetailVO detail = solutionService.detail(id, UserHolder.get());
        return Result.success(detail);
    }

    /** 发表题解（需登录） */
    @PostMapping("/problems/{problemId}/solutions")
    public Result create(@PathVariable Integer problemId, @RequestBody SolutionSaveRequest req) {
        Integer userId = requireLogin();

        if (!rateLimiter.tryAcquire(RedisConstant.RATE_SOLUTION_KEY + userId,
                RATE_WINDOW_SECONDS, RATE_LIMIT_PER_WINDOW)) {
            throw new BusinessException("发表太频繁，请稍后再试");
        }

        return Result.success(solutionService.create(problemId, req, userId));
    }

    /** 修改题解（需登录，仅作者） */
    @PutMapping("/solutions/{id}")
    public Result update(@PathVariable Integer id, @RequestBody SolutionSaveRequest req) {
        Integer userId = requireLogin();
        return Result.success(solutionService.update(id, req, userId));
    }

    /** 删除题解（需登录，仅作者；软删，其下评论一并软删） */
    @DeleteMapping("/solutions/{id}")
    public Result delete(@PathVariable Integer id) {
        Integer userId = requireLogin();
        solutionService.delete(id, userId);
        return Result.success();
    }

    /**
     * 兜底取 userId。
     *
     * 这些接口都不在 WebConfig 的公开列表里，理论上拦截器已经挡住了，
     * 走到这里 UserHolder 一定有值。这里再判一次是为了防御
     * "以后有人误把路径加进白名单" —— 那种情况下会变成 userId=null 直接写库，
     * 比抛异常严重得多。
     */
    private Integer requireLogin() {
        Integer userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        return userId;
    }
}
