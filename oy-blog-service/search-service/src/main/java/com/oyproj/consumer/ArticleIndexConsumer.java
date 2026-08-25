package com.oyproj.consumer;

import com.oyproj.Repository.ArticleSearchRepository;
import com.oyproj.common.mq.constants.ArticleMQConstant;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.converter.ArticleDocumentConverter;
import com.oyproj.domain.entity.ArticleDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 文章索引消费者服务
 * 消费失败的消息通过 RabbitMQ DLX 机制进入死信队列，不会丢失。
 */
@Slf4j
@Service
public class ArticleIndexConsumer {

    @Autowired
    private ArticleSearchRepository articleSearchRepository;

    /**
     * 处理文章索引消息
     */
    @RabbitListener(queues = ArticleMQConstant.ARTICLE_INDEX_QUEUE)
    public void handleArticleIndex(ArticleIndexMessage message) {
        try {
            log.info("收到文章索引消息，文章ID: {}, 操作类型: {}", message.getArticleId(), message.getOperation());
            switch (message.getOperation()) {
                case CREATE:
                case UPDATE:
                    indexArticle(message);
                    break;
                case DELETE:
                    deleteArticleIndex(message.getArticleId());
                    break;
                default:
                    log.warn("未知的操作类型: {}", message.getOperation());
            }
        } catch (AmqpRejectAndDontRequeueException e) {
            throw e; // 直接抛出，让消息进入 DLQ
        } catch (Exception e) {
            log.error("处理文章索引消息失败，文章ID: {}, 错误: {}", message.getArticleId(), e.getMessage(), e);
            throw new AmqpRejectAndDontRequeueException("文章索引失败，进入死信队列", e);
        }
    }

    /**
     * 处理文章删除消息
     */
    @RabbitListener(queues = ArticleMQConstant.ARTICLE_DELETE_QUEUE)
    public void handleArticleDelete(ArticleIndexMessage message) {
        try {
            log.info("收到文章删除消息，文章ID: {}", message.getArticleId());
            deleteArticleIndex(message.getArticleId());
        } catch (AmqpRejectAndDontRequeueException e) {
            throw e;
        } catch (Exception e) {
            log.error("处理文章删除消息失败，文章ID: {}, 错误: {}", message.getArticleId(), e.getMessage(), e);
            throw new AmqpRejectAndDontRequeueException("文章删除索引失败，进入死信队列", e);
        }
    }

    /**
     * 死信队列监听器 — 记录失败消息，便于排查
     */
    @RabbitListener(queues = ArticleMQConstant.ARTICLE_INDEX_DLQ)
    public void handleDeadLetter(ArticleIndexMessage message) {
        log.error("进入死信队列的消息，文章ID: {}, 操作类型: {}, 操作时间: {}",
                message.getArticleId(), message.getOperation(), message.getOperationTime());
    }

    /**
     * 索引文章到ES
     */
    private void indexArticle(ArticleIndexMessage message) {
        ArticleDocument document = ArticleDocumentConverter.toDocument(message);
        articleSearchRepository.save(document);
        log.info("文章索引成功，文章ID: {}", document.getId());
    }

    /**
     * 从ES删除文章索引
     */
    private void deleteArticleIndex(String articleId) {
        articleSearchRepository.deleteById(articleId);
        log.info("文章索引删除成功，文章ID: {}", articleId);
    }
}