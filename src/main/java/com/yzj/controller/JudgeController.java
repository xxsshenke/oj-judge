package com.yzj.controller;

import com.yzj.pojo.Result;
import com.yzj.pojo.SubmitRequest;

import com.yzj.service.SubmissionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class JudgeController {

    private final SubmissionService submissionService;

    public JudgeController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }
    @PostMapping("/submissions")
    public Result submit(@RequestBody SubmitRequest req) {
        return Result.success(submissionService.submit(req));
    }

}
