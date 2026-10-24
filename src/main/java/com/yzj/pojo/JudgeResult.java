package com.yzj.pojo;

import java.util.List;

public record JudgeResult(
        String verdict,       // AC / PARTIAL / WA / CE / RE / TLE / MLE / SE
        String message,
        long timeCostMs,
        Integer score,        // OI 得分；ACM 为 null
        Integer passedCount,  // 通过的测试点数
        Integer totalCount    // 测试点总数
) {

    /** ACM 通过 */
    public static JudgeResult ac(long cost) {
        return new JudgeResult("AC", null, cost, null, null, null);
    }

    /** ACM 失败 */
    public static JudgeResult fail(String verdict, String message, long cost) {
        return new JudgeResult(verdict, message, cost, null, null, null);
    }

    /** OI 结果：有分就有 partial，满分就是 AC */
    public static JudgeResult oi(int score, int maxScore, int passed, int total,
                                 long cost, String detail) {
        String verdict = (score >= maxScore) ? "AC" : "PARTIAL";
        return new JudgeResult(verdict, detail, cost, score, passed, total);
    }
}