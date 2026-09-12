package com.oyproj.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

/**
 * 行为事件消费者的 Kafka 错误处理策略。
 *
 * <p><b>为什么必须有这个 bean：</b>{@code ArticleBehaviorConsumer} 里的
 * {@code try { ... } finally { ack.acknowledge(); }} 只覆盖得到「监听方法体」，
 * 覆盖不到反序列化失败 —— 那发生在方法体之前的 {@code poll()} 里。没有本配置时，
 * 容器兜底 {@code new DefaultErrorHandler()}，而它的
 * {@code handleOtherException} 对 poll 期异常直接抛 {@code IllegalStateException}
 * （消息文本就是 "This error handler cannot process 'SerializationException's directly;
 * please consider configuring an 'ErrorHandlingDeserializer'..."），
 * 被 {@code KafkaMessageListenerContainer#handleConsumerException} 吞成一行 error 日志后
 * 回到轮询循环 —— 同一条毒消息被反复拉取、位点永不前进，分区被永久卡死并持续刷日志。
 * 这正是本设计明确承诺不会发生的事（见 {@code ArticleBehaviorConsumer} 的类注释）。</p>
 *
 * <p><b>两道保证：</b></p>
 * <ol>
 *   <li>value 反序列化在 {@code application.yml} 里用 {@code ErrorHandlingDeserializer}
 *       包住 {@code JsonDeserializer}：毒消息不再从 {@code poll()} 抛出，而是变成一条
 *       带 {@code DeserializationException} 头的记录，改走记录级错误通道
 *       （{@code ListenerConsumer#checkDeser} → 本 handler 的 {@code handleOne}）。</li>
 *   <li>本 handler 把 {@code DeserializationException} 显式判为不可重试，整体退避设为
 *       零重试，兜底 recoverer 只记日志：毒消息被跳过、留痕，不重试、不阻塞分区。</li>
 * </ol>
 *
 * <p><b>作用域：</b>Spring Boot 的 {@code KafkaAnnotationDrivenConfiguration} 会把容器里唯一的
 * {@code CommonErrorHandler} bean 装到 {@code @KafkaListener} 容器工厂上，所以这是
 * article-service 内所有监听器的全局策略。当前该模块只有行为事件这一个监听器；
 * ES 索引那条链在 search-service，有自己的重试/死信设计，不受影响。</p>
 */
@Slf4j
@Configuration
public class KafkaErrorHandlingConfig {

    /** 零重试：FixedBackOff 的 maxAttempts=0 表示第一次投递后立刻 STOP */
    private static final FixedBackOff NO_RETRIES = new FixedBackOff(0L, 0L);

    @Bean
    public DefaultErrorHandler articleBehaviorErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(KafkaErrorHandlingConfig::logAndSkip, NO_RETRIES);
        // 显式为不可重试，而不是依赖 Spring 的默认分类表（默认表里恰好也有它）：
        // 默认值是可以被 setClassifications/removeClassification 改掉的，而这条是硬保证。
        handler.addNotRetryableExceptions(DeserializationException.class);
        // 处理完（= 记完日志）就让容器提交这条记录的位点，避免重启/重平衡后同一条毒消息再来一遍
        handler.setAckAfterHandle(true);
        return handler;
    }

    /**
     * 只记日志的 recoverer：不重投、不再往外抛。每条毒消息只会走到这里一次
     * （位点随后前进），因此这里带堆栈不会形成日志风暴。
     */
    private static void logAndSkip(ConsumerRecord<?, ?> record, Exception ex) {
        log.warn("跳过无法消费的行为事件（不重试、不阻塞分区）, topic: {}, partition: {}, offset: {}, key: {}",
                record.topic(), record.partition(), record.offset(), record.key(), ex);
    }
}
