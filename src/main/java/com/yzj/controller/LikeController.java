package com.yzj.controller;

import com.yzj.exception.BusinessException;
import com.yzj.pojo.LikeRecord;
import com.yzj.pojo.LikeResult;
import com.yzj.pojo.Result;
import com.yzj.service.LikeService;
import com.yzj.utils.RateLimiter;
import com.yzj.utils.RedisConstant;
import com.yzj.utils.UserHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点赞接口：题解和评论共用一套逻辑，只是 targetType 不同。
 *
 * <h3>为什么用 POST / DELETE 而不是一个 POST 翻转</h3>
 * toggle 在并发下会翻来翻去：用户手抖点两次、或前端超时重试一次，状态就反了。
 * POST（点赞）和 DELETE（取消）各自幂等 —— 重复调用结果不变，重试安全。
 */
@RestController
public class LikeController {

    private static final int RATE_WINDOW_SECONDS = 60;
    /** 每分钟 60 次：正常用户点不到，脚本刷不同 id 才会撞上 */
    private static final int RATE_LIMIT_PER_WINDOW = 60;

    private final LikeService likeService;
    private final RateLimiter rateLimiter;

    public LikeController(LikeService likeService, RateLimiter rateLimiter) {
        this.likeService = likeService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/solutions/{id}/like")
    public Result likeSolution(@PathVariable Integer id) {
        return doLike(LikeRecord.TYPE_SOLUTION, id, true);
    }

    @DeleteMapping("/solutions/{id}/like")
    public Result unlikeSolution(@PathVariable Integer id) {
        return doLike(LikeRecord.TYPE_SOLUTION, id, false);
    }

    @PostMapping("/comments/{id}/like")
    public Result likeComment(@PathVariable Integer id) {
        return doLike(LikeRecord.TYPE_COMMENT, id, true);
    }

    @DeleteMapping("/comments/{id}/like")
    public Result unlikeComment(@PathVariable Integer id) {
        return doLike(LikeRecord.TYPE_COMMENT, id, false);
    }

    private Result doLike(int targetType, Integer targetId, boolean like) {
        Integer userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException("未登录");
        }

        // 加限流的原因：同一目标靠 Redis SADD 幂等挡掉了，
        // 但脚本可以对**不同 id** 疯狂点赞，那就要靠这里挡
        if (!rateLimiter.tryAcquire(RedisConstant.RATE_LIKE_KEY + userId,
                RATE_WINDOW_SECONDS, RATE_LIMIT_PER_WINDOW)) {
            throw new BusinessException("操作太频繁，请稍后再试");
        }

        LikeResult result = like
                ? likeService.like(targetType, targetId, userId)
                : likeService.unlike(targetType, targetId, userId);
        return Result.success(result);
    }
}
