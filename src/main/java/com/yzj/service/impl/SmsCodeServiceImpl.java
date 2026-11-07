package com.yzj.service.impl;

import cn.hutool.core.util.StrUtil;
import com.yzj.exception.BusinessException;
import com.yzj.pojo.SmsCodeResponse;
import com.yzj.service.SmsCodeService;
import com.yzj.utils.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * 验证码实现。不接短信通道，码直接用响应带回去给前端弹窗显示。
 *
 * 保留的两个"不简化"的点（简化了会出 bug，不是洁癖）：
 *   1. 校验通过立刻 DEL —— 否则同一条码能反复用
 *   2. 码错 / 过期 / 已用过 统一提示 —— 不区分，避免被用来探测手机号是否发过码
 */
@Slf4j
@Service
public class SmsCodeServiceImpl implements SmsCodeService {

    /**
     * 6 位纯数字。
     * 用 SecureRandom 而不是 Math.random()：后者可预测，不该拿来生成凭据。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private final StringRedisTemplate redis;

    public SmsCodeServiceImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public SmsCodeResponse send(String phone) {
        String normalizedPhone = normalizePhone(phone);

        String code = randomCode();
        String key = RedisConstant.SMS_CODE_KEY + normalizedPhone;

        redis.opsForValue().set(key, code,
                Duration.ofSeconds(RedisConstant.SMS_CODE_TTL_SECONDS));

        // 演示模式下码要回给前端，所以这里必须打日志备查
        log.info("生成验证码: phone={} code={}", normalizedPhone, code);

        return new SmsCodeResponse(code, RedisConstant.SMS_CODE_TTL_SECONDS);
    }

    @Override
    public void verify(String phone, String code) {
        if (StrUtil.isBlank(code) || !code.matches("\\d{6}")) {
            throw new BusinessException("请输入 6 位验证码");
        }

        String normalizedPhone = normalizePhone(phone);
        String key = RedisConstant.SMS_CODE_KEY + normalizedPhone;

        String cached = redis.opsForValue().get(key);

        // 码错 / 过期 / 没发过 / 已经用过 —— 全部同一句话。
        // 分情况给提示等于告诉调用方"这个号有没有请求过验证码"。
        if (cached == null || !cached.equals(code)) {
            throw new BusinessException("验证码错误或已过期");
        }

        // ⚠️ 必须在返回前删：先删再让调用方继续。
        // 反过来（先建号后删码）一旦 Redis 抖动，
        // 会出现"账号已建好但码还在"，同一张码能被再用一次。
        redis.delete(key);
    }

    /**
     * 6 位码必须补零。
     *
     * nextInt(1_000_000) 可能返回 12345，
     * 直接 String.valueOf 会变成 5 位，前端"必须 6 位"的校验会把它判成非法输入，
     * 用户就永远拿不到能用的码（实测过：不补零会产生 1~5 位的结果）。
     */
    private String randomCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String normalizePhone(String phone) {
        String p = phone == null ? "" : phone.trim();
        if (!PHONE_PATTERN.matcher(p).matches()) {
            throw new BusinessException("手机号格式不正确");
        }
        return p;
    }
}
