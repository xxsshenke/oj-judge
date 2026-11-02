package com.yzj.service;

import com.yzj.pojo.LoginRequest;
import com.yzj.pojo.LoginResponse;
import com.yzj.pojo.User;

public interface UserService {

    /** 当前登录用户（userId 由拦截器写入 UserHolder） */
    User current();

    /** 按用户 ID 查询用户信息 */
    User detail(Integer id);

    /** 登录：校验用户名密码，成功则生成 token 存入 Redis */
    LoginResponse login(LoginRequest req);

    /** 退出：删除 Redis 里的 token */
    void logout(String token);
}
