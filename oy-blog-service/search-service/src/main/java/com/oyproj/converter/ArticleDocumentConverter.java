package com.oyproj.converter;

import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.domain.entity.ArticleDocument;

/**
 * 文章索引消息 → ES 文档转换器
 * <p>
 * MQ 消费者（实时同步）与 IndexReconciler（对账兜底）共用同一套转换逻辑，
 * 保证两条数据通路写入 ES 的字段完全一致。
 */
public final class ArticleDocumentConverter {

    private ArticleDocumentConverter() {
    }

    /**
     * 将索引消息转换为 ES 文档。
     * <p>
     * 内容已在生产者侧清洗为纯文本，此处直接透传。
     */
    public static ArticleDocument toDocument(ArticleIndexMessage message) {
        ArticleDocument document = new ArticleDocument();
        document.setId(message.getArticleId());
        document.setSlug(message.getSlug());
        document.setTitle(message.getTitle());
        document.setSummary(message.getSummary());
        document.setAuthorName(message.getAuthorName());
        document.setAuthorAvatar(message.getAuthorAvatar());
        document.setAuthorId(message.getAuthorId());
        document.setCreatedAt(message.getCreatedAt());
        document.setPublishAt(message.getPublishAt());
        document.setUpdatedAt(message.getUpdatedAt());
        document.setStatus(message.getStatus());
        document.setTags(message.getTags());

        // 内容已在生产者侧清洗为纯文本
        document.setContent(message.getContentMd());

        // 统计数据（带 null 保护，兼容旧版本消息）
        document.setViewCount(message.getViewCount() != null ? message.getViewCount() : 0L);
        document.setLikeCount(message.getLikeCount() != null ? message.getLikeCount() : 0L);
        document.setCommentCount(message.getCommentCount() != null ? message.getCommentCount() : 0L);

        return document;
    }
}
