package com.yzj.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestCase {
    private Integer id;
    private Integer problemId;
    private String input;      // 可为 null
    private String expected;
    private Integer score;     // ACM 模式下忽略
    private Integer sortOrder;
}