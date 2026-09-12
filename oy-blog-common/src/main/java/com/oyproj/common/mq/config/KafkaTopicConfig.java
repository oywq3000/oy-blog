package com.oyproj.common.mq.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;

/**
 * Kafka/Redpanda topic 声明。
 *
 * <p>仅在 classpath 上有 spring-kafka 时生效（未引入 kafka 依赖的服务不受影响）。
 * topic 已存在时 KafkaAdmin 不会重复创建，因此这里的参数只影响首次创建。</p>
 */
@Configuration
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaTopicConfig {

    /** 用户行为事件流（浏览/点赞/收藏/评论） */
    public static final String TOPIC_ARTICLE_BEHAVIOR = "article.behavior";

    @Bean
    public NewTopic articleBehaviorTopic() {
        return TopicBuilder.name(TOPIC_ARTICLE_BEHAVIOR)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG,
                        String.valueOf(Duration.ofDays(30).toMillis()))
                .build();
    }
}
