package com.oyproj.config;

import com.oyproj.common.mq.constants.ArticleMQConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文章审核 MQ 拓扑声明：
 * article.moderation.exchange ──► article.moderation.queue（消费队列，@RabbitListener 订阅）
 * article.moderation.retry.exchange ──► article.moderation.retry.queue（无消费者）
 *   └─ 重试队列 DLX 指回主 exchange：重试消息带逐条 TTL 投进 retry 队列，
 *      TTL 到期死信自动回主 exchange 重新投递主队列。
 * 注意必须有独立的 retry exchange：若重试消息发往主 exchange，主队列会立即消费，延迟失效。
 */
@Configuration
public class ModerationRabbitConfig {

    @Bean
    public DirectExchange moderationExchange() {
        return new DirectExchange(ArticleMQConstant.ARTICLE_MODERATION_EXCHANGE, true, false);
    }

    @Bean
    public Queue moderationQueue() {
        return QueueBuilder.durable(ArticleMQConstant.ARTICLE_MODERATION_QUEUE).build();
    }

    @Bean
    public DirectExchange moderationRetryExchange() {
        return new DirectExchange(ArticleMQConstant.ARTICLE_MODERATION_RETRY_EXCHANGE, true, false);
    }

    @Bean
    public Queue moderationRetryQueue() {
        return QueueBuilder.durable(ArticleMQConstant.ARTICLE_MODERATION_RETRY_QUEUE)
                .deadLetterExchange(ArticleMQConstant.ARTICLE_MODERATION_EXCHANGE)
                .deadLetterRoutingKey(ArticleMQConstant.ARTICLE_MODERATION_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding moderationBinding(Queue moderationQueue, DirectExchange moderationExchange) {
        return BindingBuilder.bind(moderationQueue)
                .to(moderationExchange)
                .with(ArticleMQConstant.ARTICLE_MODERATION_ROUTING_KEY);
    }

    @Bean
    public Binding moderationRetryBinding(Queue moderationRetryQueue, DirectExchange moderationRetryExchange) {
        return BindingBuilder.bind(moderationRetryQueue)
                .to(moderationRetryExchange)
                .with(ArticleMQConstant.ARTICLE_MODERATION_ROUTING_KEY);
    }
}
