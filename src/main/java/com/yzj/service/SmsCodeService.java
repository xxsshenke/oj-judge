package com.yzj.service;

import com.yzj.pojo.SmsCodeResponse;

/**
 * 验证码的生成与校验。
 *
 * 当前是后端内部实现：不接真实短信通道，
 * 生成的码直接随响应返回给前端弹出显示。
 * 接真实通道时只需改 send() 内部，
 * verify() 那一套（存 Redis、一次性消费）不用动。
 */
public interface SmsCodeService {

    /** 生成验证码并存入 Redis，返回给调用方 */
    SmsCodeResponse send(String phone);

    /**
     * 校验验证码。校验通过后立即删除，保证一次性使用。
     *
     * @throws com.yzj.exception.BusinessException 码错 / 过期 / 没发过
     */
    void verify(String phone, String code);
}
