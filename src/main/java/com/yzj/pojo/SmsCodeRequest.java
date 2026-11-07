package com.yzj.pojo;

/**
 * 请求验证码。
 *
 * 只有手机号一个参数：注册和登录已合并成同一个流程，
 * 所以不存在"注册码"和"登录码"两种场景，不需要 scene。
 *
 * @param phone 手机号，11 位
 */
public record SmsCodeRequest(String phone) {
}
