package com.yzj.service;

import com.yzj.pojo.Submission;
import com.yzj.pojo.SubmitRequest;

public interface SubmissionService {
    Submission submit(SubmitRequest req);
}
