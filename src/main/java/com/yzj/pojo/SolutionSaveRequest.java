package com.yzj.pojo;

/**
 * 发表 / 修改题解的请求体。
 *
 * 用 record 而不是 class：它只承载两个字段、不做任何转换，
 * 和现有 {@link SubmitRequest} 保持一致。
 */
public record SolutionSaveRequest(String title, String content) {
}
