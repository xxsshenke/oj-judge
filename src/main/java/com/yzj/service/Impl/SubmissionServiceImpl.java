package com.yzj.service.Impl;

import com.yzj.exception.BusinessException;
import com.yzj.mapper.ProblemMapper;
import com.yzj.mapper.SubmissionMapper;
import com.yzj.pojo.JudgeResult;
import com.yzj.pojo.Problem;
import com.yzj.pojo.Submission;
import com.yzj.pojo.SubmitRequest;
import com.yzj.service.JudgeService;
import com.yzj.service.SubmissionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SubmissionServiceImpl implements SubmissionService {

    private final JudgeService judgeService;
    private final ProblemMapper problemMapper;
    private final SubmissionMapper submissionMapper;

    public SubmissionServiceImpl(JudgeService judgeService,
                             ProblemMapper problemMapper,
                             SubmissionMapper submissionMapper) {
        this.judgeService = judgeService;
        this.problemMapper = problemMapper;
        this.submissionMapper = submissionMapper;
    }

    public Submission submit(SubmitRequest req) {

        //参数校验
        if(req.code() == null || req.code().isBlank()){
            throw new BusinessException("代码不能为空");
        }

        //查看题目
        Problem problem = problemMapper.findById(req.problemId());
        if(problem == null){
            throw new BusinessException("题目不存在:" + req.problemId());
        }

        // 判题
        long start = System.currentTimeMillis();
        JudgeResult judgeResult = judgeService.judge(req.code(), problem.getExpected());
        int cost = (int) (System.currentTimeMillis() - start);

        Submission submission = new Submission();
        submission.setProblemId(req.problemId());
        submission.setCode(req.code());
        submission.setLanguage("java");
        submission.setVerdict(judgeResult.verdict());
        submission.setMessage(judgeResult.message());
        submission.setTimeCost(cost);
        submission.setCreateTime(LocalDateTime.now());
        submissionMapper.insert(submission);

        return submission;
    }

}
