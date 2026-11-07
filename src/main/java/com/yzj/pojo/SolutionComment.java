package com.yzj.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 题解评论（主评论和回复共用一张表）。
 *
 * <pre>
 *   id  parentId  rootId
 *   1   null      null     主评论
 *   2   1         1        回复主评论
 *   3   2         1        回复的回复 —— rootId 仍是 1，parentId 是 2
 * </pre>
 *
 * 冗余 rootId 是为了让"查一页主评论的全部回复"变成一条
 * {@code WHERE root_id IN (...)}，而不必递归展开。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolutionComment {

    private Integer id;
    private Integer solutionId;
    private Integer userId;

    /** 直接回复的那条评论 id；主评论为 null */
    private Integer parentId;

    /** 所属顶层主评论 id；主评论为 null */
    private Integer rootId;

    /**
     * 被回复者 id（= parent 的 userId）。
     *
     * 冗余这一列是为了显示「回复 @张三」时不必再 JOIN 一次 user，
     * 而且 parent 万一被硬删，这个值仍然是对的。
     */
    private Integer replyToUser;

    private String content;
    private Integer likeCount;

    /** 0 正常 / 1 已删除（软删） */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public boolean isDeleted() {
        return status != null && status == 1;
    }
}
