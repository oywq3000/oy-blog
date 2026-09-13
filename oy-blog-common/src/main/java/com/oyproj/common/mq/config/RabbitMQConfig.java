package com.oyproj.common.mq.config;

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
        return template;
    }
}
