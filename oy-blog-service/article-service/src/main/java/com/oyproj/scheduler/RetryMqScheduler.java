package com.oyproj.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyproj.common.mq.constants.ArticleMQConstant;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.domain.entity.MqRetryLog;
import com.oyproj.mapper.MqRetryLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MQ 消息重试调度器
 * 每分钟扫描 mq_retry_log 表中 PENDING 状态的失败消息，尝试重新发送。
 * 超过最大重试次数（5次）后标记为 FAILED。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryMqScheduler {

    private static final int MAX_RETRY = 5;

    private final MqRetryLogMapper retryLogMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${oy-blog.mq.retry-interval-ms:60000}",
               initialDelayString = "${oy-blog.mq.retry-initial-delay-ms:30000}")
    public void retryFailedMessages() {
        List<MqRetryLog> pendingLogs = retryLogMapper.selectList(
                new LambdaQueryWrapper<MqRetryLog>()
                        .eq(MqRetryLog::getStatus, "PENDING")
                        .lt(MqRetryLog::getRetryCount, MAX_RETRY)
                        .orderByAsc(MqRetryLog::getId)
                        .last("LIMIT 50")
        );

        if (pendingLogs.isEmpty()) {
            return;
        }

        log.info("开始重试 MQ 消息, 共 {} 条", pendingLogs.size());
        int successCount = 0;
        int failCount = 0;

        for (MqRetryLog retryLog : pendingLogs) {
            try {
                ArticleIndexMessage message = objectMapper.readValue(
                        retryLog.getMessageBody(), ArticleIndexMessage.class);
                String routingKey = "ARTICLE_DELETE".equals(retryLog.getMessageType())
                        ? ArticleMQConstant.ARTICLE_DELETE_ROUTING_KEY
                        : ArticleMQConstant.ARTICLE_INDEX_ROUTING_KEY;

                rabbitTemplate.convertAndSend(
                        ArticleMQConstant.ARTICLE_INDEX_EXCHANGE, routingKey, message);

                // 标记成功
                retryLogMapper.update(null, new LambdaUpdateWrapper<MqRetryLog>()
                        .eq(MqRetryLog::getId, retryLog.getId())
                        .set(MqRetryLog::getStatus, "SUCCESS")
                        .set(MqRetryLog::getUpdatedAt, LocalDateTime.now()));
                successCount++;
            } catch (Exception e) {
                int newRetryCount = retryLog.getRetryCount() + 1;
                String newStatus = newRetryCount >= MAX_RETRY ? "FAILED" : "PENDING";
                retryLogMapper.update(null, new LambdaUpdateWrapper<MqRetryLog>()
                        .eq(MqRetryLog::getId, retryLog.getId())
                        .set(MqRetryLog::getRetryCount, newRetryCount)
                        .set(MqRetryLog::getStatus, newStatus)
                        .set(MqRetryLog::getErrorMsg, e.getMessage() != null
                                ? e.getMessage().substring(0, Math.min(500, e.getMessage().length()))
                                : "unknown")
                        .set(MqRetryLog::getUpdatedAt, LocalDateTime.now()));
                failCount++;
            }
        }

        log.info("MQ 重试完成: 成功 {} 条, 失败 {} 条", successCount, failCount);
    }
}
