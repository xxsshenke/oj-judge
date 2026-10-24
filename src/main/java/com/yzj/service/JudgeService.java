package com.yzj.service;

import com.yzj.pojo.JudgeResult;
import com.yzj.pojo.TestCase;

import java.util.List;

public interface JudgeService {

    JudgeResult judge(String code, List<TestCase> cases, boolean isOI);
}
