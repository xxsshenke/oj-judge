package com.yzj.service.impl;

import com.yzj.exception.BusinessException;
import com.yzj.mapper.LikeRecordMapper;
import com.yzj.mapper.SolutionCommentMapper;
import com.yzj.mapper.SolutionMapper;
import com.yzj.pojo.LikeRecord;
import com.yzj.pojo.LikeResult;
import com.yzj.pojo.Solution;
import com.yzj.pojo.SolutionComment;
import com.yzj.service.LikeService;
import com.yzj.utils.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 点赞实现：Redis Set 做去重闸门，MySQL like_record 做真相源。
 *
 * <h3>为什么用 SADD 而不是"先查再写"</h3>
 * SADD 天然幂等 + 原子：同一个人狂点 10 次，只有第一次返回 1。
 * 不需要锁、不需要事务、也不需要"查到没赞过然后再插"那种有并发窗口的逻辑。
 *
 * <h3>为什么 Redis 不能是唯一真相源</h3>
 * 一旦 Redis 被 FLUSHALL、卷丢失或迁移机器，点赞关系全没，
 * 而 MySQL 的 like_count 还在 —— 用户看到 87 个赞，实际一条记录都没有，
 * 连"取消赞"都取消不了（因为本来就没记录可删）。
 *
 * <h3>两个存储都要写，那还有原子性吗</h3>
 * 没有。只要 Redis 和 MySQL 都参与，就不存在真正的原子性 ——
 * 套 @Transactional 也只能保证 MySQL 内部一致，治不了 Redis。
 * 所以这里的策略是【接受不一致 + 提供自愈】（rebuildCache / repairLikeCount）。
 */
@Slf4j
@Service
public class LikeServiceImpl implements LikeService {

    private final StringRedisTemplate redis;
    private final LikeRecordMapper likeRecordMapper;
    private final SolutionMapper solutionMapper;
    private final SolutionCommentMapper commentMapper;

    public LikeServiceImpl(StringRedisTemplate redis,
                           LikeRecordMapper likeRecordMapper,
                           SolutionMapper solutionMapper,
                           SolutionCommentMapper commentMapper) {
        this.redis = redis;
        this.likeRecordMapper = likeRecordMapper;
        this.solutionMapper = solutionMapper;
        this.commentMapper = commentMapper;
    }

    @Override
    public LikeResult like(int targetType, Integer targetId, Integer userId) {
        requireTarget(targetType, targetId);

        String key = RedisConstant.likeKey(targetType, targetId);

        // ① 先去重闸门。
        //    顺序不能反 —— 必须先 Redis 后 MySQL，理由见下面 unlike 的注释。
        Long added = redis.opsForSet().add(key, String.valueOf(userId));

        if (added == null || added == 0) {
            // 已经赞过：幂等返回，不动任何计数。
            // 这里读的是当前冗余值，可能与真实记录数有偏差（历史不一致），
            // 但那是自愈接口的事，不该让每次点赞都去 count(*)
            return new LikeResult(true, currentLikeCount(targetType, targetId));
        }

        // ② 首次点赞
        likeRecordMapper.insertIgnore(targetType, targetId, userId);
        incrementCount(targetType, targetId);

        return new LikeResult(true, currentLikeCount(targetType, targetId));
    }

    @Override
    public LikeResult unlike(int targetType, Integer targetId, Integer userId) {
        requireTarget(targetType, targetId);

        String key = RedisConstant.likeKey(targetType, targetId);

        Long removed = redis.opsForSet().remove(key, String.valueOf(userId));

        if (removed == null || removed == 0) {
            // 本来就没赞过：幂等返回。
            // ⚠️ 这里**不能**减计数 —— 否则用户连点两次"取消"就会把别人的赞减掉
            return new LikeResult(false, currentLikeCount(targetType, targetId));
        }

        likeRecordMapper.delete(targetType, targetId, userId);
        decrementCount(targetType, targetId);

        return new LikeResult(false, currentLikeCount(targetType, targetId));
    }

