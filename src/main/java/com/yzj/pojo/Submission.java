package com.yzj.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Submission {
    private Integer id;
    private Integer problemId;
    private String code;
    private String language;
    private String verdict;       // AC/WA/CE/RE/TLE/SE
    private String message;
    private Integer timeCost;     // 判题耗时 ms
    private LocalDateTime createTime;
}