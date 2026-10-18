package com.yzj.controller;

import com.yzj.exception.BusinessException;
import com.yzj.mapper.SubmissionMapper;
import com.yzj.pojo.Result;
import com.yzj.pojo.Submission;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/submissions")
public class SubmissionController {

    private final SubmissionMapper submissionMapper;

    public SubmissionController(SubmissionMapper submissionMapper) {
        this.submissionMapper = submissionMapper;
    }

    @GetMapping("/{id}")
    public Result detail(@PathVariable Integer id) {
        Submission submission = submissionMapper.findById(id);
        if (submission == null) {
            throw new BusinessException("提交记录不存在：" + id);
        }
        return Result.success(submission);
    }

    @GetMapping
    public Result listByProblem(@RequestParam Integer problemId) {
        return Result.success(submissionMapper.findByProblemId(problemId));
    }
}