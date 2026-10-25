package com.yzj.service.impl;

import com.yzj.exception.BusinessException;
import com.yzj.mapper.ProblemMapper;
import com.yzj.mapper.SubmissionMapper;
import com.yzj.mapper.TestCaseMapper;
import com.yzj.pojo.*;
import com.yzj.queue.JudgeQueue;
import com.yzj.service.JudgeService;
import com.yzj.service.SubmissionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SubmissionServiceImpl implements SubmissionService {

    private final JudgeQueue judgeQueue;
    private final JudgeService judgeService;
    private final ProblemMapper problemMapper;
    private final SubmissionMapper submissionMapper;
    private final TestCaseMapper testCaseMapper;

    public SubmissionServiceImpl(JudgeService judgeService,
                                 ProblemMapper problemMapper,
                                 SubmissionMapper submissionMapper,
                                 TestCaseMapper testCaseMapper,
                                 JudgeQueue judgeQueue
                                 ) {
        this.judgeService = judgeService;
        this.problemMapper = problemMapper;
        this.submissionMapper = submissionMapper;
        this.testCaseMapper = testCaseMapper;
        this.judgeQueue = judgeQueue;
    }

    @Override
    public Submission submit(SubmitRequest req) {

        //  参数校验（快速失败，同步做）
        if (req.code() == null || req.code().isBlank()) {
            throw new BusinessException("代码不能为空");
        }
        Problem problem = problemMapper.findById(req.problemId());
        if (problem == null) {
            throw new BusinessException("题目不存在：" + req.problemId());
        }

        //  落库（status = PENDING）
        Submission submission = new Submission();
        submission.setProblemId(req.problemId());
        submission.setCode(req.code());
        submission.setLanguage("java");
        submission.setStatus("PENDING");
        submission.setCreateTime(LocalDateTime.now());
        submissionMapper.insert(submission);       // insert 之后才有 id

        //  入队
        if (!judgeQueue.submit(submission.getId())) {
            throw new BusinessException("判题队列已满，请稍后重试");
        }

        //  立刻返回（此时 verdict 是 null，前端要轮询）
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
