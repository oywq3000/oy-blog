package com.oyproj.consumer;

import com.oyproj.Repository.ArticleSearchRepository;
import com.oyproj.common.mq.constants.ArticleMQConstant;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.domain.entity.ArticleDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 文章索引消费者服务
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
        } catch (Exception e) {
            log.error("处理文章索引消息失败，文章ID: {}, 错误: {}", message.getArticleId(), e.getMessage());
            // 这里可以添加重试机制或死信队列处理
            //todo 添加死信队列
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
        } catch (Exception e) {
            log.error("处理文章删除消息失败，文章ID: {}, 错误: {}", message.getArticleId(), e.getMessage());
        }
    }

    /**
     * 索引文章到ES
     */
    private void indexArticle(ArticleIndexMessage message) {
        ArticleDocument document = convertToDocument(message);
        articleSearchRepository.save(document);
        log.info("文章索引成功，文章ID: {}", document.getId());
    }

    /**
     * 从ES删除文章索引
     */
    private void deleteArticleIndex(String articleId) {
        try {
            articleSearchRepository.deleteById(articleId);
            log.info("文章索引删除成功，文章ID: {}", articleId);
        } catch (Exception e) {
            log.error("文章索引删除失败，文章ID: {}, 错误: {}", articleId, e.getMessage());
        }
    }

    /**
     * 将消息转换为ES文档
     */
    private ArticleDocument convertToDocument(ArticleIndexMessage message) {
        ArticleDocument document = new ArticleDocument();
        document.setId(message.getArticleId());
        document.setTitle(message.getTitle());
        document.setSummary(message.getSummary());
        document.setAuthor(message.getAuthor());
        document.setAuthorId(message.getAuthorId());
        document.setCreatedAt(message.getCreatedAt());
        document.setUpdatedAt(message.getUpdatedAt());
        document.setStatus(message.getStatus());
        document.setTags(message.getTags());
        document.setCategory(message.getCategory());
        // 设置默认值
        document.setViewCount(0);
        document.setLikeCount(0);
        document.setCommentCount(0);
        return document;
    }
}