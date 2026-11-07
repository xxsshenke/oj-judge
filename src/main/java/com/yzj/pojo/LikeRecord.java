package com.yzj.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 点赞记录。
 *
 * 题解和评论共用一张表，靠 {@link #targetType} 区分：
 * 拆成两张表的话，点赞逻辑要写两遍，而那两遍几乎一字不差。
 *
 * 这张表是「谁赞过什么」的真相源 —— Redis 里那份 Set 只是快闸，
 * 丢了还能靠这里重建，反过来则不行。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LikeRecord {

    /** 目标类型：题解 */
    public static final int TYPE_SOLUTION = 1;

    /** 目标类型：评论 */
    public static final int TYPE_COMMENT = 2;

    private Long id;
    private Integer targetType;
    private Integer targetId;
    private Integer userId;
    private LocalDateTime createTime;
}
