package com.yzj.service.impl;

import com.yzj.exception.BusinessException;
import com.yzj.mapper.ProblemMapper;
import com.yzj.mapper.SubmissionMapper;
import com.yzj.mapper.TestCaseMapper;
import com.yzj.pojo.*;
import com.yzj.queue.JudgeQueue;
import com.yzj.service.JudgeService;
import com.yzj.service.SubmissionService;
import com.yzj.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SubmissionServiceImpl implements SubmissionService {

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 每页条数上限。
     *
     * 不设上限的话，前端传 pageSize=100000 就等于让人一次拉走全表，
     * 分页也就白做了。50 是"够一屏翻好几倍"和"不拖垮接口"之间的折中。
     */
    private static final int MAX_PAGE_SIZE = 50;

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
        submission.setUserId(UserHolder.get());      // 登录用户，由 RefreshTokenInterceptor 写入
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
    public PageResult<Submission> pageByProblem(Integer problemId, int pageNum, int pageSize) {
        if (problemId == null) {
            throw new BusinessException("题目 ID 不能为空");
        }

        // 把外部传进来的页码/页大小收敛到合法范围。
        // 这类修正必须在服务端做：前端可以绕过，直接 curl 一个 pageSize=99999。
        int safePageSize = normalizePageSize(pageSize);
        int safePageNum = Math.max(pageNum, 1);

        long total = submissionMapper.countByProblemId(problemId);
        if (total == 0) {
            return PageResult.empty(safePageNum, safePageSize);
        }

        // 页码超出总页数时（比如用户收藏了 ?page=99 而数据被删），
        // 直接回退到最后一页，而不是返回空列表 —— 空列表会让前端显示"共 0 条"，
        // 跟 total 自相矛盾
        long pages = (total + safePageSize - 1) / safePageSize;
        if (safePageNum > pages) {
            safePageNum = (int) pages;
        }

        int offset = (safePageNum - 1) * safePageSize;
        List<Submission> records = submissionMapper.findPageByProblemId(problemId, offset, safePageSize);
        return PageResult.of(records, total, safePageNum, safePageSize);
    }

    /** 页大小收敛：null/非正数走默认值，超过上限按上限算。 */
    private int normalizePageSize(int pageSize) {
        if (pageSize <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
