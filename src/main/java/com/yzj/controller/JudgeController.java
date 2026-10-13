package com.yzj.controller;

import com.yzj.pojo.JudgeResult;
import com.yzj.pojo.SubmitRequest;
import com.yzj.service.JudgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;


@RestController
public class JudgeController {
    private final JudgeService judgeService;

    public JudgeController(JudgeService judgeService) {
        this.judgeService = judgeService;
    }
    @PostMapping("/submit")
    public JudgeResult submitController(
            @RequestBody SubmitRequest req) throws IOException, InterruptedException {
        return judgeService.judge(req.code(), req.expected());
    }

}
