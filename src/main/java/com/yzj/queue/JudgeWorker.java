package com.yzj.queue;

import com.yzj.exception.BusinessException;
import com.yzj.mapper.ProblemMapper;
import com.yzj.mapper.SubmissionMapper;
import com.yzj.mapper.TestCaseMapper;
import com.yzj.pojo.JudgeResult;
import com.yzj.pojo.Problem;
import com.yzj.pojo.Submission;
import com.yzj.pojo.TestCase;
import com.yzj.service.JudgeService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class JudgeWorker {

    private static final int WORKER_COUNT = 2;

    private final JudgeQueue judgeQueue;
    private final JudgeService judgeService;
    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final TestCaseMapper testCaseMapper;

    private final List<Thread> workers = new ArrayList<>();
    private volatile boolean running = true;

    public JudgeWorker(JudgeQueue judgeQueue, JudgeService judgeService, SubmissionMapper submissionMapper, ProblemMapper problemMapper, TestCaseMapper testCaseMapper) {
        this.judgeQueue = judgeQueue;
        this.judgeService = judgeService;
        this.submissionMapper = submissionMapper;
        this.problemMapper = problemMapper;
        this.testCaseMapper = testCaseMapper;
    }
    @PostConstruct
    public void start() {
        // 把上次没跑完的 PENDING 记录重新入队

        for (Submission s : submissionMapper.findByStatus("JUDGING")) {
            submissionMapper.updateStatus(s.getId(), "SE");
        }

        for (Submission s : submissionMapper.findByStatus("PENDING")) {
            judgeQueue.submit(s.getId());
        }

        //  启动 WORKER_COUNT 个线程
        for (int i = 0; i < WORKER_COUNT; i++) {
            Thread t = new Thread(this::loop, "judge-" + i);
            t.start();
            workers.add(t);
        }
        log.info("判题机池启动，{} 个 worker", WORKER_COUNT);
    }

    @PreDestroy
    public void stop() {
        running = false;
        workers.forEach(Thread::interrupt);          // 关键：打断阻塞的 take()
        log.info("判题机池关闭");
    }

    private void loop() {
        while (running) {
            try {
                Integer submissionId = judgeQueue.take();
                process(submissionId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                //  绝对不能让它冒出去，否则线程死掉
                log.error("判题线程异常", e);
            }
        }
    }

    private void process(Integer submissionId) {
        try {
            doProcess(submissionId);
        } catch (Exception e) {
            log.error("判题异常，置为 SE: {}", submissionId, e);
            submissionMapper.updateStatus(submissionId, "SE");     //  兜底
        }
    }
    /** 判一条提交 */
    private void doProcess(Integer submissionId) {

        Submission record = submissionMapper.findById(submissionId);

        if (record == null) {
            log.warn("提交不存在，跳过: {}", submissionId);
            return;
        }
        if (!"PENDING".equals(record.getStatus())) {
            log.warn("提交状态不是 PENDING，跳过: {} status={}", submissionId, record.getStatus());
            return;
        }

        int rows = submissionMapper.updateStatus(submissionId, "JUDGING");

        if (rows == 0) {
            log.warn("任务已被其他节点抢占或状态已变更: {}", submissionId);
            return;
        }

        Integer problemId = record.getProblemId();
        List<TestCase> cases = testCaseMapper.findByProblemId(problemId);
        Problem problem = problemMapper.findById(problemId);

        if (problem == null) {
            log.warn("题目不存在");
            throw new BusinessException("题目不存在");
        }

        // 这里刻意不做“测试点为空就退回用 problem.input/expected 判一次”的兜底。
        // 旧逻辑下题目没配测试点时只比对页面样例，用户原样输出样例就能 AC，属于判题漏洞。
        // 现在 cases 为空会由 JudgeService.judge 统一返回 SE（题目没有配置测试点），
        // 让数据缺失显式暴露，而不是伪装成通过。

        boolean isOi = "OI".equals(problem.getJudgeMode());

        record.setId(submissionId);
        try {
            JudgeResult judgeResult = judgeService.judge(record.getCode(), cases, isOi);
            record.setVerdict(judgeResult.verdict());
            record.setMessage(judgeResult.message());
            record.setTimeCost((int)judgeResult.timeCostMs());
            record.setScore(judgeResult.score());
            record.setStatus("DONE");
        } catch (Exception e){
            log.error("判题失败: {}", submissionId, e);
            record.setStatus("DONE"); // 业务状态完成，只是结果不好
            record.setVerdict("SE");  // 判题机系统错误
            record.setMessage("判题机内部异常: " + e.getMessage());
            record.setTimeCost(0);
        }

        submissionMapper.updateResult(record);
    }
}