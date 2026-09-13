package com.oyproj.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.domain.entity.MqRetryLog;
import com.oyproj.mapper.MqRetryLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleMessageProducer 迁 Kafka 后")
class ArticleMessageProducerImplTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    @Mock private MqRetryLogMapper retryLogMapper;

    private ArticleMessageProducerImpl producer;

    @BeforeEach
    void setUp() {
        // findAndRegisterModules()：ArticleIndexMessage 含 LocalDateTime 字段，
        // 裸 ObjectMapper 序列化会抛 InvalidDefinitionException（缺 JavaTimeModule），
        // 被 saveRetryLog 吞掉后 insert 永不发生。生产注入的是 Spring Boot 自动配置的
        // ObjectMapper（已注册 JavaTimeModule），这里对齐它。
        producer = new ArticleMessageProducerImpl(kafkaTemplate, retryLogMapper,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void indexMessage_usesArticleIdAsKey() {
        ArticleIndexMessage msg = new ArticleIndexMessage();
        msg.setOperation(MQOperation.CREATE);
        msg.setArticleId("A1");

        producer.sendArticleIndexMessage(msg);

        // 分区键必须是 articleId：既保证同篇保序，也是压实主题的 key
        verify(kafkaTemplate).send(eq("article.index"), eq("A1"), eq(msg));
    }

    @Test
    void deleteMessage_sendsTombstone_nullValue() {
        producer.sendArticleDeleteMessage("A1");

        // 删除必须发 null（tombstone），否则压实永远清不掉这个 key
        verify(kafkaTemplate).send(eq("article.index"), eq("A1"), isNull());
        verify(retryLogMapper, never()).insert(any(MqRetryLog.class));
    }

    @Test
    void indexSendFailure_writesRetryLog_stillHoldsFullMessage() {
        when(kafkaTemplate.send(any(), any(), any())).thenThrow(new RuntimeException("broker down"));
        ArticleIndexMessage msg = new ArticleIndexMessage();
        msg.setOperation(MQOperation.CREATE);
        msg.setArticleId("A1");

        assertDoesNotThrow(() -> producer.sendArticleIndexMessage(msg));

        verify(retryLogMapper).insert(any(MqRetryLog.class));
    }

    @Test
    void deleteSendFailure_writesRetryLog_withDeleteOperationNotNullBody() {
        when(kafkaTemplate.send(any(), any(), any())).thenThrow(new RuntimeException("broker down"));

        assertDoesNotThrow(() -> producer.sendArticleDeleteMessage("A1"));

        // message_body 存不了 null，所以落库的是带 operation=DELETE 的完整消息；
        // 由 RetryMqScheduler 重发时再转回 null（Task 3）
        ArgumentCaptor<com.oyproj.domain.entity.MqRetryLog> captor =
                ArgumentCaptor.forClass(com.oyproj.domain.entity.MqRetryLog.class);
        verify(retryLogMapper).insert(captor.capture());
        assertEquals("ARTICLE_DELETE", captor.getValue().getMessageType());
        assertTrue(captor.getValue().getMessageBody().contains("DELETE"));
    }
}
