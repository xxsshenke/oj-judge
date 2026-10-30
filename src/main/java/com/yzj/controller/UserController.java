package com.yzj.controller;

import com.yzj.pojo.Result;
import com.yzj.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 当前用户（登录功能上线前固定为 admin）
     */
    @GetMapping("/current")
    public Result current() {
        return Result.success(userService.current());
    }

    @GetMapping("/{id}")
    public Result detail(@PathVariable Integer id) {
        return Result.success(userService.detail(id));
    }
}
