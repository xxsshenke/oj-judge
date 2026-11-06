package com.yzj.service;

import com.yzj.pojo.PageResult;
import com.yzj.pojo.Submission;
import com.yzj.pojo.SubmitRequest;

public interface SubmissionService {
    Submission submit(SubmitRequest req);

    Submission detail(Integer id);

    /**
     * 按题目分页查询提交记录。
     *
     * @param problemId 题目 ID
     * @param pageNum   页码，从 1 开始；小于 1 会被修正为 1
     * @param pageSize  每页条数；超出上限会被裁剪（见实现类的 MAX_PAGE_SIZE）
     */
    PageResult<Submission> pageByProblem(Integer problemId, int pageNum, int pageSize);
}
