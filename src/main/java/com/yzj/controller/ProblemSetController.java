package com.yzj.controller;

import com.yzj.pojo.Result;
import com.yzj.service.ProblemSetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题库页接口。
 *
 * 只暴露两个只读接口，职责刻意保持很薄：参数清洗、聚合计算都在 Service 层，
 * 这里只负责把结果包成统一的 Result{code,data}。
 *
 *   GET /problem-sets/overview   概览：难度分布、完成情况、标签计数、每日一题
 *   GET /problem-sets/problems   列表：按关键字 / 难度 / 标签筛选
 *
 * 列表接口不分页：当前题库只有十余题，一次返回全量，分页由前端按 15 条切片完成。
 * 这样筛选、计数、分页共用同一份数据，不会出现“总数”和“当前页”口径不一致。
 */
@RestController
@RequestMapping("/problem-sets")
public class ProblemSetController {

    private final ProblemSetService problemSetService;

    public ProblemSetController(ProblemSetService problemSetService) {
        this.problemSetService = problemSetService;
    }

    /**
     * 题库概览：难度分布、完成情况、标签统计、每日一题
     */
    @GetMapping("/overview")
    public Result overview() {
        return Result.success(problemSetService.overview());
    }

    /**
     * 题库列表：keyword 支持标题模糊匹配与题号精确匹配。
     *
     * 三个参数都是可选的，缺省表示不限制：
     *   keyword     标题包含该串，或题号与之完全相等（前端会先去掉开头的 #）
     *   difficulty  1 简单 / 2 中等 / 3 困难
     *   tag         单个标签名，必须与 tags 里逗号分隔的某一项完全一致
     */
    @GetMapping("/problems")
    public Result problems(@RequestParam(required = false) String keyword,
                           @RequestParam(required = false) Integer difficulty,
                           @RequestParam(required = false) String tag) {
        return Result.success(problemSetService.problems(keyword, difficulty, tag));
    }
}
