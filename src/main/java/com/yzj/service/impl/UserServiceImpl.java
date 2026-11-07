package com.yzj.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.yzj.exception.BusinessException;
import com.yzj.mapper.UserMapper;
import com.yzj.pojo.LoginRequest;
import com.yzj.pojo.LoginResponse;
import com.yzj.pojo.SmsLoginRequest;
import com.yzj.pojo.User;
import com.yzj.service.SmsCodeService;
import com.yzj.service.UserService;
import com.yzj.utils.RedisConstant;
import com.yzj.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private static final int NICKNAME_MAX_LENGTH = 50;

    private final UserMapper userMapper;
    private final StringRedisTemplate redis;
    private final SmsCodeService smsCodeService;

    public UserServiceImpl(UserMapper userMapper,
                           StringRedisTemplate redis,
                           SmsCodeService smsCodeService) {
        this.userMapper = userMapper;
        this.redis = redis;
        this.smsCodeService = smsCodeService;
    }

    @Override
    public User current() {
        Integer userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        return detail(userId);
    }

    @Override
    public User detail(Integer id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException("用户不存在：" + id);
        }
        return user;
    }

    @Override
    public LoginResponse login(LoginRequest req) {
        if (req == null || StrUtil.isBlank(req.username()) || StrUtil.isBlank(req.password())) {
            throw new BusinessException("用户名和密码不能为空");
        }

        User user = userMapper.findByUsername(req.username().trim());

        // 用户不存在和密码错误返回同一句提示，避免被用来枚举有效用户名。
        //
        //  手机号注册的账号 password 落库是空串，这里会自然落到 "密码错误" 分支。
        //    刻意不单独判 isBlank(password) 给"请用手机号登录"的提示 ——
        //    那等于告诉调用方"这个用户名存在，只是没设密码"，是用户枚举漏洞。
        //    实测 BCrypt.checkpw(任意串, "") 返回 false，安全性由这一行保证。
        if (user == null || !BCrypt.checkpw(req.password(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }

        return issueToken(user);
    }

    @Override
    public LoginResponse loginBySms(SmsLoginRequest req) {
        if (req == null || StrUtil.isBlank(req.phone())) {
            throw new BusinessException("手机号格式不正确");
        }
        String phone = req.phone().trim();
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException("手机号格式不正确");
        }

        String nickname = StrUtil.trimToNull(req.nickname());
        if (nickname != null && nickname.length() > NICKNAME_MAX_LENGTH) {
            throw new BusinessException("昵称不能超过 " + NICKNAME_MAX_LENGTH + " 个字符");
        }

        User user = userMapper.findByPhone(phone);

        //  状态检查放在 verify() 之前：被禁用的号不该白白吃掉一张验证码，
        //    用户重发一次还是这个结果，只会更困惑。
        if (user != null && user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }

        // 校验通过后验证码立即作废（内部已 DEL）
        smsCodeService.verify(phone, req.code());

        if (user == null) {
            user = registerByPhone(phone, nickname);
        }

        return issueToken(user);
    }

    /**
     * 手机号首次登录时自动建号。
     *
     * username 也用手机号：username 是 NOT NULL + UNIQUE，
     * 填手机号既满足约束，又让 findByUsername 直接可用，表结构零改动。
     * 代价是同一个值存两列，约 11 字节/行。
     */
    private User registerByPhone(String phone, String nickname) {
        User user = new User();
        user.setUsername(phone);
        user.setPhone(phone);
        //    空串不是 null：password 列是 NOT NULL，
        //    传 null 会报 ERROR 1048（已实测）。null 才是最容易犯的错。
        user.setPassword("");
        user.setNickname(nickname != null ? nickname : defaultNickname(phone));
        user.setRole("USER");
        user.setStatus(1);

        try {
            userMapper.insert(user);
            log.info("手机号首次登录，自动建号: phone={} id={}", phone, user.getId());
            return user;
        } catch (DuplicateKeyException e) {
            // 并发下两个请求都判定"不存在"，其中一个会撞唯一索引。
            // 这不该报错给用户 —— 对方先建好了，直接按已注册处理即可。
            // 具体撞的是 uk_user_username 还是 uk_user_phone 不重要，统一重查。
            log.info("并发注册撞唯一索引，回查已有账号: phone={}", phone);
            User existing = userMapper.findByPhone(phone);
            if (existing == null) {
                throw new BusinessException("该手机号已注册，请直接登录");
            }
            if (existing.getStatus() != null && existing.getStatus() == 0) {
                throw new BusinessException("账号已被禁用");
            }
            return existing;
        }
    }

    /** 昵称留空时的默认值，例如 13800138000 -> 用户8000 */
    private String defaultNickname(String phone) {
        return "用户" + phone.substring(phone.length() - 4);
    }

    /**
     * 生成 token 存 Redis 并组装响应。
     *
     * 抽出来是因为三个入口（密码登录 / 验证码登录 / 自动注册）都要发 token，
     * 内联写三份的话，以后改 TTL 或改响应结构要改三处。
     */
    private LoginResponse issueToken(User user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = RedisConstant.LOGIN_TOKEN_KEY + token;
        redis.opsForValue().set(key, String.valueOf(user.getId()),
                Duration.ofMinutes(RedisConstant.LOGIN_TOKEN_TTL_MINUTES));

        log.info("用户登录成功: {} (id={})", user.getUsername(), user.getId());
        return new LoginResponse(token, user.getUsername(), user.getNickname(), user.getRole());
    }

    @Override
    public void logout(String token) {
        if (StrUtil.isBlank(token)) {
            return;
        }
        redis.delete(RedisConstant.LOGIN_TOKEN_KEY + token);
    }
}
