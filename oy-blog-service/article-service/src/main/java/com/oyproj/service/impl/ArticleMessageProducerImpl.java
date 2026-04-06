package com.oyproj.service.impl;

import com.oyproj.common.mq.constants.ArticleMQConstant;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.domain.dto.ArticleSaveDto;
import com.oyproj.domain.entity.Article;
import com.oyproj.service.ArticleMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * 文章消息生产者服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleMessageProducerImpl implements ArticleMessageProducer {
    private final RabbitTemplate rabbitTemplate;
    /**
     * 发送文章索引消息
     */
    public void sendArticleIndexMessage(ArticleIndexMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                    ArticleMQConstant.ARTICLE_INDEX_EXCHANGE,
                    ArticleMQConstant.ARTICLE_INDEX_ROUTING_KEY,
                    message
            );
            log.info("文章索引消息发送成功，文章ID: {}, 操作类型: {}", message.getArticleId(), message.getOperation());
        } catch (Exception e) {
            log.error("文章索引消息发送失败，文章ID: {}, 错误: {}", message.getArticleId(), e.getMessage());
            //todo 添加降级策略
        }
    }

    /**
     * 发送文章删除消息
     */
    public void sendArticleDeleteMessage(String articleId) {
        try {
            ArticleIndexMessage message = new ArticleIndexMessage();
            message.setOperation(MQOperation.DELETE);
            message.setArticleId(articleId);
            rabbitTemplate.convertAndSend(
                    ArticleMQConstant.ARTICLE_INDEX_EXCHANGE,
                    ArticleMQConstant.ARTICLE_DELETE_ROUTING_KEY,
                    message
            );
            log.info("文章删除消息发送成功，文章ID: {}", articleId);
        } catch (Exception e) {
            log.error("文章删除消息发送失败，文章ID: {}, 错误: {}", articleId, e.getMessage());
        }
    }

   /* *//**
     * 批量发送文章索引消息
     *//*
    public void batchSendArticleIndexMessages(List<Article> articles,, String operation) {
        for (Article article : articles) {
            sendArticleIndexMessage(article, operation);
        }
    }*/

    /**
     * 将Article实体转换为消息对象
     */
    private ArticleIndexMessage convertToMessage(Article article,ArticleSaveDto dto, MQOperation operation) {
        ArticleIndexMessage message = new ArticleIndexMessage();
        message.setOperation(operation);
        message.setArticleId(article.getId());
        message.setTitle(article.getTitle());
        message.setSummary(article.getSummary());
        //message.setAuthor(article.getAuthor());
        message.setAuthorId(article.getAuthorId());
        message.setCreateTime(article.getCreatedAt());
        message.setUpdateTime(article.getUpdatedAt());
        message.setStatus(article.getStatus());
        message.setCategory(dto.getCategoryCode());
        message.setTags(dto.getTags());
        return message;
    }
}