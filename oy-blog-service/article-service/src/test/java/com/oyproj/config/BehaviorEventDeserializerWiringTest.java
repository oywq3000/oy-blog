package com.oyproj.config;

import com.oyproj.common.mq.domain.ArticleBehaviorEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用<b>真实的 application.yml</b> 验证行为事件的消费端反序列化装配。
 *
 * <p>为什么要有这条：毒消息不阻塞分区这个承诺，一半靠 {@link KafkaErrorHandlingConfig}
 * （错误处理器），另一半靠 yml 里把 {@code value-deserializer} 换成
 * {@code ErrorHandlingDeserializer} 并配好 delegate。后者全是字符串，写错一个 key
 * 编译器一无所知 —— 而 Spring 的 {@code DefaultErrorHandler.handleOtherException}
 * 明确拒绝处理 poll 期反序列化异常（直接抛 IllegalStateException → 容器吞日志后回到
 * 轮询循环 → 同一条毒消息被反复拉取、位点永不前进）。所以这里把 yml 读进来、
 * 走 Boot 的 {@code KafkaProperties} 绑定还原出消费者属性，再真的反序列化两条消息：
 * 合法 JSON 要能出对象，坏 JSON 必须变成 {@code null} + 反序列化异常头。</p>
 */
@DisplayName("application.yml 的行为事件反序列化装配")
class BehaviorEventDeserializerWiringTest {

    /** 按 Boot 的方式把真实 application.yml 绑成 KafkaProperties */
    private static Map<String, Object> consumerPropertiesFromRealYaml() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
        KafkaProperties properties = binder.bind("spring.kafka", Bindable.of(KafkaProperties.class)).get();
        return properties.buildConsumerProperties();
    }

    @Test
    @DisplayName("yml 里的 value 反序列化器是 ErrorHandlingDeserializer，delegate 是 JsonDeserializer")
    void valueDeserializerIsErrorHandlingWrapperAroundJson() throws Exception {
        Map<String, Object> props = consumerPropertiesFromRealYaml();

        Object valueDeserializer = props.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG);
        assertEquals(ErrorHandlingDeserializer.class.getName(),
                valueDeserializer instanceof Class<?> c ? c.getName() : String.valueOf(valueDeserializer),
                "value-deserializer 必须包一层 ErrorHandlingDeserializer");
        assertEquals(JsonDeserializer.class.getName(),
                props.get(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS),
                "delegate 必须是 JsonDeserializer（键名取自 ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS）");
    }

    @Test
    @DisplayName("合法 JSON → 反序列化出 ArticleBehaviorEvent")
    void validPayload_deserializes() throws Exception {
        ErrorHandlingDeserializer<?> deserializer = configuredDeserializer();

        byte[] payload = ("{\"eventId\":\"e-1\",\"eventType\":\"VIEW\",\"articleId\":\"A1\","
                + "\"userId\":\"U1\",\"occurredAt\":\"2026-09-12T20:15:30+08:00\",\"weight\":1}")
                .getBytes(StandardCharsets.UTF_8);

        Object event = deserializer.deserialize("article.behavior", new RecordHeaders(), payload);

        assertInstanceOf(ArticleBehaviorEvent.class, event);
        assertEquals("A1", ((ArticleBehaviorEvent) event).getArticleId());
    }

    @Test
    @DisplayName("坏 JSON → 返回 null 并在头上留 DeserializationException（而不是从 poll() 抛出）")
    void poisonPayload_becomesNullWithDeserializationExceptionHeader() throws Exception {
        ErrorHandlingDeserializer<?> deserializer = configuredDeserializer();
        Headers headers = new RecordHeaders();

        Object event = deserializer.deserialize("article.behavior", headers,
                "this is not json at all".getBytes(StandardCharsets.UTF_8));

        assertNull(event, "毒消息必须变成 null 值记录，而不是让 poll() 抛异常");
        assertNotNull(headers.lastHeader("springDeserializerExceptionValue"),
                "必须带上 springDeserializerExceptionValue 头 —— 容器靠它把记录送进错误处理器");
    }

    /** 还原消费者实际会拿到的那个 deserializer 实例 */
    private static ErrorHandlingDeserializer<?> configuredDeserializer() throws Exception {
        ErrorHandlingDeserializer<?> deserializer = new ErrorHandlingDeserializer<>();
        deserializer.configure(consumerPropertiesFromRealYaml(), false);
        return deserializer;
    }
}
