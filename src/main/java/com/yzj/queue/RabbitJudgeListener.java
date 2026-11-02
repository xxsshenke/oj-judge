package com.yzj.queue;

import com.rabbitmq.client.Channel;
import com.yzj.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * MQ 版判题消费者。
 *
 * 手动 ACK 的原因：judgeWorker.process 内部虽然吞掉了判题异常，
 * 但「判题结果写库」这一步仍可能失败（数据库抖动、连接断）。
 * 自动 ACK 的话消息已经删了，这条提交就永远停在 JUDGING。
 *
 * requeue 用 false：否则数据库一直不可用会无限重投，把 broker 打满。
 * 失败的消息走死信队列 oj.judge.dlq，事后人工排查。
 */
@Slf4j
@Component
@Profile("mq")
public class RabbitJudgeListener {

    private final JudgeWorker judgeWorker;

    public RabbitJudgeListener(JudgeWorker judgeWorker) {
        this.judgeWorker = judgeWorker;
    }

    @RabbitListener(queues = RabbitConfig.JUDGE_QUEUE, ackMode = "MANUAL")
    public void onSubmission(String submissionId,
                             Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            judgeWorker.process(Integer.valueOf(submissionId));
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("判题消息处理失败，转死信: submissionId={}", submissionId, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
