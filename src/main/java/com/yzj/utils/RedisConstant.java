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
}
