package com.oyproj.service.impl;

import com.oyproj.common.mq.constants.ArticleMQConstant;
import com.oyproj.common.mq.domain.ArticleModerationMessage;
import com.oyproj.config.ModerationProperties;
import com.oyproj.service.ModerationRetrySender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * 审核延迟重试发送器。
 * 消息发往独立的 retry exchange → retry 队列（无消费者）→ 逐条 TTL 到期死信回主 exchange
 * 重新投递主队列（发往主 exchange 会让主队列立即消费，延迟失效——必须走 retry exchange）。
 * 重试语义：nextAttempt = 下一次消费的计数值（∈{1,2,3}）；TTL = retryTtlMs[nextAttempt-1]。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationRetrySenderImpl implements ModerationRetrySender {

    private final RabbitTemplate rabbitTemplate;
    private final ModerationProperties properties;

    /** 发延迟重试：nextAttempt 为下一次消费时的计数值（消费端 attempt+1 算出） */
    @Override
    public void sendRetry(String articleId, int nextAttempt) {
        int index = Math.max(0, Math.min(nextAttempt - 1, properties.getRetryTtlMs().size() - 1));
        long ttl = properties.getRetryTtlMs().get(index);
        MessagePostProcessor postProcessor = message -> {
            message.getMessageProperties().setHeader("x-attempt", nextAttempt);
            message.getMessageProperties().setExpiration(String.valueOf(ttl));
            return message;
        };
        ArticleModerationMessage body = ArticleModerationMessage.builder().articleId(articleId).build();
        rabbitTemplate.convertAndSend(
                ArticleMQConstant.ARTICLE_MODERATION_RETRY_EXCHANGE,
                ArticleMQConstant.ARTICLE_MODERATION_ROUTING_KEY,
                body,
                postProcessor
        );
        log.info("审核重试消息已发送, articleId: {}, nextAttempt: {}, TTL: {}ms", articleId, nextAttempt, ttl);
    }
}
