package com.oyproj.consumer;

import com.oyproj.common.mq.constants.ArticleMQConstant;
import com.oyproj.common.mq.domain.ArticleModerationMessage;
import com.oyproj.service.ArticleModerationWorkflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 文章 AI 审核消费者：MQ 入口薄壳。
 * 事务边界与三态流转都在 ArticleModerationWorkflow（两段短事务，AI 调用严格在事务外）；
 * 本类只负责解析消息与兜底吞异常——不抛出 → 不触发 RabbitMQ 无限 requeue，重试由 RetrySender/兜底扫描负责。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleModerationConsumer {

    private static final String ATTEMPT_HEADER = "x-attempt";

    private final ArticleModerationWorkflow moderationWorkflow;

    @RabbitListener(queues = ArticleMQConstant.ARTICLE_MODERATION_QUEUE)
    public void onMessage(ArticleModerationMessage body, @Header(name = ATTEMPT_HEADER, required = false) Integer attempt) {
        // 消息体由默认 Jackson converter 解析；为兼容未带头的首投，attempt 缺省 0。
        // 千万不要在这里加 @Transactional：那会重新变成"长事务包住 AI 调用"——本次重构要消除的形态。
        String articleId = body.getArticleId();
        try {
            moderationWorkflow.process(articleId, attempt == null ? 0 : attempt);
        } catch (Exception e) {
            // DB 异常等一律吞掉（不抛 → 不无限 requeue）。
            // 注意不做补偿写：状态可能已被 apply* 改过，重走转人工会污染结果；
            // 由兜底扫描对"仍卡在审核中"的文章收尾。
            log.error("审核消费处理异常, articleId: {}, 错误: {}", articleId, e.getMessage(), e);
        }
    }
}
