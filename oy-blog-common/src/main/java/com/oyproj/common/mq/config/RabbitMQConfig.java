package com.oyproj.common.mq.config;
import org.springframework.amqp.core.*;
import com.oyproj.common.mq.constants.ArticleMQConstant;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;




/**
 * RabbitMQ配置类
 */
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitMQConfig {
    /**
     * 文章索引交换机（直连交换机）
     */
    @Bean
    public DirectExchange articleIndexExchange() {
        return new DirectExchange(ArticleMQConstant.ARTICLE_INDEX_EXCHANGE, true, false);
    }
    
    /**
     * 文章索引队列
     */
    @Bean
    public Queue articleIndexQueue() {
        return new Queue(ArticleMQConstant.ARTICLE_INDEX_QUEUE, true, false, false);
    }
    
    /**
     * 文章删除队列
     */
    @Bean
    public Queue articleDeleteQueue() {
        return new Queue(ArticleMQConstant.ARTICLE_DELETE_QUEUE, true, false, false);
    }
    
    /**
     * 绑定索引队列到交换机
     */
    @Bean
    public Binding bindingArticleIndexQueue() {
        return BindingBuilder.bind(articleIndexQueue())
                .to(articleIndexExchange())
                .with(ArticleMQConstant.ARTICLE_INDEX_ROUTING_KEY);
    }
    
    /**
     * 绑定删除队列到交换机
     */
    @Bean
    public Binding bindingArticleDeleteQueue() {
        return BindingBuilder.bind(articleDeleteQueue())
                .to(articleIndexExchange())
                .with(ArticleMQConstant.ARTICLE_DELETE_ROUTING_KEY);
    }
    
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
        // 开启确认模式
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                System.err.println("消息发送失败: " + cause);
            }
        });
        return template;
    }
}