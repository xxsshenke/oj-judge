package com.yzj.queue;

/**
 * 判题任务队列。
 *
 * 两种实现按 profile 切换：
 *   - 默认（!mq）：MemoryJudgeQueue，进程内 ArrayBlockingQueue，由 JudgeWorker 起线程消费
 *   - mq：RabbitJudgeQueue，消息发到 RabbitMQ，由 RabbitJudgeListener 消费
 *
 * 接口只保留"投递"这一件事；"怎么取"由各实现自己决定
 * （内存版靠 take() 阻塞拉取，MQ 版由 broker 推过来）。
 */
public interface JudgeQueue {

    /**
     * 投递一条待判提交。
     *
     * @return false 表示投递失败（内存队列已满）；MQ 模式下基本总是 true
     */
    boolean submit(Integer submissionId);
}
