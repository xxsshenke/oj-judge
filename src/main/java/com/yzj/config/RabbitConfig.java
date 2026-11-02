package com.yzj.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 判题 MQ 拓扑（只在 mq profile 下生效）。
 *
 *   oj.judge.exchange (direct)
 *        └─ routingKey oj.judge.submit ──> oj.judge.queue   ← 正常判题
 *                                              │ nack(requeue=false)
 *                                              ▼
 *   oj.judge.dlx (direct)
 *        └─ routingKey oj.judge.dead ────> oj.judge.dlq     ← 死信，人工排查
 */
@Configuration
@Profile("mq")
public class RabbitConfig {

    public static final String JUDGE_EXCHANGE = "oj.judge.exchange";
    public static final String JUDGE_QUEUE = "oj.judge.queue";
    public static final String JUDGE_ROUTING_KEY = "oj.judge.submit";

    public static final String JUDGE_DLX = "oj.judge.dlx";
    public static final String JUDGE_DLQ = "oj.judge.dlq";
    public static final String JUDGE_DEAD_ROUTING_KEY = "oj.judge.dead";

    /** 正常判题队列：绑定死信交换机，处理失败的消息会进 DLQ */
    @Bean
    public Queue judgeQueue() {
        return QueueBuilder.durable(JUDGE_QUEUE)
                .deadLetterExchange(JUDGE_DLX)
                .deadLetterRoutingKey(JUDGE_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange judgeExchange() {
        return new DirectExchange(JUDGE_EXCHANGE, true, false);
    }

    @Bean
    public Binding judgeBinding() {
        return BindingBuilder.bind(judgeQueue()).to(judgeExchange()).with(JUDGE_ROUTING_KEY);
    }

    @Bean
    public Queue judgeDeadQueue() {
        return QueueBuilder.durable(JUDGE_DLQ).build();
    }

    @Bean
    public DirectExchange judgeDeadExchange() {
        return new DirectExchange(JUDGE_DLX, true, false);
    }

    @Bean
    public Binding judgeDeadBinding() {
        return BindingBuilder.bind(judgeDeadQueue()).to(judgeDeadExchange()).with(JUDGE_DEAD_ROUTING_KEY);
    }
}
