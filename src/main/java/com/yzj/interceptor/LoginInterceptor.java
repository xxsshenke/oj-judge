package com.yzj.interceptor;

import com.yzj.config.WebConfig;
import com.yzj.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 第二个拦截器：登录校验。
 *
 * 只做一件事：如果这条请求需要登录、而 UserHolder 里又没有 userId，就返回 401。
 * "哪些路径需要登录"由 WebConfig.PUBLIC_PATHS 统一维护，这里不写死 URI。
 *
 * 前提：RefreshTokenInterceptor（order 0）已经跑过并把 userId 放进 ThreadLocal。
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // 公开路径直接放行（题库、题面、查看提交记录等）
        if (WebConfig.isPublic(path, method)) {
            return true;
        }

        // 需要登录的路径
        if (UserHolder.get() != null) {
            return true;
        }

        // 返回 401 而不是 200 + code=0：
        // 前端 request() 靠 HTTP 状态码决定弹登录框，
        // 用 200 会被当成"业务失败"，用户看不懂
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":0,\"msg\":\"未登录或登录已过期\",\"data\":null}");
        return false;
    }
}