    @Override
    public Set<Integer> likedTargetIds(int targetType, Collection<Integer> targetIds, Integer userId) {
        // ⭐ 重要短路：未登录浏览是最常见场景，不必查 Redis
        if (userId == null || targetIds == null || targetIds.isEmpty()) {
            return Collections.emptySet();
        }

        // 这里用 MySQL 的 IN 查询而不是 Redis 的 pipeline 或 SMISMEMBER：
        //
        //   SMISMEMBER key m1 m2 ... 是"一个 key 里查多个成员"，
        //   而我们需要的是"多个 key（每个目标一个 Set）里查同一个成员" ——
        //   方向正好反了，用不了。
        //
        //   Redis pipeline 能一次往返发 N 个 SISMEMBER，也行；
        //   但 like_record 本来就有 idx_like_user 索引，一次 IN 查询同样是一次往返，
        //   而且 MySQL 是真相源，结果更可信（Redis 丢了这里还是对的）。
        //
        // 结论：直接查库，代码更短，也不会出现"Redis 少了记录导致按钮状态错"。
        List<Integer> liked = likeRecordMapper.findLikedTargetIds(targetType, List.copyOf(targetIds), userId);
        return Set.copyOf(liked);
    }

    @Override
    public void rebuildCache(int targetType, Integer targetId) {
        String key = RedisConstant.likeKey(targetType, targetId);

        List<LikeRecord> records = likeRecordMapper.findByTarget(targetType, targetId);

        redis.delete(key);
        if (records.isEmpty()) {
            return;
        }
        String[] members = records.stream()
                .map(r -> String.valueOf(r.getUserId()))
                .toArray(String[]::new);
        redis.opsForSet().add(key, members);
        log.info("重建点赞缓存 targetType={} targetId={} 恢复 {} 条", targetType, targetId, members.length);
    }

    @Override
    public int repairLikeCount(int targetType, Integer targetId) {
        int real = likeRecordMapper.countByTarget(targetType, targetId);

        if (targetType == LikeRecord.TYPE_SOLUTION) {
            solutionMapper.syncLikeCount(targetId, real);
        } else {
            commentMapper.syncLikeCount(targetId, real);
        }
        return real;
    }

    /* ==================== 内部方法 ==================== */

    /**
     * 校验目标存在且未删除。
     *
     * 不校验的话，可以给一个已删除的评论点赞 —— 数据会变成孤儿，
     * 而且用户看不出任何异常。
     */
    private void requireTarget(int targetType, Integer targetId) {
        if (targetId == null) {
            throw new BusinessException("目标 ID 不能为空");
        }
        if (targetType == LikeRecord.TYPE_SOLUTION) {
            Solution solution = solutionMapper.findById(targetId);
            if (solution == null) {
                throw new BusinessException("题解不存在：" + targetId);
            }
        } else if (targetType == LikeRecord.TYPE_COMMENT) {
            SolutionComment comment = commentMapper.findById(targetId);
            if (comment == null) {
                throw new BusinessException("评论不存在：" + targetId);
            }
        } else {
            throw new BusinessException("不支持的点赞类型");
        }
    }

    private int currentLikeCount(int targetType, Integer targetId) {
        if (targetType == LikeRecord.TYPE_SOLUTION) {
            Solution s = solutionMapper.findById(targetId);
            return s == null ? 0 : s.getLikeCount();
        }
        SolutionComment c = commentMapper.findById(targetId);
        return c == null ? 0 : c.getLikeCount();
    }

    private void incrementCount(int targetType, Integer targetId) {
        if (targetType == LikeRecord.TYPE_SOLUTION) {
            solutionMapper.incrementLikeCount(targetId);
        } else {
            commentMapper.incrementLikeCount(targetId);
        }
    }

    /**
     * 递减计数。
     *
     * ⚠️ SQL 里用 GREATEST(x - 1, 0)：like_count 是 int NOT NULL，
     * 减到 -1 会静默存进去（非严格模式）或直接报错（严格模式），两种都是 bug。
     */
    private void decrementCount(int targetType, Integer targetId) {
        if (targetType == LikeRecord.TYPE_SOLUTION) {
            solutionMapper.decrementLikeCount(targetId);
        } else {
            commentMapper.decrementLikeCount(targetId);
        }
    }
}
