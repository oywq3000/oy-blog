package com.oyproj.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyproj.common.mq.constants.ArticleMQConstant;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.domain.entity.MqRetryLog;
import com.oyproj.mapper.MqRetryLogMapper;
import com.oyproj.service.ArticleMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 文章消息生产者服务
 * 发送失败时写入 mq_retry_log 表，由 RetryMqScheduler 定时重试；
 * 同时 IndexReconciler 对账机制确保最终一致性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleMessageProducerImpl implements ArticleMessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final MqRetryLogMapper retryLogMapper;
    private final ObjectMapper objectMapper;

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
            saveRetryLog("ARTICLE_INDEX", message, e);
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
            // 删除消息体简单，直接构造
            ArticleIndexMessage msg = new ArticleIndexMessage();
            msg.setOperation(MQOperation.DELETE);
            msg.setArticleId(articleId);
            saveRetryLog("ARTICLE_DELETE", msg, e);
        }
    }

    /**
     * 写入重试日志表
     */
    private void saveRetryLog(String messageType, ArticleIndexMessage message, Exception e) {
        try {
            String body = objectMapper.writeValueAsString(message);
            MqRetryLog log = MqRetryLog.builder()
                    .messageType(messageType)
                    .messageBody(body)
                    .retryCount(0)
                    .status("PENDING")
                    .errorMsg(e.getMessage() != null ? e.getMessage().substring(0, Math.min(500, e.getMessage().length())) : "unknown")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            retryLogMapper.insert(log);
        } catch (Exception ex) {
            log.error("写入MQ重试日志失败", ex);
        }
    }
}
