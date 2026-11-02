package com.yzj.utils;


public class  RedisConstant {

    public static final String CACHE_OJ_KEY = "oj:problem:";

    public static final String CACHE_LOCK_KEY = "oj:lock:problem:";    //  加 lock:

    /**
     * 提交中标记：同一来源同时只允许一个提交在判题。
     * 目前按 IP（还没有登录），接入用户系统后这里换成 userId 即可。
     */
    public static final String SUBMIT_LOCK_KEY = "oj:judging:";

    /** 标记过期时间（秒）—— 判题最坏约 10 秒，靠 TTL 自动兜底释放 */
    public static final long SUBMIT_LOCK_TTL_SECONDS = 60;
}
