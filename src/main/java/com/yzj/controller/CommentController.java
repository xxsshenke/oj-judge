package com.yzj.controller;

import com.yzj.exception.BusinessException;
import com.yzj.pojo.CommentCreateRequest;
import com.yzj.pojo.CommentVO;
import com.yzj.pojo.PageResult;
import com.yzj.pojo.Result;
import com.yzj.service.CommentService;
import com.yzj.utils.RateLimiter;
import com.yzj.utils.RedisConstant;
import com.yzj.utils.UserHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论接口。
 *
 * 评论的"列表/发表"挂在题解下（/solutions/{id}/comments），
 * 单条评论的删除挂在自己身上（/comments/{id}）。
 */
@RestController
public class CommentController {

    private static final int RATE_WINDOW_SECONDS = 60;
    private static final int RATE_LIMIT_PER_WINDOW = 10;

    private final CommentService commentService;
    private final RateLimiter rateLimiter;

    public CommentController(CommentService commentService, RateLimiter rateLimiter) {
        this.commentService = commentService;
        this.rateLimiter = rateLimiter;
    }

    /** 评论列表（免登录）—— 主评论分页，每条内嵌前 10 条回复 */
    @GetMapping("/solutions/{solutionId}/comments")
    public Result pageBySolution(@PathVariable Integer solutionId,
                                 @RequestParam(required = false, defaultValue = "1") int pageNum,
                                 @RequestParam(required = false, defaultValue = "10") int pageSize) {
        PageResult<CommentVO> page = commentService.pageBySolution(
                solutionId, pageNum, pageSize, UserHolder.get());
        return Result.success(page);
    }

    /** 发表评论 / 回复（需登录） */
    @PostMapping("/solutions/{solutionId}/comments")
    public Result create(@PathVariable Integer solutionId, @RequestBody CommentCreateRequest req) {
        Integer userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException("未登录");
        }

        if (!rateLimiter.tryAcquire(RedisConstant.RATE_COMMENT_KEY + userId,
                RATE_WINDOW_SECONDS, RATE_LIMIT_PER_WINDOW)) {
            throw new BusinessException("评论太频繁，请稍后再试");
        }

        return Result.success(commentService.create(solutionId, req, userId));
    }

    /** 删除评论（需登录，仅作者） */
    @DeleteMapping("/comments/{id}")
    public Result delete(@PathVariable Integer id) {
        Integer userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        commentService.delete(id, userId);
        return Result.success();
    }
}
