package com.oyproj.service.impl;

import com.oyproj.common.mq.config.KafkaTopicConfig;
import com.oyproj.common.mq.constants.ArticleBehaviorType;
import com.oyproj.common.mq.domain.ArticleBehaviorEvent;
import com.oyproj.service.ArticleEventPublisher;
import com.oyproj.service.ArticleEventWeights;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 行为事件发布器实现。
 *
 * <p>刻意做成"薄壳"：同步发一条消息、失败只记日志。与 ES 索引消息那条有四层兜底的
 * 链路相反——热榜算错 5% 没有任何用户会察觉，不值得为它付出重试/死信/对账的复杂度。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleEventPublisherImpl implements ArticleEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ArticleEventWeights articleEventWeights;

    @Override
    public void publishView(String articleId, String userId) {
        publish(articleId, userId, ArticleBehaviorType.VIEW);
    }

    @Override
    public void publishLike(String articleId, String userId, boolean cancelled) {
        publish(articleId, userId, cancelled ? ArticleBehaviorType.UNLIKE : ArticleBehaviorType.LIKE);
    }

    @Override
    public void publishFavorite(String articleId, String userId, boolean cancelled) {
        publish(articleId, userId, cancelled ? ArticleBehaviorType.UNFAVORITE : ArticleBehaviorType.FAVORITE);
    }

    @Override
    public void publish(String articleId, String userId, ArticleBehaviorType type) {
        if (articleId == null || articleId.isEmpty() || type == null) {
            return;
        }
        try {
            ArticleBehaviorEvent event = ArticleBehaviorEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(type)
                    .articleId(articleId)
                    .userId(userId)
                    .occurredAt(OffsetDateTime.now().toString())
                    .weight(articleEventWeights.weightOf(type))
                    .build();
            // 分区键用 articleId：同一篇文章的事件进同一分区，保证顺序
            kafkaTemplate.send(KafkaTopicConfig.TOPIC_ARTICLE_BEHAVIOR, articleId, event);
        } catch (Exception e) {
            // 装饰功能：吞掉异常，绝不影响调用方
            log.warn("发布行为事件失败, articleId: {}, type: {}", articleId, type, e);
        }
    }
}
