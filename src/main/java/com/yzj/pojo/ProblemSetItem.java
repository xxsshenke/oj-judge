package com.yzj.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 题库列表行：题目基础信息 + 提交统计 + 当前用户完成状态。
 *
 * 字段全部由 ProblemSetMapper.findProblems 的聚合查询填充，
 * 其中 solved / attempted 在 SQL 里是 0/1，MyBatis 会自动映射成 Boolean。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemSetItem {

    private Integer id;
    private String title;
    private Integer difficulty;
    private String tags;
    private Integer submitCount;
    private Integer acceptedCount;
    private Boolean solved;
    private Boolean attempted;

    /**
     * 通过率 = 通过提交数 / 总提交数，保留一位小数。
     *
     * 没有提交时返回 0，由前端显示成“—”，避免出现 0.0% 误导成“提交过但没过”。
     * 这里用整数放大 1000 倍再四舍五入，避免浮点误差（如 2/3 → 66.7）。
     */
    public double getPassRate() {
        if (submitCount == null || submitCount == 0 || acceptedCount == null) {
            return 0D;
        }
        return Math.round(acceptedCount * 1000D / submitCount) / 10D;
    }
}
