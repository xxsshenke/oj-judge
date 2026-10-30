package com.yzj.mapper;

import com.yzj.pojo.ProblemSetItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProblemSetMapper {

    /**
     * 题库列表：按关键字 / 难度 / 标签筛选，同时把每题的提交统计聚合出来。
     *
     * 返回的是全量结果（没有 limit），调用方需要分页时自行切片。
     */
    List<ProblemSetItem> findProblems(@Param("keyword") String keyword,
                                      @Param("difficulty") Integer difficulty,
                                      @Param("tag") String tag);

    /**
     * 所有已配置标签（英文逗号分隔的原串）。
     *
     * 说明：当前标签计数由 ProblemSetServiceImpl 从 findProblems 的结果里拆分统计，
     * 本方法暂未被调用，保留作为“需要单独查标签字典”时的入口。
     */
    List<String> findAllTags();
}
