package com.yzj.utils;

/**
 * 保存当前请求的登录用户 ID。
 *
 * 拦截器解析 token 后写入，Controller / Service 里直接读取，
 * 避免每个方法都多传一个参数。
 *
 * ⚠️ 用 ThreadLocal 的前提是"一个请求由同一个线程处理"，
 * 并且必须在请求结束时 remove()，否则线程池复用会串号。
 */
public class UserHolder {

    private static final ThreadLocal<Integer> CURRENT_USER = new ThreadLocal<>();

    public static void set(Integer userId) {
        CURRENT_USER.set(userId);
    }

    public static Integer get() {
        return CURRENT_USER.get();
    }

    public static void remove() {
        CURRENT_USER.remove();
    }
}
