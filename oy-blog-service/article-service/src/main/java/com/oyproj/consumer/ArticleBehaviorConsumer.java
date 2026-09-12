package com.oyproj.consumer;

import com.oyproj.common.mq.config.KafkaTopicConfig;
import com.oyproj.common.mq.domain.ArticleBehaviorEvent;
import com.oyproj.service.HotRankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 行为事件消费者：把事件累加进 Redis 日桶。
 *
 * <p>与 ES 索引消费者（失败重试 + 死信队列）刻意不同：这里 catch 一切异常后
 * <b>照样 ack</b>。Kafka 是 offset 提交制，不 ack 会阻塞整个分区（队头阻塞），
 * 而热榜是装饰功能，不值得为一条毒消息卡住全部分区的消费。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleBehaviorConsumer {

    private final HotRankService hotRankService;

    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_ARTICLE_BEHAVIOR,
            groupId = "${spring.kafka.consumer.group-id:article-hot-rank}")
    public void onEvent(ArticleBehaviorEvent event, Acknowledgment ack) {
        try {
            hotRankService.recordEvent(event);
        } catch (Exception e) {
            log.warn("处理行为事件失败，跳过（不阻塞分区）, event: {}", event, e);
        } finally {
            ack.acknowledge();
        }
    }
}
