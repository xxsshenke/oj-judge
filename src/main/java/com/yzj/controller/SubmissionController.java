package com.yzj.controller;

import com.yzj.pojo.Result;
import com.yzj.pojo.Submission;
import com.yzj.pojo.SubmitRequest;
import com.yzj.service.SubmissionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @GetMapping("/{id}")
    public Result detail(@PathVariable Integer id) {
        Submission submission = submissionService.detail(id);

        return Result.success(submission);
    }

    @GetMapping
    public Result listByProblem(@RequestParam Integer problemId) {
        return Result.success(submissionService.listByProblem(problemId));
    }

    @PostMapping
    public Result submit(@RequestBody SubmitRequest req) {
        return Result.success(submissionService.submit(req));
    }
}