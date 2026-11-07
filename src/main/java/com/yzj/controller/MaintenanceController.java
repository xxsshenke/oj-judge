package com.yzj.controller;

import com.yzj.exception.BusinessException;
import com.yzj.mapper.UserMapper;
import com.yzj.pojo.LikeRecord;
import com.yzj.pojo.Result;
import com.yzj.pojo.User;
import com.yzj.service.LikeService;
import com.yzj.utils.UserHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据自愈接口（仅管理员）。
 *
 * <h3>为什么需要这个</h3>
 * 点赞同时写 Redis（去重闸门）和 MySQL（真相源），这两者之间不存在原子性。
 * 可能出现的偏差：
 * <ul>
 *   <li>SADD 成功但 UPDATE 失败 → 用户显示"已赞"，计数却没涨（永久错位）</li>
 *   <li>Redis 被 FLUSHALL / 卷丢失 / 迁移机器 → 关系全没，但 like_count 还在。
 *       用户看到 87 赞，一条记录都没有，点"取消赞"也取消不了</li>
 * </ul>
 *
 * 这两个接口就是把偏差修回来的手段 —— 有自愈能力，
 * 才敢把"Redis + MySQL 双写、不加事务"这个设计立住。
 */
@RestController
public class MaintenanceController {

    private final LikeService likeService;
    private final UserMapper userMapper;

    public MaintenanceController(LikeService likeService, UserMapper userMapper) {
        this.likeService = likeService;
        this.userMapper = userMapper;
    }

    /**
     * 按 like_record 重算某个目标的点赞数，并重建 Redis 集合。
     *
     * 一次调用同时修复两件事：
     *   ① like_count 冗余字段 → 覆盖成 like_record 的真实条数
     *   ② Redis Set → 用 like_record 重放
     *
     * @param type 1 题解 / 2 评论
     */
    @PostMapping("/admin/like/repair/{type}/{id}")
    public Result repair(@PathVariable Integer type,
                         @PathVariable Integer id,
                         @RequestParam(defaultValue = "true") boolean rebuildCache) {
        requireAdmin();

        if (type != LikeRecord.TYPE_SOLUTION && type != LikeRecord.TYPE_COMMENT) {
            throw new BusinessException("不支持的类型：" + type);
        }

        int real = likeService.repairLikeCount(type, id);
        if (rebuildCache) {
            likeService.rebuildCache(type, id);
        }

        return Result.success(java.util.Map.of(
                "targetType", type,
                "targetId", id,
                "likeCount", real,
                "cacheRebuilt", rebuildCache));
    }

    /**
     * 只重建 Redis 缓存，不动 MySQL。
     *
     * 场景：Redis 丢了但 MySQL 是对的 —— 这时候不需要改计数，只需要把
     * "谁赞过"这套关系重新灌回 Redis。反过来的情况用上面那个接口。
     */
    @PostMapping("/admin/like/rebuild-cache/{type}/{id}")
    public Result rebuildCache(@PathVariable Integer type, @PathVariable Integer id) {
        requireAdmin();
        likeService.rebuildCache(type, id);
        return Result.success();
    }

    /**
     * 管理员校验。
     *
     * 这里多查了一次 user 表 —— UserHolder 里只存了 userId，没有 role。
     * 之所以接受这次查库：这是低频的运维接口，不是热路径。
     * 如果以后管理接口变多，应该在 RefreshTokenInterceptor 续期时顺手把 role
     * 一起放进 UserHolder，那样所有管理接口都不用再查。
     */
    private void requireAdmin() {
        Integer userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        User user = userMapper.findById(userId);
        if (user == null || !"ADMIN".equals(user.getRole())) {
            throw new BusinessException("无权限：仅管理员可操作");
        }
    }
}
