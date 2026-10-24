package com.yzj.mapper;

import com.yzj.pojo.TestCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TestCaseMapper {
    List<TestCase> findByProblemId(@Param("problemId") Integer problemId);
}