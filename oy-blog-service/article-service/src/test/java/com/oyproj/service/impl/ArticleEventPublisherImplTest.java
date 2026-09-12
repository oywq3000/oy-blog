package com.oyproj.service.impl;

import com.oyproj.common.mq.constants.ArticleBehaviorType;
import com.oyproj.common.mq.domain.ArticleBehaviorEvent;
import com.oyproj.service.ArticleEventWeights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ArticleEventPublisher 发布器")
class ArticleEventPublisherImplTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final ArticleEventWeights weights = mock(ArticleEventWeights.class);

    private ArticleEventPublisherImpl publisher;

    @BeforeEach
    void setUp() {
        lenient().when(weights.weightOf(any())).thenReturn(1);
        publisher = new ArticleEventPublisherImpl(kafkaTemplate, weights);
    }

    @Test
    void publishLike_sendsEventWithArticleIdAsKey() {
        when(weights.weightOf(ArticleBehaviorType.LIKE)).thenReturn(2);

        publisher.publishLike("A1", "U9", false);

        ArgumentCaptor<ArticleBehaviorEvent> captor = ArgumentCaptor.forClass(ArticleBehaviorEvent.class);
        verify(kafkaTemplate).send(eq("article.behavior"), eq("A1"), captor.capture());

        ArticleBehaviorEvent event = captor.getValue();
        assertEquals(ArticleBehaviorType.LIKE, event.getEventType());
        assertEquals("A1", event.getArticleId());
        assertEquals("U9", event.getUserId());
        assertEquals(2, event.getWeight());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void publishLike_cancelled_sendsUnlikeWithPositiveWeightFromResolver() {
        when(weights.weightOf(ArticleBehaviorType.UNLIKE)).thenReturn(-2);

        publisher.publishLike("A1", "U9", true);

        ArgumentCaptor<ArticleBehaviorEvent> captor = ArgumentCaptor.forClass(ArticleBehaviorEvent.class);
        verify(kafkaTemplate).send(any(), any(), captor.capture());
        assertEquals(ArticleBehaviorType.UNLIKE, captor.getValue().getEventType());
        assertEquals(-2, captor.getValue().getWeight());
    }

    @Test
    void publishFavorite_cancelled_sendsUnfavorite() {
        publisher.publishFavorite("A1", "U9", true);

        ArgumentCaptor<ArticleBehaviorEvent> captor = ArgumentCaptor.forClass(ArticleBehaviorEvent.class);
        verify(kafkaTemplate).send(any(), any(), captor.capture());
        assertEquals(ArticleBehaviorType.UNFAVORITE, captor.getValue().getEventType());
    }

    @Test
    void publishView_allowsNullUserId() {
        publisher.publishView("A1", null);

        ArgumentCaptor<ArticleBehaviorEvent> captor = ArgumentCaptor.forClass(ArticleBehaviorEvent.class);
        verify(kafkaTemplate).send(any(), any(), captor.capture());
        assertEquals(ArticleBehaviorType.VIEW, captor.getValue().getEventType());
        assertNull(captor.getValue().getUserId());
    }

    @Test
    void kafkaFailure_isSwallowed_doesNotThrow() {
        when(kafkaTemplate.send(any(), any(), any()))
                .thenThrow(new RuntimeException("broker down"));

        assertDoesNotThrow(() -> publisher.publishView("A1", "U9"));
    }

    @Test
    void blankArticleId_isIgnored_noSend() {
        publisher.publishView("", "U9");
        publisher.publishView(null, "U9");

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void occurredAt_isIsoOffsetDateTime() {
        publisher.publishView("A1", "U9");

        ArgumentCaptor<ArticleBehaviorEvent> captor = ArgumentCaptor.forClass(ArticleBehaviorEvent.class);
        verify(kafkaTemplate).send(any(), any(), captor.capture());

        // 必须能被 OffsetDateTime.parse 解析，否则消费端日桶归日会炸
        assertNotNull(java.time.OffsetDateTime.parse(captor.getValue().getOccurredAt()));
    }
}
