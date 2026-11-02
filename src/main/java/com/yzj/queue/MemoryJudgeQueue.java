package com.yzj.queue;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 单机版队列：进程内 ArrayBlockingQueue。
 *
 * 有界队列 —— 满了直接拒绝，不阻塞接口线程。
 * 消费由 JudgeWorker 自己起线程 take() 完成。
 */
@Component
@Profile("!mq")
public class MemoryJudgeQueue implements JudgeQueue {

    private static final int CAPACITY = 100;

    private final BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(CAPACITY);

    /** 入队。返回 false 表示队列已满。 */
    @Override
    public boolean submit(Integer submissionId) {
        return queue.offer(submissionId);
    }

    /** 取任务。队列空时【阻塞】—— 只有 JudgeWorker 用，所以不在接口里。 */
    public Integer take() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }
}
