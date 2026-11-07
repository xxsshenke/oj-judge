package com.yzj.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 题解。
 *
 * 与 {@link SolutionVO} 的区别：这个是"数据库行的镜像"，
 * VO 是"要发给前端的形状"（多了 nickname/liked、少了 content）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Solution {

    private Integer id;
    private Integer problemId;
    private Integer userId;
    private String title;
    private String content;

    /** 点赞数（冗余字段，靠 increment/decrement 增量维护，见 LikeServiceImpl） */
    private Integer likeCount;

    /** 评论总数，含回复。语义要和前端对齐：用户说"7 条评论"指的是总共 7 条发言 */
    private Integer commentCount;

    /** 0 正常 / 1 已删除（软删） */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
