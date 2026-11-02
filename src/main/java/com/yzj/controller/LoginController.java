package com.yzj.controller;

import com.yzj.interceptor.RefreshTokenInterceptor;
import com.yzj.pojo.LoginRequest;
import com.yzj.pojo.LoginResponse;
import com.yzj.pojo.Result;
import com.yzj.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录 / 退出。
 *
 * 这两个接口在 WebConfig 里是免登录的 —— 没登录当然也能调登录接口。
 */
@RestController
public class LoginController {

    private final UserService userService;

    public LoginController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest req) {
        LoginResponse resp = userService.login(req);
        return Result.success(resp);
    }

    @PostMapping("/logout")
    public Result logout(HttpServletRequest request) {
        userService.logout(RefreshTokenInterceptor.resolveToken(request));
        return Result.success();
    }
}
