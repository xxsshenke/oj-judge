package com.yzj.mapper;

import com.yzj.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findById(@Param("id") Integer id);
}
