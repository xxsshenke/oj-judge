package com.yzj.queue;

import com.yzj.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * MQ 版队列：投递就是把提交 id 发到 RabbitMQ。
 *
 * 和内存版的差别：
 *   - 消息存在 broker 里，服务重启不丢
 *   - 多台判题机可以消费同一个队列，天然负载均衡
 *   - 发送成功只代表 broker 收到了，不代表判完了
 */
@Slf4j
@Component
@Profile("mq")
public class RabbitJudgeQueue implements JudgeQueue {

    private final RabbitTemplate rabbitTemplate;

    public RabbitJudgeQueue(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public boolean submit(Integer submissionId) {
        try {
            // 发字符串而不是 Integer：避免依赖默认消息转换器的类型推断
            rabbitTemplate.convertAndSend(
                    RabbitConfig.JUDGE_EXCHANGE,
                    RabbitConfig.JUDGE_ROUTING_KEY,
                    String.valueOf(submissionId));
            return true;
        } catch (Exception e) {
            log.error("投递判题消息失败: submissionId={}", submissionId, e);
            return false;
        }
    }
}
