package com.yzj.pojo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论 / 回复（发给前端的形状）。
 *
 * 两层结构：顶层评论的 {@link #replies} 有值，回复自己的 replies 为 null。
 * 不做无限嵌套 —— 也不需要，`root_id` 方案本身就是两层的。
 */
@Data
public class CommentVO {

    private Integer id;
    private Integer solutionId;
    private Integer parentId;
    private Integer rootId;

    /**
     * 内容。已软删时为 null —— 由后端置空，**不能指望前端过滤**，
     * 前端一旦漏判就把删掉的内容泄露了。
     */
    private String content;

    private Integer userId;
    private String nickname;
    private String avatar;

    /** 被回复者昵称，前端显示「回复 @张三」；顶层评论为 null */
    private String replyToNickname;

    private Integer likeCount;
    private Boolean liked;

    /** 是否已删除：前端据此显示"该评论已删除"，而不是渲染空内容 */
    private Boolean deleted;

    private Boolean mine;

    private LocalDateTime createTime;

    /**
     * 前若干条回复，仅顶层评论有值。
     *
     * 长度可能小于 {@link #replyCount} —— 见 repliesTruncated。
     */
    private List<CommentVO> replies;

    /** 回复总数（不受截断影响） */
    private Integer replyCount;

    /**
     * 回复是否被截断。
     *
     * 为 true 时前端显示「查看全部 N 条回复」。
     * 不截断的话，一条有 200 回复的主评论会把页面卡死。
     */
    private Boolean repliesTruncated;
}
