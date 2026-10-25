package com.yzj.mapper;

import com.yzj.pojo.Submission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SubmissionMapper {

    void insert(Submission submission);

    Submission findById(@Param("id") Integer id);

    List<Submission> findByProblemId(@Param("problemId") Integer problemId);

    // ⭐ 新增：只更新判定结果，不动 code（code 太大）
    void updateResult(Submission submission);

    int updateStatus(@Param("id") Integer id, @Param("status") String status);

    // ⭐ 新增：服务启动时恢复未完成的提交
    List<Submission> findByStatus(@Param("status") String status);
}