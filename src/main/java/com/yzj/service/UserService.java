package com.yzj.service;

import com.yzj.pojo.User;

public interface UserService {

    /**
     * 当前登录用户。登录功能上线前默认返回第一个用户（admin）。
     */
    User current();

    /**
     * 按用户 ID 查询用户信息。
     */
    User detail(Integer id);
}
