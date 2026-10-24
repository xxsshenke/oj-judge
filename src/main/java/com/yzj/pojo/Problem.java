package com.yzj.pojo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class Problem {
    private Integer id;
    private String title;
    private String description;
    private String expected;      // 标准输出，判题用
    private String input;        // 标准输入 , 判题用
    private Integer difficulty;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
