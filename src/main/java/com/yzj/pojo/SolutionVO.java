package com.yzj.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题解列表项（发给前端的形状）。
 *
 *  刻意**不含 content**：一篇题解可能几千字，一页 10 条就是几十 KB，
 * 而列表页只显示摘要。需要完整正文走详情接口 GET /solutions/{id}。
 * 这与现有 SubmissionMapper 「列表不返回 code」是同一个考虑。
 */
@Data
public class SolutionVO {

    private Integer id;
    private Integer problemId;
    private String title;

    /** 正文前 N 字，仅供列表预览 */
    private String excerpt;

    private Integer userId;
    private String nickname;
    private String avatar;

    private Integer likeCount;
    private Integer commentCount;

    /** 当前登录用户是否赞过；未登录恒为 false */
    private Boolean liked;

    private LocalDateTime createTime;

    /** 是否本人发布 —— 前端据此显示"编辑/删除"按钮，但后端仍会独立校验 */
    private Boolean mine;
}
