package com.yzj.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标签及其题目数量
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagCount {

    private String name;
    private Integer count;
}
