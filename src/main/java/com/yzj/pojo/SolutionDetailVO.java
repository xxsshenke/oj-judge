package com.yzj.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题解详情（发给前端的形状）。比 {@link SolutionVO} 多 content 和 problemTitle。
 */
@Data
public class SolutionDetailVO {

    private Integer id;
    private Integer problemId;

    /** 题目名 —— 详情页要显示"这道题"，否则用户从别处跳进来不知道在看哪题 */
    private String problemTitle;

    private String title;
    private String content;

    private Integer userId;
    private String nickname;
    private String avatar;

    private Integer likeCount;
    private Integer commentCount;
    private Boolean liked;
    private Boolean mine;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
