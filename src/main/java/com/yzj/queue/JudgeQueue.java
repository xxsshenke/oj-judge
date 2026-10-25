package com.yzj.queue;

import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 提交队列：接口线程放，判题线程取。
 * 有界队列 —— 满了直接拒绝，不阻塞接口。
 */
@Component
public class JudgeQueue {

    private static final int CAPACITY = 100;

    private final BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(CAPACITY);

    /** 入队。返回 false 表示队列已满。 */
    public boolean submit(Integer submissionId) {
        return queue.offer(submissionId);
    }

    /** 取任务。队列空时【阻塞】。 */
    public Integer take() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }
}