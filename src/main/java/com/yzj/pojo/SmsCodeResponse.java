package com.yzj.pojo;

/**
 * 验证码响应。
 *
 * @param smsCode       验证码明文
 * @param expireSeconds 有效期（秒）
 *
 * ⚠️ smsCode 只在"验证码由后端内部实现"的模式下返回 ——
 *    不接真实短信通道，所以把码直接给前端弹出来。
 *    一旦接真实通道，这个字段必须删掉，否则等于把验证码广播出去。
 */
public record SmsCodeResponse(String smsCode, long expireSeconds) {
}
