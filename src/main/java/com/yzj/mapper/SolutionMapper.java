package com.yzj.mapper;

import com.yzj.pojo.Solution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SolutionMapper {

    void insert(Solution solution);

    /**
     * 查未删除的题解。列表/详情/点赞前置校验都用它。
     *
     * 返回带 content，列表页在 Service 里截断成 excerpt，
     * 而不是让 XML 里塞 substring —— 截断长度是展示逻辑，不该进 SQL。
     */
    Solution findById(@Param("id") Integer id);

    /**
     * 分页查某题的题解。
     *
     * @param sort 只能是 "new" 或 "hot"，Service 层已收敛过；
     *             ⚠️ 绝不能把外部传的值直接拼进 ORDER BY
     */
    List<Solution> findPageByProblem(@Param("problemId") Integer problemId,
                                     @Param("sort") String sort,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    long countByProblem(@Param("problemId") Integer problemId);

    /** 某人是否已在此题下发过题解（一人一题限一篇） */
    Solution findByProblemAndUser(@Param("problemId") Integer problemId,
                                  @Param("userId") Integer userId);

    int updateContent(@Param("id") Integer id,
                      @Param("title") String title,
                      @Param("content") String content);

    int softDelete(@Param("id") Integer id);

    /** 点赞数 +1 */
    int incrementLikeCount(@Param("id") Integer id);

    /**
     * 点赞数 -1。
     *
     * ⚠️ SQL 里必须用 GREATEST(like_count - 1, 0)：
     * like_count 是 int NOT NULL，减到 -1 会静默存进去（非严格模式）
     * 或直接报错（严格模式），两种都是 bug。
     */
    int decrementLikeCount(@Param("id") Integer id);

    int incrementCommentCount(@Param("id") Integer id);

    int decrementCommentCount(@Param("id") Integer id);

    /** 点赞数重算（自愈用）：把 like_count 覆盖成真实记录数 */
    int syncLikeCount(@Param("id") Integer id, @Param("likeCount") int likeCount);
}
