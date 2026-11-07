package com.yzj.service;

import com.yzj.pojo.LikeResult;

import java.util.Collection;
import java.util.Set;

/**
 * 点赞服务：题解和评论共用同一套逻辑，只靠 targetType 区分。
 *
 * 抽象在这里的理由：如果不抽，4 个端点（题解赞/取消、评论赞/取消）
 * 会是 4 份几乎一字不差的代码。
 */
public interface LikeService {

    /**
     * 点赞（幂等）。
     *
     * 重复点赞不报错、不重复计数，直接返回当前状态。
     *
     * @param targetType {@link com.yzj.pojo.LikeRecord#TYPE_SOLUTION} 或 TYPE_COMMENT
     * @param targetId   目标 id
     * @param userId     操作人，由 UserHolder 提供
     */
    LikeResult like(int targetType, Integer targetId, Integer userId);

    /** 取消点赞（幂等）。本来没赞也返回成功，状态为未赞。 */
    LikeResult unlike(int targetType, Integer targetId, Integer userId);

    /**
     * 批量判断"当前用户赞过这批目标中的哪些"。
     *
     * 列表页用：一次拿全，不要循环查。
     *
     * @param userId 为 null（未登录）时直接返回空集合，**不查 Redis**
     *               —— 这是个重要短路，未登录浏览是最常见的场景
     */
    Set<Integer> likedTargetIds(int targetType, Collection<Integer> targetIds, Integer userId);

    /**
     * 重建某个目标的 Redis 点赞集合（自愈）。
     *
     * 场景：Redis 被 FLUSHALL / 卷丢失 / 迁移机器后，
     * 关系全没了但 MySQL 的 like_count 还在 —— 用户点"取消赞"取消不了。
     * 用 like_record 重放一次即可恢复。
     */
    void rebuildCache(int targetType, Integer targetId);

    /** 把点赞数按 like_record 重算，覆盖冗余字段（自愈） */
    int repairLikeCount(int targetType, Integer targetId);
}
