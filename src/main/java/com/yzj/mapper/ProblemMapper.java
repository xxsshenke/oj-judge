package com.yzj.mapper;

import com.yzj.pojo.Problem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProblemMapper {

    List<Problem> findAll();

    Problem findById(@Param("id") Integer id);

    Problem findDetailById(@Param("id") Integer id);
}
