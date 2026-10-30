package com.yzj.service;

import com.yzj.pojo.ProblemSetItem;
import com.yzj.pojo.ProblemSetOverview;

import java.util.List;

/**
 * 题库页的业务口径。
 *
 * 关键约定：列表里的 solved / attempted / submitCount / acceptedCount 都由
 * ProblemSetMapper.findProblems 一次性聚合出来，概览接口复用同一份结果，
 * 因此“概览里的已通过数”和“列表里打勾的题数”天然一致，不会各算一套。
 */
public interface ProblemSetService {

    /**
     * 题库页概览（标签、难度分布、完成情况、每日一题）
     */
    ProblemSetOverview overview();

    /**
     * 题库列表，支持关键字、难度、标签筛选。
     *
     * 返回值是筛选后的全量结果（不分页），顺序固定按题号升序。
     */
    List<ProblemSetItem> problems(String keyword, Integer difficulty, String tag);
}
