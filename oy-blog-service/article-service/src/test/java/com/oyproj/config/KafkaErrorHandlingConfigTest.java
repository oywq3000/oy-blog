package com.oyproj.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.DeserializationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 行为事件消费者的错误处理策略。
 *
 * <p>要钉住的性质只有一句：<b>反序列化失败的记录被就地放弃（跳过 + 记日志），
 * 不重试、不阻塞分区</b>。这是本设计对消费者链路的明确承诺，也是它和 ES 索引链
 * （重试 + 死信）分道扬镳的地方。</p>
 *
 * <p>观测点用 {@code DefaultErrorHandler.handleOne} 的返回值：
 * {@code true} = 记录已被就地处理（recoverer 消费掉、位点前进）；
 * {@code false} = 已排定下一次重试投递。用「Spring 默认配置」作对照组，
 * 证明这个观测点确实能区分两种走向 —— 否则测试即使写错也永远是绿的。</p>
 */
@DisplayName("KafkaErrorHandlingConfig 毒消息不重试策略")
class KafkaErrorHandlingConfigTest {

    private final KafkaErrorHandlingConfig config = new KafkaErrorHandlingConfig();

    @SuppressWarnings("unchecked")
    private final Consumer<Object, Object> consumer = mock(Consumer.class);
    private final MessageListenerContainer container = mock(MessageListenerContainer.class);

    private static ConsumerRecord<Object, Object> record(long offset) {
        return new ConsumerRecord<>("article.behavior", 0, offset, "A1", null);
    }

    /** ErrorHandlingDeserializer 产生的异常：DeserializationException 被包在 ListenerExecutionFailedException 里 */
    private static ListenerExecutionFailedException deserFailure() {
        DeserializationException cause = new DeserializationException(
                "Can't deserialize data [[1,2,3]] from topic [article.behavior]", new byte[]{1, 2, 3}, false,
                new RuntimeException("invalid json"));
        return new ListenerExecutionFailedException("Listener threw exception", cause);
    }

    /** 与反序列化无关的普通运行时异常 */
    private static ListenerExecutionFailedException otherFailure() {
        return new ListenerExecutionFailedException("Listener threw exception", new RuntimeException("redis down"));
    }

    @Test
    @DisplayName("反序列化失败：就地放弃（handleOne 返回 true = 已恢复，不进重试）")
    void deserializationFailure_isRecoveredInPlace_notRetried() {
        DefaultErrorHandler handler = config.articleBehaviorErrorHandler();

        assertTrue(handler.handleOne(deserFailure(), record(7L), consumer, container),
                "毒消息必须被就地放弃；返回 false 表示已排定重试 —— 那就违反了「不重试、不阻塞分区」的承诺");
    }

    @Test
    @DisplayName("零重试：连普通可重试异常也不排定重试（对照组能区分两种走向）")
    void noRetriesAtAll_contrastedWithSpringDefault() {
        // 对照组：Spring 默认配置（SeekUtils.DEFAULT_BACK_OFF = FixedBackOff(0, 9)）
        // 对同一个可重试异常会排定重试 → 返回 false。证明观测点有区分度。
        DefaultErrorHandler springDefault = new DefaultErrorHandler();
        assertFalse(springDefault.handleOne(otherFailure(), record(8L), consumer, container),
                "对照组应当排定重试（返回 false），否则本测试的观测点没有区分度");

        // 本服务的配置：零重试 —— 直接放弃
        DefaultErrorHandler handler = config.articleBehaviorErrorHandler();
        assertTrue(handler.handleOne(otherFailure(), record(9L), consumer, container),
                "热榜链路的策略是零重试：可重试异常也不该被排定重投");
    }

    @Test
    @DisplayName("DeserializationException 被显式登记为不可重试")
    void deserializationException_isExplicitlyClassifiedNotRetryable() {
        DefaultErrorHandler handler = config.articleBehaviorErrorHandler();

        // removeClassification 是 ExceptionClassifier 上唯一公开的分类读取口
        // （protected getClassifier() 拿不到）。读的是显式写入分类表里的值。
        assertEquals(Boolean.FALSE, handler.removeClassification(DeserializationException.class),
                "DeserializationException 必须显式判为不可重试，不能只依赖 Spring 的默认分类表");
    }

    @Test
    @DisplayName("不是 Spring 默认的那只 handler（必须真的被配置过）")
    void handlerIsOurConfiguredInstance() {
        DefaultErrorHandler handler = config.articleBehaviorErrorHandler();

        assertNotNull(handler);
        assertTrue(handler.isAckAfterHandle(), "处理完要让容器提交位点，否则重启后同一条毒消息会再来一遍");
    }
}
