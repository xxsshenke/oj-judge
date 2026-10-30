package com.yzj.pojo;

import lombok.Data;

import java.util.List;

/**
 * 题库页概览：难度分布、完成情况、标签统计、每日一题。
 *
 * 统计口径：
 *   - total / easyCount / mediumCount / hardCount 是题目数量；
 *   - solvedCount / easySolved / mediumSolved / hardSolved 是“至少 AC 过一次”的题目数量；
 *   - tags 是标签覆盖的题目数，按题数降序；
 *   - daily 是当天轮换到的题目，可能为 null（题库为空时）。
 * 所有数字都来自同一份全量查询结果，保证彼此一致。
 */
@Data
public class ProblemSetOverview {

    private Integer total;
    private Integer easyCount;
    private Integer mediumCount;
    private Integer hardCount;
    private Integer solvedCount;
    private Integer easySolved;
    private Integer mediumSolved;
    private Integer hardSolved;
    private List<TagCount> tags;
    private ProblemSetItem daily;
}
