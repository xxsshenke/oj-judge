package com.yzj.service.impl;

import com.yzj.exception.BusinessException;
import com.yzj.mapper.ProblemMapper;
import com.yzj.pojo.Problem;
import com.yzj.service.ProblemService;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class ProblemServiceImpl implements ProblemService {

    private final ProblemMapper problemMapper;

    public ProblemServiceImpl(ProblemMapper problemMapper) {
        this.problemMapper = problemMapper;
    }

    @Override
    public List<Problem> list() {
        return problemMapper.findAll();
    }

    @Override
    public Problem detail(Integer id) {
        Problem problem = problemMapper.findDetailById(id);
        if (problem == null) {
            throw new BusinessException("题目不存在：" + id);
        }
        return problem;
    }
}
