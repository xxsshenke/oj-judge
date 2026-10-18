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
}