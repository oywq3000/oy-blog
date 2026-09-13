package com.oyproj.consumer;

import com.oyproj.Repository.ArticleSearchRepository;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.domain.entity.ArticleDocument;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleIndexConsumer 迁 Kafka 后")
class ArticleIndexConsumerTest {

    @Mock private ArticleSearchRepository articleSearchRepository;

    private ArticleIndexConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ArticleIndexConsumer();
        ReflectionTestUtils.setField(consumer, "articleSearchRepository", articleSearchRepository);
    }

    private ConsumerRecord<String, ArticleIndexMessage> record(
            String key, ArticleIndexMessage value) {
        return new ConsumerRecord<>("article.index", 0, 0L, key, value);
    }

    @Test
    void tombstone_deletesFromEs_usingMessageKey() {
        // tombstone 没有 body，文章 id 只能来自消息 KEY
        consumer.handleArticleIndex(record("A1", null));

        verify(articleSearchRepository).deleteById("A1");
    }

    @Test
    void createMessage_indexesToEs() {
        ArticleIndexMessage msg = new ArticleIndexMessage();
        msg.setOperation(MQOperation.CREATE);
        msg.setArticleId("A1");

        consumer.handleArticleIndex(record("A1", msg));

        verify(articleSearchRepository).save(any());
        verify(articleSearchRepository, never()).deleteById(any());
    }

    @Test
    void deleteOperationMessage_stillDeletesFromEs() {
        // 兼容路径：迁移期/异常情况下仍可能收到带内容的 DELETE 消息
        ArticleIndexMessage msg = new ArticleIndexMessage();
        msg.setOperation(MQOperation.DELETE);
        msg.setArticleId("A1");

        consumer.handleArticleIndex(record("A1", msg));

        verify(articleSearchRepository).deleteById("A1");
    }

    @Test
    @DisplayName("key 与 body.articleId 不一致 → 抛错（绝不静默删错/复活文档）")
    void keyBodyMismatch_throwsInsteadOfSilentlyMisindexing() {
        // 生产实测过这种记录：key = poison-b-verify，body.articleId = 另一篇文章。
        // 若不拦，结论取决于走哪个分支——CREATE 会按 body 建出别的文档、
        // tombstone 会按 key 删掉不相干的 id，两种都静默。
        ArticleIndexMessage msg = new ArticleIndexMessage();
        msg.setOperation(MQOperation.CREATE);
        msg.setArticleId("B2");

        IllegalStateException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> consumer.handleArticleIndex(record("A1", msg)),
                "key/body 不一致必须抛出（走 @RetryableTopic 重试→DLT），而不是被静默处理");

        assertEquals("key/body articleId mismatch", ex.getMessage());
        // 关键：既没有按 body 索引，也没有按 key 删除 —— 记录被整体拒绝
        verifyNoInteractions(articleSearchRepository);
    }

    @Test
    void esFailure_propagates_soRetryableTopicCanRetry() {
        // 关键：索引失败必须抛出，不能吞掉 —— 索引链路靠重试，与热榜链路相反
        ArticleIndexMessage msg = new ArticleIndexMessage();
        msg.setOperation(MQOperation.CREATE);
        msg.setArticleId("A1");
        when(articleSearchRepository.save(any())).thenThrow(new RuntimeException("ES down"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> consumer.handleArticleIndex(record("A1", msg)));
    }

    /**
     * 迁移前本文件里既有的 publishAt 断言，改成本任务的新签名继续保留
     * （ES 文档转换的字段覆盖不能因为换监听器而丢掉）。
     */
    @Test
    @DisplayName("should carry publishAt into ES document on CREATE")
    void createMessage_carriesPublishAtIntoDocument() {
        LocalDateTime publishAt = LocalDateTime.of(2026, 8, 1, 9, 30);
        ArticleIndexMessage msg = new ArticleIndexMessage();
        msg.setOperation(MQOperation.CREATE);
        msg.setArticleId("a1");
        msg.setSlug("hello-world");
        msg.setTitle("Hello World");
        msg.setSummary("summary");
        msg.setContentMd("content");
        msg.setStatus("published");
        msg.setCreatedAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        msg.setUpdatedAt(LocalDateTime.of(2026, 8, 1, 9, 30));
        msg.setPublishAt(publishAt);
        msg.setTags(List.of("Java"));
        ArgumentCaptor<ArticleDocument> captor = ArgumentCaptor.forClass(ArticleDocument.class);

        consumer.handleArticleIndex(record("a1", msg));

        verify(articleSearchRepository).save(captor.capture());
        ArticleDocument doc = captor.getValue();
        assertNotNull(doc);
        assertEquals(publishAt, doc.getPublishAt());
    }
}
