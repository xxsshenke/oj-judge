package com.yzj.service;

import com.yzj.pojo.vo.JudgeResult;
import com.yzj.pojo.entity.TestCase;

import java.util.List;

public interface JudgeService {

    JudgeResult judge(String code, List<TestCase> cases, boolean isOI);
}
