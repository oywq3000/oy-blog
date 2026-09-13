package com.oyproj.common.mq.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 基础配置类
 *
 * <p><b>本类现只服务审核链路。</b>索引链路已迁至 Kafka 压实主题 {@code article.index}
 * （见 {@code KafkaTopicConfig}），原先在此声明的索引/删除队列、交换机、死信拓扑与发布确认回调
 * 已一并删除。</p>
 *
 * <p>保留的两个 bean 是审核链路的依赖（{@code ArticleModerationProducerImpl}、
 * {@code ModerationRetrySenderImpl} 均注入 {@link RabbitTemplate}）：
 * {@link #jsonMessageConverter()} 与 {@link #rabbitTemplate(ConnectionFactory)}。
 * 审核链路的队列/交换机拓扑声明在 article-service 的 {@code ModerationRabbitConfig}，不在本类。</p>
 */
@Slf4j
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitMQConfig {

    /**
     * JSON消息转换器
     */
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 配置RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        // 发布方确认与退回处理：都走 logback（stderr 的 println 进不了 ELK 管道）。
        // 该模板现在只被审核链路使用（索引链路已迁 Kafka），故这是审核消息的 nack/退回信号。
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.warn("RabbitMQ 消息未被 broker 确认（nack）: {}", cause);
            }
        });
        template.setReturnsCallback(returned -> log.warn(
                "RabbitMQ 消息被退回（不可路由）: exchange={}, routingKey={}, replyText={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return template;
    }
}
