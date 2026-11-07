package com.yzj.service;

import com.yzj.pojo.CommentCreateRequest;
import com.yzj.pojo.CommentVO;
import com.yzj.pojo.PageResult;

/**
 * 题解评论（主评论 + 回复）。
 */
public interface CommentService {

    /**
     * 分页查某题解的评论。
     *
     * 返回的是"主评论分页，每条主评论内嵌最多 N 条回复"。
     * replyCount 是真实回复总数，可能大于 replies 数组长度。
     */
    PageResult<CommentVO> pageBySolution(Integer solutionId, int pageNum, int pageSize,
                                         Integer currentUserId);

    /** 发表评论；parentId 为 null 时是主评论 */
    CommentVO create(Integer solutionId, CommentCreateRequest req, Integer userId);

    /** 删除评论（仅作者，软删） */
    void delete(Integer id, Integer userId);
}
