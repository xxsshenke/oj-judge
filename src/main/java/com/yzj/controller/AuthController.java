package com.yzj.controller;

import com.yzj.pojo.Result;
import com.yzj.pojo.SmsCodeRequest;
import com.yzj.pojo.SmsCodeResponse;
import com.yzj.pojo.SmsLoginRequest;
import com.yzj.service.SmsCodeService;
import com.yzj.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 手机号注册 / 验证码登录。
 *
 * 两个接口都在 WebConfig 的白名单里 —— 调用时用户还没有身份。
 */
@RestController
public class AuthController {

    private final SmsCodeService smsCodeService;
    private final UserService userService;

    public AuthController(SmsCodeService smsCodeService, UserService userService) {
        this.smsCodeService = smsCodeService;
        this.userService = userService;
    }

    /**
     * 获取验证码。
     *
     * 当前是后端内部实现：响应里直接带 smsCode，前端弹窗显示。
     * 接真实短信通道后要把响应里的 smsCode 删掉。
     */
    @PostMapping("/auth/sms/code")
    public Result smsCode(@RequestBody SmsCodeRequest req) {
        SmsCodeResponse resp = smsCodeService.send(req == null ? null : req.phone());
        return Result.success(resp);
    }

    /**
     * 手机号 + 验证码登录。手机号没注册就自动建号。
     *
     * 返回结构与 /login 一致（LoginResponse），
     * 前端拿到 token 直接 setAuth，不用关心是新用户还是老用户。
     */
    @PostMapping("/login/sms")
    public Result loginBySms(@RequestBody SmsLoginRequest req) {
        return Result.success(userService.loginBySms(req));
    }
}
