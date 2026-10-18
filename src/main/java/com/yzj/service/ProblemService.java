package com.yzj.service;

import com.yzj.pojo.Problem;

import java.util.List;

public interface ProblemService {
    List<Problem> list();
    Problem detail(Integer id);
}
