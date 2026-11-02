package com.yzj.mapper;

import com.yzj.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findById(@Param("id") Integer id);

    /**
     * 登录用：按用户名查，需要带出 password 字段做校验。
     * User.password 上的 @JsonIgnore 只影响返回给前端，
     * 不影响 MyBatis 把列映射进对象。
     */
    User findByUsername(@Param("username") String username);
}
