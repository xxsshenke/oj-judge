package com.yzj.config;

import com.yzj.interceptor.LoginInterceptor;
import com.yzj.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 拦截器注册 + 免登录路径规则。
 *
 *   order 0  RefreshTokenInterceptor  拦截 /**，只做"续期 + 塞 UserHolder"，永远放行
 *   order 1  LoginInterceptor         拦截 /**，只是自己不判断路径 ——
 *           "这条路径要不要登录"由本类的 PUBLIC_PATHS 决定
 *
 * 顺序不能反：先续期再校验。否则 token 恰好在这一刻过期，
 * 活跃用户会被误踢出去。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /**
     * 免登录路径。
     *
     * 设计原则：**浏览开放，写入要登录**
     *   - 题库、题面、提交记录：谁都能看（利于分享和搜索）
     *   - 提交代码、个人资料、登出：必须登录
     */
    private static final List<PublicPath> PUBLIC_PATHS = List.of(
            // 登录 / 登出接口本身
            PublicPath.any("/login"),
            PublicPath.any("/logout"),

            // 手机号注册 / 验证码登录 —— 调用时用户还没有身份。
            //    必须写精确路径，不能图省事写成 /login/*：
            //    Ant 的 * 会匹配一切一级子路径，以后再加 /login/captcha、
            //    /login/refresh 就会静默变成免登录接口。白名单最不该用通配。
            PublicPath.any("/auth/sms/code"),
            PublicPath.any("/login/sms"),

            // 题目相关：只读
            PublicPath.get("/problems"),
            PublicPath.get("/problems/*"),
            PublicPath.get("/problem-sets/**"),

            // 题解：只读。
            //   /problems/* 匹配不到 /problems/1/solutions —— Ant 的 * 不跨 /，
            //    必须单独写这条三段规则。也不能图省事写成 /problems/**，
            //    那会把 POST /problems/1/solutions 也放行，登录校验就废了。
            PublicPath.get("/problems/*/solutions"),
            PublicPath.get("/solutions/*"),
            PublicPath.get("/solutions/*/comments"),

            // 提交记录：GET 是"看记录"，POST 是"交代码"（POST 没在这里，所以要登录）
            PublicPath.get("/submissions"),
            PublicPath.get("/submissions/*"),

            // 静态资源与错误页
            PublicPath.any("/"),
            PublicPath.any("/index.html"),
            PublicPath.any("/favicon.ico"),
            PublicPath.any("/assets/**"),
            PublicPath.any("/error")
    );

    private final RefreshTokenInterceptor refreshTokenInterceptor;
    private final LoginInterceptor loginInterceptor;

    public WebConfig(RefreshTokenInterceptor refreshTokenInterceptor,
                     LoginInterceptor loginInterceptor) {
        this.refreshTokenInterceptor = refreshTokenInterceptor;
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(refreshTokenInterceptor)
                .addPathPatterns("/**")
                .order(0);

        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .order(1);
    }

    /**
     * 判断某个请求是否免登录。
     *
     * LoginInterceptor 直接调用这个方法 —— 把"哪些路径开放"集中在一处，
     * 而不是散落在拦截器里判断 URI。
     */
    public static boolean isPublic(String path, String method) {
        for (PublicPath rule : PUBLIC_PATHS) {
            if (MATCHER.match(rule.pattern(), path) && rule.allows(method)) {
                return true;
            }
        }
        return false;
    }
}
