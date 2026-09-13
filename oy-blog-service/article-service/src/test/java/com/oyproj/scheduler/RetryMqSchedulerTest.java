package com.oyproj.scheduler;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.domain.entity.MqRetryLog;
import com.oyproj.mapper.MqRetryLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetryMqScheduler 迁 Kafka 后")
class RetryMqSchedulerTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    @Mock private MqRetryLogMapper retryLogMapper;

    private RetryMqScheduler scheduler;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 纯 Mockito 单测没有 Spring/MyBatis 扫描，MyBatis-Plus 的 lambda 列缓存是空的：
        // 生产代码里的 LambdaQueryWrapper.eq(MqRetryLog::getStatus, ...) 会抛
        // "can not find lambda cache for this entity"。这里显式注册表信息，
        // 让本测试类不依赖其它 @SpringBootTest 用例先跑（单独 -Dtest= 也能过）。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), MqRetryLog.class);
    }

    @BeforeEach
    void setUp() {
        // findAndRegisterModules()：ArticleIndexMessage 带 LocalDateTime 字段，
        // 裸 ObjectMapper 反序列化真实落库消息体会抛异常（重试会被静默记成失败）
        scheduler = new RetryMqScheduler(retryLogMapper, kafkaTemplate,
                new ObjectMapper().findAndRegisterModules());
    }

    private MqRetryLog pending(String id, String type, String body) {
        MqRetryLog log = new MqRetryLog();
        log.setId(Long.parseLong(id));
        log.setMessageType(type);
        log.setMessageBody(body);
        log.setRetryCount(0);
        log.setStatus("PENDING");
        return log;
    }

    @Test
    void indexRetry_resendsToKafkaTopic() {
        when(retryLogMapper.selectList(any())).thenReturn(List.of(
                pending("1", "ARTICLE_INDEX",
                        "{\"operation\":\"CREATE\",\"articleId\":\"A1\"}")));

        scheduler.retryFailedMessages();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("article.index"), eq("A1"), payload.capture());
        // 非删除操作必须发完整消息体，而不是 tombstone
        assertInstanceOf(ArticleIndexMessage.class, payload.getValue());
        assertEquals(MQOperation.CREATE, ((ArticleIndexMessage) payload.getValue()).getOperation());
    }

    @Test
    void deleteRetry_resendsAsTombstone_notAsBody() {
        when(retryLogMapper.selectList(any())).thenReturn(List.of(
                pending("2", "ARTICLE_DELETE",
                        "{\"operation\":\"DELETE\",\"articleId\":\"A1\","
                                + "\"operationTime\":\"2026-09-13T10:00:00\"}")));

        scheduler.retryFailedMessages();

        // 关键：落库时存的是带 operation=DELETE 的完整消息（因为 message_body 存不了 null），
        // 重发时必须转回 null —— 漏了这一步，重试路径就会漏发 tombstone，
        // 结果"文章删了但 ES 里还在"
        verify(kafkaTemplate).send(eq("article.index"), eq("A1"), isNull());
    }

    @Test
    void deleteRetry_byMessageType_whenOperationMissingFromBody() {
        // 防御：body 里的 operation 若缺失/为 null，靠 messageType 兜底判断
        when(retryLogMapper.selectList(any())).thenReturn(List.of(
                pending("3", "ARTICLE_DELETE", "{\"articleId\":\"A1\"}")));

        scheduler.retryFailedMessages();

        verify(kafkaTemplate).send(eq("article.index"), eq("A1"), isNull());
    }

    @Test
    @DisplayName("messageType 是 ARTICLE_INDEX、但 body.operation=DELETE → 仍发 tombstone")
    void deleteRetry_byBodyOperation_whenMessageTypeIsNotDelete() {
        // 这条钉的是 isDelete 那个 || 的**第二半**：所有"messageType=ARTICLE_DELETE"的用例
        // 都会被第一半短路掉，只有这一条能让 MQOperation.DELETE.equals(...) 成为决定性操作数。
        // 场景真实存在：messageType 是落库时的标签，body 才是权威内容；标签写错/漏写时
        // 若这半句失效，重试会把"删除"当成"索引"发出去 → 文档被复活（且没有任何报错）。
        when(retryLogMapper.selectList(any())).thenReturn(List.of(
                pending("5", "ARTICLE_INDEX",
                        "{\"operation\":\"DELETE\",\"articleId\":\"A1\","
                                + "\"operationTime\":\"2026-09-13T10:00:00\"}")));

        scheduler.retryFailedMessages();

        verify(kafkaTemplate).send(eq("article.index"), eq("A1"), isNull());
    }

    @Test
    void sendFailure_incrementsRetryCount() {
        when(retryLogMapper.selectList(any())).thenReturn(List.of(
                pending("4", "ARTICLE_INDEX",
                        "{\"operation\":\"CREATE\",\"articleId\":\"A1\"}")));
        when(kafkaTemplate.send(any(), any(), any())).thenThrow(new RuntimeException("broker down"));

        scheduler.retryFailedMessages();

        verify(retryLogMapper, atLeastOnce()).update(isNull(), any());
    }
}
