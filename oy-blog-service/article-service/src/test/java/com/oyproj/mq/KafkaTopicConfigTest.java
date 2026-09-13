package com.oyproj.mq;

import com.oyproj.common.mq.config.KafkaTopicConfig;
import com.oyproj.common.mq.constants.ArticleBehaviorType;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
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

    @Test
    void indexTopicName_isArticleIndex() {
        assertEquals("article.index", KafkaTopicConfig.TOPIC_ARTICLE_INDEX);
    }

    @Test
    void indexTopic_isCompacted_andHasNoTimeRetention() {
        NewTopic topic = new KafkaTopicConfig().articleIndexTopic();

        assertEquals("article.index", topic.name());
        assertEquals(3, topic.numPartitions());
        assertEquals((short) 1, topic.replicationFactor());
        assertEquals("compact", topic.configs().get(TopicConfig.CLEANUP_POLICY_CONFIG));
        // 关键：绝不能设 retention.ms —— 设了会让"长期未更新的文章"整段被删，
        // 重放就重建不出它（spec §2.1）
        assertFalse(topic.configs().containsKey(TopicConfig.RETENTION_MS_CONFIG),
                "article.index 不得设置 retention.ms");
    }
}
