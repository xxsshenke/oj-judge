package com.yzj.service;

import com.yzj.pojo.PageResult;
import com.yzj.pojo.SolutionDetailVO;
import com.yzj.pojo.SolutionSaveRequest;
import com.yzj.pojo.SolutionVO;

/**
 * 题解业务。
 */
public interface SolutionService {

    /**
     * 按题目分页查题解。
     *
     * @param sort       "new"（最新）或 "hot"（最热）；非法值按 new 处理
     * @param currentUserId 当前登录用户，未登录为 null —— 用来算 liked / mine
     */
    PageResult<SolutionVO> pageByProblem(Integer problemId, String sort,
                                         int pageNum, int pageSize,
                                         Integer currentUserId);

    /** 题解详情（含完整正文） */
    SolutionDetailVO detail(Integer id, Integer currentUserId);

    /** 发表题解（一人一题限一篇） */
    SolutionDetailVO create(Integer problemId, SolutionSaveRequest req, Integer userId);

    /** 修改题解（仅作者） */
    SolutionDetailVO update(Integer id, SolutionSaveRequest req, Integer userId);

    /** 删除题解（仅作者，软删，其下评论一并软删） */
    void delete(Integer id, Integer userId);
}
