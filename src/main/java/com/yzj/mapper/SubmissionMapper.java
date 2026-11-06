package com.yzj.mapper;

import com.yzj.pojo.Submission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SubmissionMapper {

    void insert(Submission submission);

    Submission findById(@Param("id") Integer id);

    /**
     * 按题目分页查询提交记录。
     *
     * ⚠️ 这里刻意不返回 code 字段（见 XML 的 listColumns）。
     * 列表页只展示结果、耗时、时间，返回大段源码纯属浪费带宽和内存：
     * 单条 code 可能几十 KB，一页 20 条就是几百 KB，而页面一个字节都用不到。
     * 需要看源码时走 GET /submissions/{id} 单独取。
     *
     * @param offset 偏移量 = (pageNum - 1) * pageSize
     */
    List<Submission> findPageByProblemId(@Param("problemId") Integer problemId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /** 与 findPageByProblemId 完全相同的过滤条件，只算总数 */
    long countByProblemId(@Param("problemId") Integer problemId);

    // ⭐ 新增：只更新判定结果，不动 code（code 太大）
    void updateResult(Submission submission);

    int updateStatus(@Param("id") Integer id, @Param("status") String status);

    // ⭐ 新增：服务启动时恢复未完成的提交
    List<Submission> findByStatus(@Param("status") String status);
}
