package com.oyproj.mq;

import com.oyproj.common.mq.config.KafkaTopicConfig;
import com.oyproj.common.mq.constants.ArticleBehaviorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kafka topic 常量与行为事件类型")
class KafkaTopicConfigTest {

    @Test
    void topicName_isArticleBehavior() {
        assertEquals("article.behavior", KafkaTopicConfig.TOPIC_ARTICLE_BEHAVIOR);
    }

    @Test
    void behaviorType_hasSixValues() {
        assertEquals(6, ArticleBehaviorType.values().length);
        assertNotNull(ArticleBehaviorType.valueOf("VIEW"));
        assertNotNull(ArticleBehaviorType.valueOf("UNLIKE"));
        assertNotNull(ArticleBehaviorType.valueOf("UNFAVORITE"));
    }
}
