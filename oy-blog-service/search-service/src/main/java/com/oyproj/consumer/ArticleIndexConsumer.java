package com.oyproj.consumer;

import com.oyproj.Repository.ArticleSearchRepository;
import com.oyproj.common.mq.config.KafkaTopicConfig;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.converter.ArticleDocumentConverter;
import com.oyproj.domain.entity.ArticleDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

/**
 * 文章索引消费者（Kafka/Redpanda）。
 *
 * <p><b>失败必须重试</b>：ES 索引错了用户直接搜到，与热榜链路"吞异常照样 ack"的取舍相反。
 * 重试交给 {@link RetryableTopic}——它自动建 retry topic 与 DLT，替代原先手工搭的
 * 退避重试 + {@code article.index.dlq}；重试耗尽后记录到 DLT（原 {@code handleDeadLetter} 的等价物）。</p>
 *
 * <p><b>反序列化失败同样受保护</b>：{@code application.yml} 里 value 用
 * {@code ErrorHandlingDeserializer} 包住 {@code JsonDeserializer}，毒消息被转成记录级异常
 * 并同样走重试→DLT 通道。缺了它，毒消息会在 {@code poll()} 阶段反复重抛、位点永不前进、
 * 分区被永久卡死（方案 A 已在另一条链路上用字节码验证过这个失败模式）。</p>
 *
 * <p><b>因此本模块刻意不声明全局 {@code CommonErrorHandler} bean</b>——那会覆盖
 * {@code @RetryableTopic} 的错误处理，把重试静默关掉。</p>
 */
@Slf4j
@Service
public class ArticleIndexConsumer {

    @Autowired
    private ArticleSearchRepository articleSearchRepository;

    /**
     * 处理文章索引消息。
     *
     * <p>用 {@link ConsumerRecord} 而非裸消息体：<b>tombstone 没有 body</b>，
     * 文章 id 在消息 KEY 上（分区键 = articleId，同时也是压实主题的 key）。</p>
     */
    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_ARTICLE_INDEX,
            groupId = "${spring.kafka.consumer.group-id:article-index}")
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleArticleIndex(ConsumerRecord<String, ArticleIndexMessage> record) {
        String keyArticleId = record.key();
        ArticleIndexMessage message = record.value();

        if (message == null) {
            // tombstone：文章已删除。没有 body，id 只能来自 key。
            log.info("收到索引 tombstone，文章ID: {}", keyArticleId);
            deleteArticleIndex(keyArticleId);
            return;
        }

        log.info("收到文章索引消息，文章ID: {}, 操作类型: {}", message.getArticleId(), message.getOperation());
        switch (message.getOperation()) {
            case CREATE:
            case UPDATE:
                indexArticle(message);
                break;
            case DELETE:
                // 兼容：迁移期/异常路径下仍可能收到带内容的 DELETE 消息
                deleteArticleIndex(keyArticleId != null ? keyArticleId : message.getArticleId());
                break;
            default:
                log.warn("未知的操作类型: {}", message.getOperation());
        }
    }

    /**
     * 死信处理 —— 重试耗尽后记录，便于排查（原 RabbitMQ DLQ 监听器的等价物）
     */
    @DltHandler
    public void handleDlt(ConsumerRecord<String, ArticleIndexMessage> record) {
        log.error("索引消息进入死信主题（重试耗尽），文章ID: {}, partition: {}, offset: {}",
                record.key(), record.partition(), record.offset());
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
