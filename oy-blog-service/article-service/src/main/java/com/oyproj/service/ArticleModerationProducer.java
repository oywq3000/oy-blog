package com.oyproj.service;

import com.oyproj.common.mq.constants.ArticleMQConstant;
import com.oyproj.common.mq.domain.ArticleModerationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * 文章审核消息生产者。
 * 发送失败只记日志不抛异常：文章已落库 ai_reviewing，ModerationStuckScanner 兜底扫描会接管。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleModerationProducer {

    private final RabbitTemplate rabbitTemplate;

    /** 发布/编辑提交后触发一次后台审核 */
    public void sendModerationMessage(String articleId) {
        try {
            ArticleModerationMessage message = ArticleModerationMessage.builder()
                    .articleId(articleId)
                    .build();
            rabbitTemplate.convertAndSend(
                    ArticleMQConstant.ARTICLE_MODERATION_EXCHANGE,
                    ArticleMQConstant.ARTICLE_MODERATION_ROUTING_KEY,
                    message
            );
            log.info("审核消息发送成功, articleId: {}", articleId);
        } catch (Exception e) {
            log.error("审核消息发送失败（兜底扫描将接管）, articleId: {}, 错误: {}", articleId, e.getMessage());
        }
    }
}
