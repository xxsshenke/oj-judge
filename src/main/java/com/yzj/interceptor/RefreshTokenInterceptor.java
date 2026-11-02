package com.yzj.interceptor;

import com.yzj.utils.RedisConstant;
import com.yzj.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 第一个拦截器：解析并续期 token。
 *
 * 特点：**不管有没有登录都放行**。
 *   - 带了有效 token  → 把 userId 放进 UserHolder，并续期 30 分钟
 *   - 没带 / token 无效 → 什么都不做，直接放行
 *
 * 为什么要单独一个拦截器：
 *   登录校验（LoginInterceptor）要在续期之后执行。
 *   如果只有一个拦截器，token 恰好在这次请求过期，
 *   就会被直接判定未登录，用户明明还活跃却被踢出去。
 *
 * 顺序上必须排在 LoginInterceptor 之前（order 更小）。
 */
@Slf4j
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redis;

    public RefreshTokenInterceptor(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = resolveToken(request);
        if (token == null) {
            return true;                       // 没登录，放行给下一个拦截器判断
        }

        String key = RedisConstant.LOGIN_TOKEN_KEY + token;
        String userId = redis.opsForValue().get(key);
        if (userId == null) {
            return true;                       // token 过期或伪造，同样放行
        }

        // 写入当前用户，Controller 里可以直接用
        UserHolder.set(Integer.valueOf(userId));

        // 续期：每次访问都重置 TTL，实现"30 分钟不活跃才失效"
        redis.expire(key, Duration.ofMinutes(RedisConstant.LOGIN_TOKEN_TTL_MINUTES));

        return true;
    }

    /** 请求结束后必须清理 ThreadLocal，否则线程复用会串用户 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserHolder.remove();
    }

    /** 支持 "Bearer xxx" 和裸 token 两种写法 */
    public static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(RedisConstant.TOKEN_HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }
        if (header.startsWith(RedisConstant.TOKEN_PREFIX)) {
            header = header.substring(RedisConstant.TOKEN_PREFIX.length());
        }
        String token = header.trim();
        return token.isEmpty() ? null : token;
    }
}
