package com.yzj.service.impl;

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
import java.util.List;

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

    @Override
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
        JudgeResult judgeResult = judgeService.judge(req.code(), problem.getExpected(),problem.getInput());

        Submission submission = new Submission();
        submission.setProblemId(req.problemId());
        submission.setCode(req.code());
        submission.setLanguage("java");
        submission.setVerdict(judgeResult.verdict());
        submission.setMessage(judgeResult.message());
        submission.setTimeCost((int)judgeResult.timeCostMs());
        submission.setCreateTime(LocalDateTime.now());
        submissionMapper.insert(submission);

        return submission;
    }

    @Override
    public Submission detail(Integer id) {
        Submission submission = submissionMapper.findById(id);
        if (submission == null) {
            throw new BusinessException("提交记录不存在：" + id);
        }
        return submission;
    }

    @Override
    public List<Submission> listByProblem(Integer problemId) {
        return submissionMapper.findByProblemId(problemId);
    }
}
