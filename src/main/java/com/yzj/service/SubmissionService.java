package com.yzj.service;

import com.yzj.pojo.Submission;
import com.yzj.pojo.SubmitRequest;

import java.util.List;

public interface SubmissionService {
    Submission submit(SubmitRequest req);

    Submission detail(Integer id);

    List<Submission> listByProblem(Integer problemId);
}
