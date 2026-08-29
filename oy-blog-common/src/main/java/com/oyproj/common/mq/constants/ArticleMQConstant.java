package com.oyproj.common.mq.constants;

public class ArticleMQConstant {
    public static final String ARTICLE_INDEX_EXCHANGE = "article.index.exchange";
    public static final String ARTICLE_INDEX_QUEUE = "article.index.queue";
    public static final String ARTICLE_DELETE_QUEUE = "article.delete.queue";
    public static final String ARTICLE_INDEX_ROUTING_KEY = "article.index";
    public static final String ARTICLE_DELETE_ROUTING_KEY = "article.delete";

    // 死信队列
    public static final String ARTICLE_INDEX_DLX = "article.index.dlx";
    public static final String ARTICLE_INDEX_DLQ = "article.index.dlq";
    public static final String ARTICLE_INDEX_DLQ_ROUTING_KEY = "article.index.dlq";

    // 文章 AI 审核队列（异步审核）
    public static final String ARTICLE_MODERATION_EXCHANGE = "article.moderation.exchange";
    public static final String ARTICLE_MODERATION_QUEUE = "article.moderation.queue";
    public static final String ARTICLE_MODERATION_ROUTING_KEY = "article.moderation";
    // 延迟重试回路：retry exchange → retry 队列（无消费者，消息带逐条 TTL）→ 到期死信回主 exchange
    public static final String ARTICLE_MODERATION_RETRY_EXCHANGE = "article.moderation.retry.exchange";
    public static final String ARTICLE_MODERATION_RETRY_QUEUE = "article.moderation.retry.queue";
}
