package com.yzj.config;

import java.util.Set;

/**
 * 一条"免登录"规则。
 *
 * @param pattern  Ant 风格路径，例如 /problems/** 或 /submissions
 * @param methods  允许的方法；null 表示任意方法
 *
 * 之所以要带方法：GET /submissions 是「查看提交记录」，匿名可看；
 * POST /submissions 是「提交代码」，必须登录。
 * 只用 excludePathPatterns 无法区分方法，所以这里自己定义规则。
 */
public record PublicPath(String pattern, Set<String> methods) {

    public static PublicPath any(String pattern) {
        return new PublicPath(pattern, null);
    }

    public static PublicPath get(String pattern) {
        return new PublicPath(pattern, Set.of("GET"));
    }

    public boolean allows(String method) {
        return methods == null || methods.contains(method.toUpperCase());
    }
}
