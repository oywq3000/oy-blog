package com.oyproj.consumer;

import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.mq.constants.ArticleBehaviorType;
import com.oyproj.common.mq.domain.ArticleBehaviorEvent;
import com.oyproj.common.service.CommonCache;
import com.oyproj.config.RecommendProperties;
import com.oyproj.dto.ArticleTagDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileConsumer 在线画像")
class ProfileConsumerTest {

    @Mock private CommonCache<Object> commonCache;
    @Mock private ArticleTagDao articleTagDao;
    @Mock private Acknowledgment ack;
    private ProfileConsumer consumer;

    @BeforeEach
    void setUp() {
        RecommendProperties props = new RecommendProperties();
        consumer = new ProfileConsumer(commonCache, articleTagDao, props);
    }

    private ArticleBehaviorEvent event(ArticleBehaviorType type, String userId, String articleId, int weight) {
        return ArticleBehaviorEvent.builder()
                .eventType(type).userId(userId).articleId(articleId).weight(weight).build();
    }

    @Test
    void logonView_incrementsEachTagOnProfileUser() {
        when(articleTagDao.listTagIdsByArticleIds(List.of("a1")))
                .thenReturn(List.of("t1", "t2"));
        consumer.onEvent(event(ArticleBehaviorType.VIEW, "u1", "a1", 1), ack);
        verify(commonCache).incrementScore("rec:profile:user:u1", "t1", 1);
        verify(commonCache).incrementScore("rec:profile:user:u1", "t2", 1);
        verify(commonCache, never()).zAdd(anyString(), anyLong(), anyString());
        verify(ack).acknowledge();
    }

    @Test
    void unlike_negativeWeightDecrements() {
        when(articleTagDao.listTagIdsByArticleIds(List.of("a1"))).thenReturn(List.of("t1"));
        consumer.onEvent(event(ArticleBehaviorType.UNLIKE, "u1", "a1", -2), ack);
        verify(commonCache).incrementScore("rec:profile:user:u1", "t1", -2);
    }

    @Test
    void guest_event_writesGuestProfileAndConsumedWithTtl() {
        String gid = CachePrefix.GUEST_ID.getPrefix() + "abc";
        when(articleTagDao.listTagIdsByArticleIds(List.of("a9"))).thenReturn(List.of("t7"));
        consumer.onEvent(event(ArticleBehaviorType.VIEW, gid, "a9", 1), ack);
        verify(commonCache).incrementScore("rec:profile:guest:" + gid, "t7", 1);
        verify(commonCache).zAdd("rec:consumed:guest:" + gid, 1L, "a9");
        RecommendProperties props = new RecommendProperties();
        verify(commonCache).expire("rec:profile:guest:" + gid, props.getGuestTtlSeconds());
        verify(commonCache).expire("rec:consumed:guest:" + gid, props.getGuestTtlSeconds());
    }

    @Test
    void nullEvent_orDaoFailure_alwaysAcksWithoutThrow() {
        consumer.onEvent(null, ack);
        verify(ack).acknowledge();
        reset(ack, commonCache);
        when(articleTagDao.listTagIdsByArticleIds(List.of("a1"))).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> consumer.onEvent(event(ArticleBehaviorType.VIEW, "u1", "a1", 1), ack));
        verify(ack).acknowledge();
    }
}