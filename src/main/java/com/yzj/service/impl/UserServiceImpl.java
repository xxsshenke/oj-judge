package com.yzj.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.yzj.exception.BusinessException;
import com.yzj.mapper.UserMapper;
import com.yzj.pojo.LoginRequest;
import com.yzj.pojo.LoginResponse;
import com.yzj.pojo.User;
import com.yzj.service.UserService;
import com.yzj.utils.RedisConstant;
import com.yzj.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final StringRedisTemplate redis;

    public UserServiceImpl(UserMapper userMapper, StringRedisTemplate redis) {
        this.userMapper = userMapper;
        this.redis = redis;
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

        // 用户不存在和密码错误返回同一句提示，避免被用来枚举有效用户名
        if (user == null || !BCrypt.checkpw(req.password(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }

        // 登录成功：生成 token 存 Redis，value 为 userId，TTL 30 分钟
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
