package com.oyproj.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyproj.common.mq.config.KafkaTopicConfig;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.domain.entity.MqRetryLog;
import com.oyproj.mapper.MqRetryLogMapper;
import com.oyproj.service.ArticleMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 文章索引消息生产者（Kafka/Redpanda）。
 *
 * <p><b>只有"调用点失败"才会落库</b>：{@code send()} 同步抛出的异常（broker 不可达、元数据超时
 * 触发 {@code max.block.ms} 等）写进 mq_retry_log 表，由 RetryMqScheduler 定时重试；
 * <b>异步 puback 失败不走这条路径</b>——{@code send()} 只负责入队，broker 侧后来的拒绝/超时
 * 不会在调用点抛出，因此不会触发 catch、也不会落库，那段边界最终只能由 IndexReconciler 对账兜住
 * （详见 doc/es-mysql-sync-mechanism.md 的容错层次）。</p>
 *
 * <p><b>删除走 tombstone</b>（{@code value = null}）：压实主题按 key 只保留最新一条记录，
 * 发 null 才能让该 key 连历史一起消失。文章 id 在消息 KEY 上，消费端从 key 取。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleMessageProducerImpl implements ArticleMessageProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MqRetryLogMapper retryLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 发送文章索引消息
     */
    public void sendArticleIndexMessage(ArticleIndexMessage message) {
        try {
            // 分区键 = articleId：同篇文章的事件进同一分区（保序），
            // 同时也是压实主题的 key（每篇只保留最新一条）
            kafkaTemplate.send(KafkaTopicConfig.TOPIC_ARTICLE_INDEX, message.getArticleId(), message);
            // 措辞刻意是"已提交"而不是"发送成功"：send() 只是把记录交给发送缓冲，
            // 异步的 puback 失败不会回到这里（也就不会触发下面的 catch）。
            log.info("文章索引消息已提交（异步入队，未等确认），文章ID: {}, 操作类型: {}",
                    message.getArticleId(), message.getOperation());
        } catch (Exception e) {
            log.error("文章索引消息发送失败（调用点异常），文章ID: {}, 错误: {}", message.getArticleId(), e.getMessage());
            saveRetryLog("ARTICLE_INDEX", message, e);
        }
    }

    /**
     * 发送文章删除消息（tombstone）
     */
    public void sendArticleDeleteMessage(String articleId) {
        try {
            kafkaTemplate.send(KafkaTopicConfig.TOPIC_ARTICLE_INDEX, articleId, null);
            // 同上：这里只能保证"已提交入队"，确认结果是异步的
            log.info("文章删除消息（tombstone）已提交（异步入队，未等确认），文章ID: {}", articleId);
        } catch (Exception e) {
            log.error("文章删除消息发送失败（调用点异常），文章ID: {}, 错误: {}", articleId, e.getMessage());
            // 降级落库存完整消息（含 operation=DELETE）——message_body 是普通列，存不了 null。
            // RetryMqScheduler 重发时会识别 DELETE 再转回 null。
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
