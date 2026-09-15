package com.oyproj.consumer;

import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.mq.config.KafkaTopicConfig;
import com.oyproj.common.mq.domain.ArticleBehaviorEvent;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.util.FaultLogThrottle;
import com.oyproj.common.RecommendKeys;
import com.oyproj.config.RecommendProperties;
import com.oyproj.dto.ArticleTagDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 推荐画像消费者：把行为事件增量写进用户/游客标签画像。
 *
 * <p>与热榜消费者同哲学：装饰功能，catch 一切异常照样 ack，绝不阻塞分区。
 * 游客画像与已读集合带 7 天 TTL（匿名身份自动消散）；登录用户画像不设 TTL。</p>
 *
 * <p>注意 consumer group 独立为 article-recommend-profile，与热榜组互不抢 offset。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileConsumer {

    private final CommonCache<Object> commonCache;
    private final ArticleTagDao articleTagDao;
    private final RecommendProperties recommendProperties;

    private final FaultLogThrottle recordFailureLog = new FaultLogThrottle();

    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_ARTICLE_BEHAVIOR,
            groupId = "${spring.kafka.consumer.profile-group-id:article-recommend-profile}")
    public void onEvent(ArticleBehaviorEvent event, Acknowledgment ack) {
        try {
            handle(event);
        } catch (Exception e) {
            if (recordFailureLog.allow()) {
                log.warn("维护推荐画像失败，跳过（本窗口已抑制 {} 条同类故障）: {}",
                        recordFailureLog.drainSuppressed(), event, e);
            }
        } finally {
            ack.acknowledge();
        }
    }

    private void handle(ArticleBehaviorEvent event) {
        if (event == null || event.getUserId() == null || event.getArticleId() == null) {
            return;
        }
        boolean guest = event.getUserId().startsWith(CachePrefix.GUEST_ID.getPrefix());
        String profileKey = guest
                ? RecommendKeys.profileGuest(event.getUserId())
                : RecommendKeys.profileUser(event.getUserId());
        List<String> tagIds = articleTagDao.listTagIdsByArticleIds(List.of(event.getArticleId()));
        for (String tagId : tagIds) {
            commonCache.incrementScore(profileKey, tagId, event.getWeight());
        }
        if (guest) {
            String consumedKey = RecommendKeys.consumedGuest(event.getUserId());
            commonCache.zAdd(consumedKey, 1L, event.getArticleId());
            commonCache.expire(profileKey, recommendProperties.getGuestTtlSeconds());
            commonCache.expire(consumedKey, recommendProperties.getGuestTtlSeconds());
        }
    }
}