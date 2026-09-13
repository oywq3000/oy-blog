package com.oyproj.common.mq.constants;

/**
 * MQ 常量。
 *
 * <p>索引链路的常量（{@code ARTICLE_INDEX_*} / {@code ARTICLE_DELETE_*}）随索引链路迁至 Kafka
 * 已删除——Kafka 主题名见 {@code KafkaTopicConfig}（{@code TOPIC_ARTICLE_INDEX}）。
 * 这里保留的仅剩审核链路（RabbitMQ）的常量。</p>
 */
public class ArticleMQConstant {
    // 文章 AI 审核队列（异步审核）
    public static final String ARTICLE_MODERATION_EXCHANGE = "article.moderation.exchange";
    public static final String ARTICLE_MODERATION_QUEUE = "article.moderation.queue";
    public static final String ARTICLE_MODERATION_ROUTING_KEY = "article.moderation";
    // 延迟重试回路：retry exchange → retry 队列（无消费者，消息带逐条 TTL）→ 到期死信回主 exchange
    public static final String ARTICLE_MODERATION_RETRY_EXCHANGE = "article.moderation.retry.exchange";
    public static final String ARTICLE_MODERATION_RETRY_QUEUE = "article.moderation.retry.queue";
}
