package com.yzj.mapper;

import com.yzj.pojo.SolutionComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SolutionCommentMapper {

    void insert(SolutionComment comment);

    SolutionComment findById(@Param("id") Integer id);

    /**
     * 分页查顶层主评论（root_id IS NULL），按 id 倒序 = 最新在前。
     */
    List<SolutionComment> findMainPage(@Param("solutionId") Integer solutionId,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    long countMain(@Param("solutionId") Integer solutionId);

    /**
     * 一次查出这批主评论的全部回复。
     *
     * 这是 root_id 冗余换来的最大好处：不管多少条回复都只有一次查询，没有 N+1。
     *
     * ⚠️ 这里**不截断**：某个主评论有 200 条回复时也会全部取出，
     * 由 Service 在内存里按 root 分组后各取前 N 条。
     * 为什么不直接在 SQL 里截断：LIMIT 是全局的，做不到"每组各取 N 条"
     * （除非上 ROW_NUMBER() 窗口函数，一期不值得）。
     * 一页只有 10 个主评论，全取也就几千行，内存完全扛得住 ——
     * 而且 replyCount 正好可以直接用这份数据的长度算出来，省掉一次 count 查询。
     */
    List<SolutionComment> findRepliesByRootIds(@Param("rootIds") List<Integer> rootIds);

    int softDelete(@Param("id") Integer id);

    int incrementLikeCount(@Param("id") Integer id);

    int decrementLikeCount(@Param("id") Integer id);

    int syncLikeCount(@Param("id") Integer id, @Param("likeCount") int likeCount);

    /** 题解删除时，把其下所有评论一并软删 */
    int softDeleteBySolutionId(@Param("solutionId") Integer solutionId);
}
