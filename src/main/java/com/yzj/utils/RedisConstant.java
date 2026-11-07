package com.yzj.utils;

/**
 * Redis key 与登录相关常量。
 */
public class RedisConstant {

    public static final String CACHE_OJ_KEY = "oj:problem:";

    public static final String CACHE_LOCK_KEY = "oj:lock:problem:";

    /**
     * 提交中标记：同一来源同时只允许一个提交在判题。
     * 登录后按 userId 限流，未登录回退到 IP。
     */
    public static final String SUBMIT_LOCK_KEY = "oj:judging:";

    /** 标记过期时间（秒）—— 判题最坏约 10 秒，靠 TTL 自动兜底释放 */
    public static final long SUBMIT_LOCK_TTL_SECONDS = 60;

    /** 登录 token：oj:login:token:{token} -> userId */
    public static final String LOGIN_TOKEN_KEY = "oj:login:token:";

    /**
     * token 有效期（分钟）。每个请求都会刷新，
     * 所以这是"空闲超时"，不是"绝对过期"。
     */
    public static final long LOGIN_TOKEN_TTL_MINUTES = 30;

    /** 请求头里带 token 的字段名 */
    public static final String TOKEN_HEADER = "Authorization";

    /** token 前缀，前端传 "Bearer xxx" 也兼容 */
    public static final String TOKEN_PREFIX = "Bearer ";

    // ==================== 题解 / 评论 / 点赞 ====================

    /**
     * 题解点赞关系：oj:like:solution:{solutionId} -> Set&lt;userId&gt;
     *
     * ⚠️ 刻意不设 TTL。点赞关系是业务数据，不是缓存 ——
     * 设了 TTL 就变成"关系会过期"，那是 bug。
     *
     * 它只是"快闸"（SADD 天然幂等，挡住重复点赞），
     * 真相源在 MySQL 的 like_record 表，Redis 丢了还能重建。
     */
    public static final String LIKE_SOLUTION_KEY = "oj:like:solution:";

    /** 评论点赞关系：oj:like:comment:{commentId} -> Set&lt;userId&gt; */
    public static final String LIKE_COMMENT_KEY = "oj:like:comment:";

    /** 发题解限流：每分钟 3 次 */
    public static final String RATE_SOLUTION_KEY = "oj:rate:solution:user:";

    /** 发评论限流：每分钟 10 次 */
    public static final String RATE_COMMENT_KEY = "oj:rate:comment:user:";

    /** 点赞限流：每分钟 60 次 —— 正常用户点不到这个量，脚本刷不同 id 才会撞上 */
    public static final String RATE_LIKE_KEY = "oj:rate:like:user:";

    /** 按 targetType 取对应的点赞 key 前缀 */
    public static String likeKey(int targetType, Integer targetId) {
        return targetType == com.yzj.pojo.LikeRecord.TYPE_SOLUTION
                ? LIKE_SOLUTION_KEY + targetId
                : LIKE_COMMENT_KEY + targetId;
    }

    // ==================== 手机号 + 验证码 ====================

    /**
     * 验证码：oj:sms:code:{phone} -> 6 位数字字符串
     *
     * 刻意不带 scene：注册和登录已合并成一个流程，
     * 只有一种码，不存在跨场景复用的问题。
     * 如果以后真的拆出第二个流程（比如"换绑手机号"），
     * 那时必须把 scene 加进 key —— 否则两个流程的码可以互用。
     */
    public static final String SMS_CODE_KEY = "oj:sms:code:";

    /** 验证码有效期（秒） */
    public static final long SMS_CODE_TTL_SECONDS = 300;
}
