package com.yzj.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Integer id;
    private String username;      // 登录用户名
    private String nickname;      // 昵称
    @JsonIgnore
    private String password;      // 密码，登录功能后续实现，暂不返回给前端
    private String email;
    private String avatar;        // 头像地址
    private String bio;           // 个人简介
    private String school;        // 学校
    private Integer rating;       // Rating 积分，默认 1000
    private Integer solvedCount;  // 通过题目数，默认 0
    private Integer submitCount;  // 提交次数，默认 0
    private String role;          // ADMIN / USER
    private Integer status;       // 1 正常，0 禁用
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
