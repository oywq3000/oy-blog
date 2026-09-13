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

    /** 文章索引事件流（压实主题：每个 articleId 只保留最新一条） */
    public static final String TOPIC_ARTICLE_INDEX = "article.index";

    @Bean
    public NewTopic articleBehaviorTopic() {
        return TopicBuilder.name(TOPIC_ARTICLE_BEHAVIOR)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG,
                        String.valueOf(Duration.ofDays(30).toMillis()))
                .build();
    }

    /**
     * 文章索引 topic —— <b>压实主题</b>。
     *
     * <p>{@code cleanup.policy=compact} 表示按 key（= articleId）只保留每个 key 的最新一条记录。
     * 新消费者从 offset 0 顺序读一遍，后者覆盖前者，最后一条胜出 ——
     * 读完整即得"所有文章的当前状态"，也就重建了整个 ES 索引。</p>
     *
     * <p><b>刻意不设 {@code retention.ms}</b>：Kafka 的 retention 按段判定，一个段里最新记录
     * 超过保留期就整段删除，<b>包括某篇文章的唯一最新版本</b>。设了时间删除，"半年没更新过的
     * 文章"会从 topic 消失，重放就重建不出它 —— 直接毁掉本功能的全部价值。
     * 存储由"文章数"决定（每篇一条），会饱和，不需要时间兜底。</p>
     *
     * <p>删除动作用 <b>tombstone</b>（{@code value=null}）：压实后该 key 连历史一起消失。</p>
     */
    @Bean
    public NewTopic articleIndexTopic() {
        return TopicBuilder.name(TOPIC_ARTICLE_INDEX)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .build();
    }
}
