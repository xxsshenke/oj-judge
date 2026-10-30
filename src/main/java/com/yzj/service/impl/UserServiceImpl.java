package com.yzj.service.impl;

import com.yzj.exception.BusinessException;
import com.yzj.mapper.UserMapper;
import com.yzj.pojo.User;
import com.yzj.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    /**
     * 登录功能上线前的默认当前用户（第一个用户 admin）
     */
    private static final Integer DEFAULT_USER_ID = 1;

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User current() {
        return detail(DEFAULT_USER_ID);
    }

    @Override
    public User detail(Integer id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException("用户不存在：" + id);
        }
        return user;
    }
}
