package com.yzj.pojo;

/**
 * 登录成功后返回给前端的凭据。
 *
 * 不放整个 User 对象 —— 前端需要时再调 /users/current 取，
 * 保持"登录只负责发 token"这一件事。
 */
public record LoginResponse(String token, String username, String nickname, String role) {
}
