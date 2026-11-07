package com.yzj.pojo;

/**
 * 手机号 + 验证码登录 / 注册请求。
 *
 * 注册和登录合一：手机号没注册就自动建号，已注册就登录。
 * 所以没有单独的 /register 接口 —— 前端只弹一个框。
 *
 * @param phone    手机号
 * @param code     验证码
 * @param nickname 昵称，可空。只在"首次注册"时用得上；
 *                 已注册用户的昵称不会被覆盖。
 */
public record SmsLoginRequest(String phone, String code, String nickname) {
}
