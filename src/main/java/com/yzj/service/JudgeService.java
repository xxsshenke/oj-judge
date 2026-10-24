package com.yzj.service;

import com.yzj.pojo.JudgeResult;

public interface JudgeService {

    JudgeResult judge(String code, String expected, String input);
}
