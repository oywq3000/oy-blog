# 文章 AI 审核异步化 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把已实现的同步 AI 审核改为真异步：发布/编辑秒回 `ai_reviewing`，MQ 消息驱动后台审核，延迟重试（10s/30s/90s）+ 15 分钟兜底扫描双保险，任何故障收敛到转人工。

**Architecture:** publish 非豁免路径落库 `status=ai_reviewing`（编辑→`article_pending_content` 待生效区）并在事务提交后发 MQ 审核消息；article-service 新增 `@RabbitListener` 消费者带幂等闸执行三态流转（复用 ModerationService/ArticleIndexMessageService/ArticleChapterService）；失败经 TTL 死信回路延迟重试 3 次后转人工；`@Scheduled` 兜底扫描防消息丢失卡死。Python/DB/admin-service 零改动。

**Tech Stack:** Spring Boot 3.2 + Spring AMQP（RabbitTemplate/@RabbitListener/Declarable）+ MyBatis-Plus + Mockito/JUnit5。

**Spec:** `docs/superpowers/specs/2026-08-29-async-moderation-design.md`（本计划从 spec 论证，执行时与 spec 一起阅读）

## Global Constraints

- 仓库：`G:\JavaWorkSpace\oy-blog-dev1`（Git Bash `/g/JavaWorkSpace/oy-blog-dev1`），分支 dev1，不 push。
- **命令行编译必须 JDK 21**：统一前缀 `JAVA_HOME=/d/DevelopKit/jdk-21.0.8`。
- **Java 单测命令模板**（必须带 `-Dsurefire.failIfNoSpecifiedTests=false`）：
  `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/article-service -am test -Dtest=<测试类> -Dsurefire.failIfNoSpecifiedTests=false`
- **`src/test` 被 .gitignore 忽略**：测试文件不提交，commit 只含 src/main（与 doc/）。
- **单测里 `Result.ok/error` 依赖 I18nUtils 静态 messageSource**：每个新测试类带 `@BeforeAll` 反射注入 `StaticMessageSource`（先例 `ArticleStatsBizServiceImplTest` 第 43-48 行）。
- 若编译失败源于 src/test 下遗留一次性脚本（testForCreateArticle 等），删除再跑。
- 状态字面量：`status` 新增 `ai_reviewing`；`review_status` 新增 `ai_reviewing`（原 approved/rejected/manual/exempt 保留）；ModerationLog action 沿用 `ai_approve/ai_reject/ai_manual`。
- 幂等铁律：消费端一切动作前先查 DB 状态闸；重复消息/过期任务/兜底扫描并发必须无害。
- 重试语义：消息头 `x-attempt` 从 0 起（无头=0）；消费失败时 `attempt < 3` → 重试（`sendRetry(articleId, attempt+1)`，TTL = `retryTtlMs[attempt]` = 10s/30s/90s），`attempt >= 3` → 转人工。
- 代码中文注释，风格与现有代码一致。
- 基线：当前 dev1 上同步版代码（47 测试全绿）是起点；同步版 spec `2026-08-29-article-ai-moderation-design.md` 的决策（豁免、先审后生效语义、fail-closed 方向）全部保留。

---

## 文件结构总览

- Modify `oy-blog-common/.../mq/constants/ArticleMQConstant.java`（+4 常量）
- Create `oy-blog-common/.../mq/domain/ArticleModerationMessage.java`
- Create `article-service/.../config/ModerationRabbitConfig.java`（exchange/主队列/重试队列 Declarable 声明）
- Modify `article-service/.../config/ModerationProperties.java`（+retryTtlMs/maxAttempt/stuckTimeoutMinutes/scanIntervalMs）
- Create `article-service/.../service/ArticleModerationProducer.java`
- Create `article-service/.../service/ModerationRetrySender.java`
- Modify `article-service/.../service/impl/ArticleBizServiceImpl.java`（publish 异步化）
- Modify `article-service/.../service/ArticleIndexMessageService.java`（+loadTagNames）
- Modify `article-service/.../service/impl/ModerationAdminBizServiceImpl.java`（listTagNames 改调共享方法）
- Create `article-service/.../consumer/ArticleModerationConsumer.java`
- Create `article-service/.../scheduler/ModerationStuckScanner.java`
- Modify `doc/article-moderation-acceptance.md`（异步验收项）

---

## Task 1: MQ 基建（常量 + 消息体 + 队列声明）

**Files:**
- Modify: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-common/src/main/java/com/oyproj/common/mq/constants/ArticleMQConstant.java`
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-common/src/main/java/com/oyproj/common/mq/domain/ArticleModerationMessage.java`
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/config/ModerationRabbitConfig.java`

**Interfaces:**
- Produces（Task 2/3/4/5 依赖）:
  - 常量 `ArticleMQConstant.ARTICLE_MODERATION_EXCHANGE`（"article.moderation.exchange"）、`ARTICLE_MODERATION_QUEUE`（"article.moderation.queue"）、`ARTICLE_MODERATION_ROUTING_KEY`（"article.moderation"）、`ARTICLE_MODERATION_RETRY_QUEUE`（"article.moderation.retry.queue"）
  - 消息体 `ArticleModerationMessage`：字段 `articleId`（Lombok @Data @Builder @NoArgsConstructor @AllArgsConstructor）

说明：队列声明为纯 Spring AMQP 注解配置（Declarable Bean），无独立测试价值，本任务直接实现 + 编译验证 + 提交。

- [ ] **Step 1: 扩展常量**

`ArticleMQConstant.java` 在 DLQ 常量后追加：

```java
    // 文章 AI 审核队列（异步审核）
    public static final String ARTICLE_MODERATION_EXCHANGE = "article.moderation.exchange";
    public static final String ARTICLE_MODERATION_QUEUE = "article.moderation.queue";
    public static final String ARTICLE_MODERATION_ROUTING_KEY = "article.moderation";
    // 延迟重试回路：retry exchange → retry 队列（无消费者，消息带逐条 TTL）→ 到期死信回主 exchange
    public static final String ARTICLE_MODERATION_RETRY_EXCHANGE = "article.moderation.retry.exchange";
    public static final String ARTICLE_MODERATION_RETRY_QUEUE = "article.moderation.retry.queue";
```

- [ ] **Step 2: 消息体**

```java
package com.oyproj.common.mq.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章 AI 审核消息（发布/编辑提交后驱动后台审核）
 * 重试计数不放消息体，放消息头 x-attempt（见 ModerationRetrySender）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleModerationMessage {
    /** 文章ID（消费端从 DB 读最新状态，消息体只做触发） */
    private String articleId;
}
```

- [ ] **Step 3: 队列声明**

```java
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
```

- [ ] **Step 4: 编译验证**

Run: `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/article-service -am compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add oy-blog-common/src/main/java/com/oyproj/common/mq/constants/ArticleMQConstant.java oy-blog-common/src/main/java/com/oyproj/common/mq/domain/ArticleModerationMessage.java oy-blog-service/article-service/src/main/java/com/oyproj/config/ModerationRabbitConfig.java && git commit -m "feat: 文章审核 MQ 拓扑（exchange+消费队列+TTL 死信重试队列）"
```

---

## Task 2: ModerationProperties 扩展 + ArticleModerationProducer

**Files:**
- Modify: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/config/ModerationProperties.java`
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/service/ArticleModerationProducer.java`
- Test: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/test/java/com/oyproj/service/ArticleModerationProducerTest.java`

**Interfaces:**
- Consumes: Task 1 常量与消息体；已有 `RabbitTemplate`（Spring Boot 自动配置）
- Produces（Task 4/5 依赖）:
  - `ArticleModerationProducer.sendModerationMessage(String articleId)`（发送失败记日志不抛异常——兜底扫描接管）
  - `ModerationProperties.getRetryTtlMs()`（`List<Long>` 默认 `[10000,30000,90000]`）、`getMaxAttempt()`（int 默认 3）、`getStuckTimeoutMinutes()`（int 默认 15）、`getScanIntervalMs()`（long 默认 300000）

- [ ] **Step 1: 写失败测试** `ArticleModerationProducerTest.java`

```java
package com.oyproj.service;

import com.oyproj.common.mq.constants.ArticleMQConstant;
import com.oyproj.common.mq.domain.ArticleModerationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 审核消息生产者测试：正常发送、失败仅记日志不抛异常
 */
@ExtendWith(MockitoExtension.class)
class ArticleModerationProducerTest {

    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private ArticleModerationProducer producer;

    @Test
    void shouldSendToModerationExchangeWithRoutingKey() {
        producer.sendModerationMessage("a1");

        ArgumentCaptor<ArticleModerationMessage> captor = ArgumentCaptor.forClass(ArticleModerationMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(ArticleMQConstant.ARTICLE_MODERATION_EXCHANGE),
                eq(ArticleMQConstant.ARTICLE_MODERATION_ROUTING_KEY),
                captor.capture());
        assertEquals("a1", captor.getValue().getArticleId());
    }

    @Test
    void shouldSwallowSendFailure() {
        doThrow(new RuntimeException("连接失败")).when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(Object.class));

        // 发送失败只记日志不抛异常：文章已落库 ai_reviewing，兜底扫描接管
        assertDoesNotThrow(() -> producer.sendModerationMessage("a1"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: 用 Global Constraints 命令模板（`-Dtest=ArticleModerationProducerTest`）
Expected: 编译失败（类不存在）

- [ ] **Step 3: 实现**

`ModerationProperties.java` 在 `timeoutMs` 后追加：

```java
    /** 审核重试延迟序列（毫秒）：第 1/2/3 次重试的延迟，取 retryTtlMs[attempt] */
    private List<Long> retryTtlMs = new ArrayList<>(List.of(10000L, 30000L, 90000L));
    /** 最大重试次数（失败达到该次数后转人工），attempt 从 0 起 */
    private int maxAttempt = 3;
    /** 兜底扫描：审核中超过该分钟数无结果 → 转人工 */
    private int stuckTimeoutMinutes = 15;
    /** 兜底扫描间隔（毫秒） */
    private long scanIntervalMs = 300000L;
```

```java
package com.oyproj.service;

import com.oyproj.common.mq.constants.ArticleMQConstant;
import com.oyproj.common.mq.domain.ArticleModerationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * 文章审核消息生产者。
 * 发送失败只记日志不抛异常：文章已落库 ai_reviewing，ModerationStuckScanner 兜底扫描会接管。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleModerationProducer {

    private final RabbitTemplate rabbitTemplate;

    /** 发布/编辑提交后触发一次后台审核 */
    public void sendModerationMessage(String articleId) {
        try {
            ArticleModerationMessage message = ArticleModerationMessage.builder()
                    .articleId(articleId)
                    .build();
            rabbitTemplate.convertAndSend(
                    ArticleMQConstant.ARTICLE_MODERATION_EXCHANGE,
                    ArticleMQConstant.ARTICLE_MODERATION_ROUTING_KEY,
                    message
            );
            log.info("审核消息发送成功, articleId: {}", articleId);
        } catch (Exception e) {
            log.error("审核消息发送失败（兜底扫描将接管）, articleId: {}, 错误: {}", articleId, e.getMessage());
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: 同 Step 2 命令
Expected: 2 个测试全 PASS

- [ ] **Step 5: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add oy-blog-service/article-service/src/main/java/com/oyproj/config/ModerationProperties.java oy-blog-service/article-service/src/main/java/com/oyproj/service/ArticleModerationProducer.java && git commit -m "feat: 审核消息生产者 + 异步重试配置（TTL序列/最大次数/兜底扫描参数）"
```

---

## Task 3: ModerationRetrySender（延迟重试）

**Files:**
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/service/ModerationRetrySender.java`
- Test: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/test/java/com/oyproj/service/ModerationRetrySenderTest.java`

**Interfaces:**
- Consumes: Task 1 常量/消息体、Task 2 `ModerationProperties`
- Produces（Task 5 依赖）: `ModerationRetrySender.sendRetry(String articleId, int nextAttempt)`——`nextAttempt` 为下一次消费的计数值（∈{1,2,3}，由消费端 `attempt+1` 算出）；TTL = `retryTtlMs[nextAttempt-1]`（10s/30s/90s），越界钳制取最后一个

- [ ] **Step 1: 写失败测试**

```java
package com.oyproj.service;

import com.oyproj.common.mq.constants.ArticleMQConstant;
import com.oyproj.config.ModerationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * 延迟重试发送器测试：TTL 按 nextAttempt 递增（10s/30s/90s），消息头带 x-attempt
 */
@ExtendWith(MockitoExtension.class)
class ModerationRetrySenderTest {

    @Mock private RabbitTemplate rabbitTemplate;

    private ModerationRetrySender sender;

    @BeforeEach
    void setUp() {
        ModerationProperties props = new ModerationProperties();
        sender = new ModerationRetrySender(rabbitTemplate, props);
    }

    /** 捕获后置处理器并手动执行，校验最终消息的 header 与 expiration */
    private Message capturePostProcessed(String articleId, int nextAttempt) {
        sender.sendRetry(articleId, nextAttempt);
        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                eq(ArticleMQConstant.ARTICLE_MODERATION_RETRY_EXCHANGE),
                eq(ArticleMQConstant.ARTICLE_MODERATION_ROUTING_KEY),
                any(),
                captor.capture());
        MessageProperties props = new MessageProperties();
        Message raw = new Message(new byte[0], props);
        return captor.getValue().postProcessMessage(raw);
    }

    @Test
    void retryTtlFollowsSequence() {
        // nextAttempt=1/2/3 → TTL 10s/30s/90s
        assertEquals("10000", capturePostProcessed("a1", 1).getMessageProperties().getExpiration());
        assertEquals("30000", capturePostProcessed("a1", 2).getMessageProperties().getExpiration());
        assertEquals("90000", capturePostProcessed("a1", 3).getMessageProperties().getExpiration());
    }

    @Test
    void retryCarriesAttemptHeader() {
        Message processed = capturePostProcessed("a1", 2);
        assertEquals(2, processed.getMessageProperties().getHeader("x-attempt"));
    }

    @Test
    void attemptBeyondSequenceClampsToLastTtl() {
        // 防御：nextAttempt 越界时取序列最后一个 TTL（正常流程到不了这里，attempt>=3 走转人工）
        assertEquals("90000", capturePostProcessed("a1", 99).getMessageProperties().getExpiration());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: Global Constraints 模板（`-Dtest=ModerationRetrySenderTest`）
Expected: 编译失败（类不存在）

- [ ] **Step 3: 实现**

```java
package com.oyproj.service;

import com.oyproj.common.mq.constants.ArticleMQConstant;
import com.oyproj.common.mq.domain.ArticleModerationMessage;
import com.oyproj.config.ModerationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.stereotype.Service;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * 审核延迟重试发送器。
 * 消息发往独立的 retry exchange → retry 队列（无消费者）→ 逐条 TTL 到期死信回主 exchange
 * 重新投递主队列（发往主 exchange 会让主队列立即消费，延迟失效——必须走 retry exchange）。
 * 重试语义：nextAttempt = 下一次消费的计数值（∈{1,2,3}）；TTL = retryTtlMs[nextAttempt-1]。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationRetrySender {

    private final RabbitTemplate rabbitTemplate;
    private final ModerationProperties properties;

    /** 发延迟重试：nextAttempt 为下一次消费时的计数值（消费端 attempt+1 算出） */
    public void sendRetry(String articleId, int nextAttempt) {
        int index = Math.max(0, Math.min(nextAttempt - 1, properties.getRetryTtlMs().size() - 1));
        long ttl = properties.getRetryTtlMs().get(index);
        MessagePostProcessor postProcessor = message -> {
            message.getMessageProperties().setHeader("x-attempt", nextAttempt);
            message.getMessageProperties().setExpiration(String.valueOf(ttl));
            return message;
        };
        ArticleModerationMessage body = ArticleModerationMessage.builder().articleId(articleId).build();
        rabbitTemplate.convertAndSend(
                ArticleMQConstant.ARTICLE_MODERATION_RETRY_EXCHANGE,
                ArticleMQConstant.ARTICLE_MODERATION_ROUTING_KEY,
                body,
                postProcessor
        );
        log.info("审核重试消息已发送, articleId: {}, nextAttempt: {}, TTL: {}ms", articleId, nextAttempt, ttl);
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: 同 Step 2 命令
Expected: 3 个测试全 PASS

- [ ] **Step 5: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add oy-blog-service/article-service/src/main/java/com/oyproj/service/ModerationRetrySender.java && git commit -m "feat: 审核延迟重试发送器（x-attempt 头 + 逐条 TTL 死信回路）"
```

---

## Task 4: publish 异步化改造

**Files:**
- Modify: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleBizServiceImpl.java`（publish 非豁免路径改为异步提交）
- Test: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/test/java/com/oyproj/service/impl/ArticleBizPublishModerationTest.java`（重构）

**Interfaces:**
- Consumes: Task 2 `ArticleModerationProducer`、已有 `ModerationService/ArticlePendingContentMapper/ArticleIndexMessageService/ArticleChapterService`
- Produces: `POST /article/publish` 返回 verdict 新增 `ai_reviewing`；落库语义 `ai_reviewing` / 编辑待生效区（Task 5 消费者依赖这些落库状态）

- [ ] **Step 1: 重构测试**（同步三态用例迁移到 Task 5 消费者测试；本任务测"提交即审"语义）

`ArticleBizPublishModerationTest.java` 保留豁免类用例（exemptUserSkipsAiAndPublishesDirectly / disabledSwitchPublishesDirectly），其余替换为：

```java
    @Test
    void publishNewArticleSubmitsToAsyncReview() {
        Result<Map<String, String>> result = biz.publish(dto());

        assertEquals("ai_reviewing", result.getData().get("verdict"));

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao, atLeastOnce()).save(captor.capture());
        Article saved = captor.getValue();
        assertEquals("ai_reviewing", saved.getStatus());
        assertEquals("ai_reviewing", saved.getReviewStatus());
        assertEquals("u1", saved.getAuthorId());

        verify(moderationService, never()).moderate(anyString(), anyString(), any(), anyString()); // 审核移到消费者
        verify(articleModerationProducer).sendModerationMessage(anyString()); // 事务提交后发消息
        verify(contentDao).saveOrUpdate(any()); // 内容照常保存
        verify(indexMessageService, never()).sendIndexAfterCommit(any(), anyList(), any()); // 审核通过前不发索引
    }

    @Test
    void publishRejectsWhenAlreadyReviewing() {
        Article reviewing = publishedArticle();
        reviewing.setStatus("ai_reviewing");
        when(articleDao.getById("a1")).thenReturn(reviewing);

        ArticleSaveDto dto = dto();
        dto.setId("a1");
        Result<Map<String, String>> result = biz.publish(dto);

        assertEquals(false, result.getIsSuccess());
        assertTrue(result.getErrMsg().contains("审核中"));
        verify(articleModerationProducer, never()).sendModerationMessage(anyString());
        verify(moderationService, never()).moderate(anyString(), anyString(), any(), anyString());
    }

    @Test
    void editPublishedSubmitsPendingContentForAsyncReview() {
        when(articleDao.getById("a1")).thenReturn(publishedArticle());
        when(pendingContentMapper.selectById("a1")).thenReturn(null);

        ArticleSaveDto dto = dto();
        dto.setId("a1");
        Result<Map<String, String>> result = biz.publish(dto);

        assertEquals("ai_reviewing", result.getData().get("verdict"));

        // 旧版内容不动（先审后生效语义保留）
        verify(contentDao, never()).saveOrUpdate(any());
        verify(indexMessageService, never()).sendIndexAfterCommit(any(), anyList(), any());

        ArgumentCaptor<ArticlePendingContent> captor = ArgumentCaptor.forClass(ArticlePendingContent.class);
        verify(pendingContentMapper).insert(captor.capture());
        assertEquals("a1", captor.getValue().getArticleId());
        assertEquals("新标题", captor.getValue().getPendingTitle());

        ArgumentCaptor<Article> articleCaptor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao, atLeastOnce()).updateById(articleCaptor.capture());
        Article updated = articleCaptor.getValue();
        assertEquals("published", updated.getStatus()); // 旧版继续展示
        assertEquals("ai_reviewing", updated.getReviewStatus());

        verify(articleModerationProducer).sendModerationMessage("a1");
    }

    @Test
    void editRejectedWhenAlreadyEditingReviewing() {
        Article published = publishedArticle();
        published.setReviewStatus("ai_reviewing");
        when(articleDao.getById("a1")).thenReturn(published);

        ArticleSaveDto dto = dto();
        dto.setId("a1");
        Result<Map<String, String>> result = biz.publish(dto);

        assertEquals(false, result.getIsSuccess());
        assertTrue(result.getErrMsg().contains("审核中"));
        verify(articleModerationProducer, never()).sendModerationMessage(anyString());
    }

    @Test
    void resubmitRejectedArticleGoesToAiReviewing() {
        Article rejected = publishedArticle();
        rejected.setStatus("rejected");
        when(articleDao.getById("a1")).thenReturn(rejected);

        ArticleSaveDto dto = dto();
        dto.setId("a1");
        Result<Map<String, String>> result = biz.publish(dto);

        assertEquals("ai_reviewing", result.getData().get("verdict"));
        verify(articleModerationProducer).sendModerationMessage("a1");
    }
```

测试类新增 `@Mock private ArticleModerationProducer articleModerationProducer;`。删除旧的三态同步用例（approveNewArticlePublishes 等）与 `resubmitRejectedArticleApprovePublishes`（被上面的 resubmitRejectedArticleGoesToAiReviewing 替代）。豁免用例、helper 方法（`dto()`/`publishedArticle()`）、`setUp` 的 lenient stub、`@BeforeAll` I18n 注入全部保留不动。

- [ ] **Step 2: 运行确认失败**

Run: Global Constraints 模板（`-Dtest=ArticleBizPublishModerationTest`）
Expected: FAIL（现实现仍同步审核，断言不符）

- [ ] **Step 3: 改造 publish**

`ArticleBizServiceImpl.java`：

1. 注入新依赖：`@NotNull private final ArticleModerationProducer articleModerationProducer;`（字段区追加）

2. `publish` 方法整体替换为：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, String>> publish(ArticleSaveDto dto) {
        // contentHtml 为空时，自动从 contentMd 渲染
        if (!StringUtils.hasText(dto.getContentHtml())) {
            dto.setContentHtml(MarkdownRenderer.toHtml(dto.getContentMd()));
        }
        boolean isNew = !StringUtils.hasText(dto.getId());
        Article existing = isNew ? null : articleDao.getById(dto.getId());
        if (!isNew && existing == null) {
            throw new NotFoundException(I18nUtils.t("article.not_found"));
        }
        boolean editingPublished = existing != null && "published".equals(existing.getStatus());

        // 审核中守卫：AI 审核中 / 编辑审核中，不允许再次提交（防频繁重审；可删除）
        if (existing != null
                && ("ai_reviewing".equals(existing.getStatus()) || "ai_reviewing".equals(existing.getReviewStatus()))) {
            return Result.error("审核中，请稍候");
        }

        // 豁免路径：开关关闭或豁免用户 → 直接放行（同步、秒过，不做异步）
        if (!moderationService.isEnabled() || moderationService.isExempt()) {
            boolean isRejectedResubmit = existing != null && "rejected".equals(existing.getStatus());
            MQOperation op = (isNew || isRejectedResubmit) ? MQOperation.CREATE : MQOperation.UPDATE;
            return persistWithReview(dto, "published", "exempt", "审核豁免", op, null);
        }

        if (editingPublished) {
            // 编辑已发布文章：新内容进待生效区（旧版继续 published），异步审核后替换生效
            existing.setCoverUrl(dto.getCoverUrl());
            existing.setAllowComment(dto.getAllowComment() != null ? dto.getAllowComment() : 1);
            existing.setUpdateAt(LocalDateTime.now());
            existing.setReviewStatus("ai_reviewing");
            existing.setReviewReason("");
            articleDao.updateById(existing);
            saveRelations(existing.getId(), dto);
            savePendingContent(existing.getId(), dto, "");
            sendModerationAfterCommit(existing.getId());
            return publishResult(existing.getId(), "ai_reviewing", "已提交 AI 审核");
        }

        // 新文章/草稿转发布/驳回重发：落库为"AI 审核中"，消息驱动后台审核
        Result<Map<String, String>> result =
                persistWithReview(dto, "ai_reviewing", "ai_reviewing", "", null, null);
        sendModerationAfterCommit(result.getData().get("articleId"));
        return publishResult(result.getData().get("articleId"), "ai_reviewing", "已提交 AI 审核");
    }

    /** 事务提交后发审核消息（DB 已提交，消费者读到的状态一定存在） */
    private void sendModerationAfterCommit(String articleId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                articleModerationProducer.sendModerationMessage(articleId);
            }
        });
    }
```

3. `persistWithReview` 的 `status`/`reviewStatus` 注释补充 `ai_reviewing` 取值（方法体不用改）。

- [ ] **Step 4: 运行确认通过 + 全量回归**

Run: 同 Step 2 命令 + `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/article-service -am test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 新测试全 PASS；全量回归无新增失败（publish 相关旧断言已随 Step 1 重构）

- [ ] **Step 5: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleBizServiceImpl.java && git commit -m "feat: publish 异步化（提交即审：落库 ai_reviewing + 事务提交后发 MQ，含审核中守卫）"
```

---

## Task 5: ArticleModerationConsumer（核心：幂等闸 + 三态流转 + 失败重试）

**Files:**
- Modify: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/service/ArticleIndexMessageService.java`（+`loadTagNames`）
- Modify: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ModerationAdminBizServiceImpl.java`（私有 listTagNames 改调共享方法）
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/consumer/ArticleModerationConsumer.java`
- Test: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/test/java/com/oyproj/consumer/ArticleModerationConsumerTest.java`

**Interfaces:**
- Consumes: Task 1 队列、Task 2 `ModerationService.moderate/writeLog`、Task 3 `ModerationRetrySender`、已有 `articleDao/articleTagDao/contentDao/pendingContentMapper/indexMessageService/chapterService`
- Produces: 审核结果落库（published/rejected/pending_review + 待生效区清理/保留）；ModerationLog（ai_approve/ai_reject/ai_manual）

- [ ] **Step 1: 写失败测试** `ArticleModerationConsumerTest.java`

```java
package com.oyproj.consumer;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleContent;
import com.oyproj.domain.entity.ArticlePendingContent;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.dto.ArticleDao;
import com.oyproj.dto.ArticleTagDao;
import com.oyproj.mapper.ArticlePendingContentMapper;
import com.oyproj.service.ArticleChapterService;
import com.oyproj.service.ArticleIndexMessageService;
import com.oyproj.service.ModerationRetrySender;
import com.oyproj.service.ModerationService;
import com.oyproj.service.ModerationVerdict;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 审核消费者测试：幂等闸四分支 + 三态流转 + 失败重试/转人工
 */
@ExtendWith(MockitoExtension.class)
class ArticleModerationConsumerTest {

    @Mock private ArticleDao articleDao;
    @Mock private ArticleContentDao contentDao;
    @Mock private ArticleTagDao articleTagDao;
    @Mock private ArticlePendingContentMapper pendingContentMapper;
    @Mock private ModerationService moderationService;
    @Mock private ArticleIndexMessageService indexMessageService;
    @Mock private ArticleChapterService chapterService;
    @Mock private ModerationRetrySender retrySender;

    @InjectMocks private ArticleModerationConsumer consumer;

    @BeforeAll
    static void initI18n() throws Exception {
        Field field = I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, (MessageSource) new StaticMessageSource());
    }

    private Article newReviewing() {
        Article a = new Article();
        a.setId("a1");
        a.setTitle("新标题");
        a.setSummary("摘要");
        a.setAuthorId("u1");
        a.setStatus("ai_reviewing");
        a.setReviewStatus("ai_reviewing");
        a.setCreatedAt(LocalDateTime.now());
        return a;
    }

    private Article publishedWithPending() {
        Article a = new Article();
        a.setId("a1");
        a.setTitle("旧标题");
        a.setAuthorId("u1");
        a.setStatus("published");
        a.setReviewStatus("ai_reviewing");
        return a;
    }

    private ArticlePendingContent pending() {
        return ArticlePendingContent.builder()
                .articleId("a1")
                .pendingTitle("新标题")
                .pendingSummary("新摘要")
                .pendingContentMd("# 新正文")
                .pendingContentHtml("<h1>新正文</h1>")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void approveNewArticlePublishes() {
        when(articleDao.getById("a1")).thenReturn(newReviewing());
        when(moderationService.moderate("a1", "新标题", "摘要", anyString()))
                .thenReturn(ModerationVerdict.approved("内容正常"));
        when(articleTagDao.listTagNamesByArticleIds(anyList())).thenReturn(Collections.emptyMap());

        consumer.handle("a1", 0);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao).updateById(captor.capture());
        Article updated = captor.getValue();
        assertEquals("published", updated.getStatus());
        assertEquals("approved", updated.getReviewStatus());
        assertEquals(1, updated.getIsReviewed());

        verify(indexMessageService).sendIndexAfterCommit(any(), anyList(), eq(com.oyproj.common.mq.constants.MQOperation.CREATE));
        verify(moderationService).writeLog("a1", "ai_approve", "内容正常", "ai");
        verify(retrySender, never()).sendRetry(anyString(), anyInt());
    }

    @Test
    void rejectNewArticleRejects() {
        when(articleDao.getById("a1")).thenReturn(newReviewing());
        when(moderationService.moderate(anyString(), anyString(), any(), anyString()))
                .thenReturn(ModerationVerdict.rejected("广告引流"));

        consumer.handle("a1", 0);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao).updateById(captor.capture());
        assertEquals("rejected", captor.getValue().getStatus());
        assertEquals("广告引流", captor.getValue().getReviewReason());

        verify(indexMessageService, never()).sendIndexAfterCommit(any(), anyList(), any());
        verify(moderationService).writeLog("a1", "ai_reject", "广告引流", "ai");
    }

    @Test
    void manualNewArticleGoesPendingReview() {
        when(articleDao.getById("a1")).thenReturn(newReviewing());
        when(moderationService.moderate(anyString(), anyString(), any(), anyString()))
                .thenReturn(ModerationVerdict.manual("内容有歧义"));

        consumer.handle("a1", 0);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao).updateById(captor.capture());
        assertEquals("pending_review", captor.getValue().getStatus());
        assertEquals("manual", captor.getValue().getReviewStatus());
        verify(indexMessageService, never()).sendIndexAfterCommit(any(), anyList(), any());
        verify(moderationService).writeLog("a1", "ai_manual", "内容有歧义", "ai");
    }

    @Test
    void approveEditAppliesPending() {
        when(articleDao.getById("a1")).thenReturn(publishedWithPending());
        when(pendingContentMapper.selectById("a1")).thenReturn(pending());
        when(moderationService.moderate("a1", "新标题", "新摘要", "# 新正文"))
                .thenReturn(ModerationVerdict.approved("内容正常"));
        when(contentDao.getById("a1")).thenReturn(null);
        when(articleTagDao.listTagNamesByArticleIds(anyList())).thenReturn(Collections.emptyMap());

        consumer.handle("a1", 0);

        verify(articleDao).updateById(any(Article.class));
        verify(contentDao).saveOrUpdate(any(ArticleContent.class));
        verify(chapterService).rebuild("a1", "# 新正文");
        verify(pendingContentMapper).deleteById("a1");
        verify(indexMessageService).sendIndexAfterCommit(any(), anyList(), eq(com.oyproj.common.mq.constants.MQOperation.UPDATE));
        verify(moderationService).writeLog("a1", "ai_approve", "内容正常", "ai");
    }

    @Test
    void rejectEditDiscardsPending() {
        when(articleDao.getById("a1")).thenReturn(publishedWithPending());
        when(pendingContentMapper.selectById("a1")).thenReturn(pending());
        when(moderationService.moderate(anyString(), anyString(), any(), anyString()))
                .thenReturn(ModerationVerdict.rejected("广告引流"));

        consumer.handle("a1", 0);

        verify(pendingContentMapper).deleteById("a1"); // 本次编辑丢弃
        verify(contentDao, never()).saveOrUpdate(any());
        verify(indexMessageService, never()).sendIndexAfterCommit(any(), anyList(), any());
        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao).updateById(captor.capture());
        assertEquals("published", captor.getValue().getStatus()); // 旧版继续展示
        assertEquals("rejected", captor.getValue().getReviewStatus());
    }

    @Test
    void manualEditKeepsPendingForHumanReview() {
        when(articleDao.getById("a1")).thenReturn(publishedWithPending());
        when(pendingContentMapper.selectById("a1")).thenReturn(pending());
        when(moderationService.moderate(anyString(), anyString(), any(), anyString()))
                .thenReturn(ModerationVerdict.manual("内容有歧义"));

        consumer.handle("a1", 0);

        verify(pendingContentMapper, never()).deleteById(anyString()); // 待生效区保留，进人工队列
        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao).updateById(captor.capture());
        assertEquals("manual", captor.getValue().getReviewStatus());
    }

    @Test
    void deletedArticleGetsCleanedUp() {
        Article deleted = newReviewing();
        deleted.setDeletedAt(LocalDateTime.now());
        when(articleDao.getById("a1")).thenReturn(deleted);
        when(pendingContentMapper.selectById("a1")).thenReturn(pending());

        consumer.handle("a1", 0);

        verify(pendingContentMapper).deleteById("a1"); // 收尾：清待生效区
        verify(moderationService, never()).moderate(anyString(), anyString(), any(), anyString());
        verify(articleDao, never()).updateById(any(Article.class));
    }

    @Test
    void staleTaskIsIgnored() {
        // 状态已非审核中（重复消息/已人工处理）：无任何动作
        Article published = publishedWithPending();
        published.setReviewStatus("approved");
        when(articleDao.getById("a1")).thenReturn(published);

        consumer.handle("a1", 0);

        verify(moderationService, never()).moderate(anyString(), anyString(), any(), anyString());
        verify(articleDao, never()).updateById(any(Article.class));
        verify(pendingContentMapper, never()).deleteById(anyString());
    }

    @Test
    void failureUnderMaxAttemptSendsRetry() {
        when(articleDao.getById("a1")).thenReturn(newReviewing());
        when(moderationService.moderate(anyString(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("连接超时"));

        consumer.handle("a1", 1); // attempt=1 < 3

        verify(retrySender).sendRetry("a1", 2); // 下一次消费 attempt=2
        verify(articleDao, never()).updateById(any(Article.class));
    }

    @Test
    void failureAtMaxAttemptGoesManual() {
        when(articleDao.getById("a1")).thenReturn(newReviewing());
        when(moderationService.moderate(anyString(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("连接超时"));

        consumer.handle("a1", 3); // attempt=3 >= 3：转人工

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao).updateById(captor.capture());
        assertEquals("pending_review", captor.getValue().getStatus());
        assertEquals("manual", captor.getValue().getReviewStatus());
        assertEquals("审核服务不可用，转人工审核", captor.getValue().getReviewReason());
        verify(retrySender, never()).sendRetry(anyString(), anyInt());
        verify(moderationService).writeLog("a1", "ai_manual", "审核服务不可用，转人工审核", "ai");
    }
}
```

> 注意：`approveNewArticlePublishes` 里 `moderate("a1", "新标题", "摘要", anyString())` 的 contentMd 参数——新文章审核用 `article_content` 的正文，测试需 stub `contentDao.getById("a1")` 返回带 contentMd 的 ArticleContent（见 Step 3 实现）；若实现直接读 content 表，请在测试中补 `when(contentDao.getById("a1")).thenReturn(content)`（contentMd="# 正文"）并把 moderate stub 改为 `moderate("a1", "新标题", "摘要", "# 正文")`。

- [ ] **Step 2: 运行确认失败**

Run: Global Constraints 模板（`-Dtest=ArticleModerationConsumerTest`）
Expected: 编译失败（类不存在）

- [ ] **Step 3: 实现**

先做共享方法抽取。`ArticleIndexMessageService.java` 加：

```java
    private final ArticleTagDao articleTagDao;
```

```java
    /** 文章标签名列表（索引消息用，发布与审核通过共用） */
    public List<String> loadTagNames(String articleId) {
        return articleTagDao.listTagNamesByArticleIds(Collections.singletonList(articleId))
                .getOrDefault(articleId, Collections.emptyList());
    }
```

（import 补 `com.oyproj.dto.ArticleTagDao` 与 `java.util.Collections`）

`ModerationAdminBizServiceImpl.java`：删除私有 `listTagNames` 方法，三处调用改为 `indexMessageService.loadTagNames(...)`。

消费者：

```java
package com.oyproj.consumer;

import com.oyproj.common.mq.constants.ArticleMQConstant;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.config.ModerationProperties;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleContent;
import com.oyproj.domain.entity.ArticlePendingContent;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.dto.ArticleDao;
import com.oyproj.mapper.ArticlePendingContentMapper;
import com.oyproj.service.ArticleChapterService;
import com.oyproj.service.ArticleIndexMessageService;
import com.oyproj.service.ModerationRetrySender;
import com.oyproj.service.ModerationService;
import com.oyproj.service.ModerationVerdict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 文章 AI 审核消费者：消息驱动后台审核。
 * 幂等铁律：一切动作前先查 DB 状态闸；重复消息/过期任务直接返回（自动确认）。
 * 失败路径：attempt < maxAttempt → 延迟重试；>= maxAttempt → 转人工（fail-closed）。
 * 任何异常都被吞掉（不抛出 → 不触发 RabbitMQ 无限 requeue），重试由 RetrySender/兜底扫描负责。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleModerationConsumer {

    private static final String ATTEMPT_HEADER = "x-attempt";
    private static final String MANUAL_FALLBACK_REASON = "审核服务不可用，转人工审核";

    private final ArticleDao articleDao;
    private final ArticleContentDao contentDao;
    private final ArticlePendingContentMapper pendingContentMapper;
    private final ModerationService moderationService;
    private final ArticleIndexMessageService indexMessageService;
    private final ArticleChapterService chapterService;
    private final ModerationRetrySender retrySender;
    private final ModerationProperties properties;

    @RabbitListener(queues = ArticleMQConstant.ARTICLE_MODERATION_QUEUE)
    public void onMessage(ArticleModerationMessage body, @Header(name = ATTEMPT_HEADER, required = false) Integer attempt) {
        // 消息体由默认 Jackson converter 解析；为兼容未带头的首投，attempt 缺省 0
        handle(body.getArticleId(), attempt == null ? 0 : attempt);
    }

    /** 主处理入口（测试直接调用此方法） */
    @Transactional(rollbackFor = Exception.class)
    public void handle(String articleId, int attempt) {
        try {
            Article article = articleDao.getById(articleId);
            if (article == null || article.getDeletedAt() != null) {
                // 作者撤稿：清理待生效区收尾
                pendingContentMapper.deleteById(articleId);
                return;
            }
            ArticlePendingContent pending = pendingContentMapper.selectById(articleId);

            // 幂等闸：只处理"审核中"状态；其余（草稿/已驳回/已人工处理/重复消息）直接返回
            boolean newReviewing = "ai_reviewing".equals(article.getStatus())
                    && "ai_reviewing".equals(article.getReviewStatus());
            boolean editReviewing = "published".equals(article.getStatus())
                    && "ai_reviewing".equals(article.getReviewStatus())
                    && pending != null;
            if (!newReviewing && !editReviewing) {
                log.debug("审核消息跳过（状态已非审核中）, articleId: {}", articleId);
                return;
            }

            // 组装审核输入：编辑场景用待生效区内容
            String title = editReviewing ? pending.getPendingTitle() : article.getTitle();
            String summary = editReviewing ? pending.getPendingSummary() : article.getSummary();
            String content = editReviewing ? pending.getPendingContentMd() : loadContentMd(articleId);

            ModerationVerdict verdict;
            try {
                verdict = moderationService.moderate(articleId, title, summary, content);
            } catch (Exception e) {
                log.warn("AI 审核调用失败, articleId: {}, attempt: {}, 错误: {}", articleId, attempt, e.getMessage());
                onModerateFailure(articleId, attempt, newReviewing, editReviewing);
                return;
            }

            if (editReviewing) {
                applyEditVerdict(article, pending, verdict);
            } else {
                applyNewVerdict(article, verdict);
            }
        } catch (Exception e) {
            // DB 异常等一律吞掉（不抛 → 不无限 requeue）。
            // 注意不走 onModerateFailure：状态可能已被 apply* 改过，重走转人工会污染结果；
            // 由兜底扫描对"仍卡在审核中"的文章收尾。
            log.error("审核消费处理异常, articleId: {}, 错误: {}", articleId, e.getMessage(), e);
        }
    }

    /** 新文章三态流转 */
    private void applyNewVerdict(Article article, ModerationVerdict verdict) {
        if (verdict.isApproved()) {
            article.setStatus("published");
            article.setPublishAt(LocalDateTime.now());
            article.setReviewStatus("approved");
            article.setReviewReason(verdict.reason());
            article.setIsReviewed(1);
            article.setUpdateAt(LocalDateTime.now());
            articleDao.updateById(article);
            indexMessageService.sendIndexAfterCommit(article, indexMessageService.loadTagNames(article.getId()), MQOperation.CREATE);
            moderationService.writeLog(article.getId(), "ai_approve", verdict.reason(), "ai");
            return;
        }
        if (verdict.isRejected()) {
            article.setStatus("rejected");
            article.setReviewStatus("rejected");
            article.setReviewReason(verdict.reason());
            article.setUpdateAt(LocalDateTime.now());
            articleDao.updateById(article);
            moderationService.writeLog(article.getId(), "ai_reject", verdict.reason(), "ai");
            return;
        }
        // manual：转人工队列（现有人工审核直接接管）
        article.setStatus("pending_review");
        article.setReviewStatus("manual");
        article.setReviewReason(verdict.reason());
        article.setUpdateAt(LocalDateTime.now());
        articleDao.updateById(article);
        moderationService.writeLog(article.getId(), "ai_manual", verdict.reason(), "ai");
    }

    /** 编辑三态流转（先审后生效语义保留：reject/manual 不碰旧版内容） */
    private void applyEditVerdict(Article article, ArticlePendingContent pending, ModerationVerdict verdict) {
        if (verdict.isApproved()) {
            // 待生效内容替换生效
            article.setTitle(pending.getPendingTitle());
            article.setSummary(pending.getPendingSummary());
            article.setReviewStatus("approved");
            article.setReviewReason(verdict.reason());
            article.setUpdateAt(LocalDateTime.now());
            articleDao.updateById(article);

            ArticleContent content = contentDao.getById(article.getId());
            if (content == null) {
                content = ArticleContent.builder().articleId(article.getId()).build();
            }
            content.setContentMd(pending.getPendingContentMd());
            content.setContentHtml(pending.getPendingContentHtml());
            content.setWordsCount(pending.getPendingContentMd() != null ? pending.getPendingContentMd().length() : 0);
            content.setUpdatedAt(LocalDateTime.now());
            contentDao.saveOrUpdate(content);

            chapterService.rebuild(article.getId(), pending.getPendingContentMd());
            pendingContentMapper.deleteById(pending.getArticleId());
            indexMessageService.sendIndexAfterCommit(article, indexMessageService.loadTagNames(article.getId()), MQOperation.UPDATE);
            moderationService.writeLog(article.getId(), "ai_approve", verdict.reason(), "ai");
            return;
        }
        if (verdict.isRejected()) {
            // 本次编辑丢弃：清待生效区，旧版继续展示
            pendingContentMapper.deleteById(pending.getArticleId());
            article.setReviewStatus("rejected");
            article.setReviewReason(verdict.reason());
            article.setUpdateAt(LocalDateTime.now());
            articleDao.updateById(article);
            moderationService.writeLog(article.getId(), "ai_reject", verdict.reason(), "ai");
            return;
        }
        // manual：待生效区保留，进人工队列
        article.setReviewStatus("manual");
        article.setReviewReason(verdict.reason());
        article.setUpdateAt(LocalDateTime.now());
        articleDao.updateById(article);
        moderationService.writeLog(article.getId(), "ai_manual", verdict.reason(), "ai");
    }

    /** 失败路径：attempt < maxAttempt → 延迟重试；否则转人工（fail-closed） */
    private void onModerateFailure(String articleId, int attempt, boolean newReviewing, boolean editReviewing) {
        if (attempt < properties.getMaxAttempt()) {
            retrySender.sendRetry(articleId, attempt + 1);
            return;
        }
        Article article = articleDao.getById(articleId);
        if (article == null || article.getDeletedAt() != null) {
            pendingContentMapper.deleteById(articleId);
            return;
        }
        if (newReviewing || "ai_reviewing".equals(article.getStatus())) {
            article.setStatus("pending_review");
        }
        article.setReviewStatus("manual");
        article.setReviewReason(MANUAL_FALLBACK_REASON);
        article.setUpdateAt(LocalDateTime.now());
        articleDao.updateById(article);
        moderationService.writeLog(articleId, "ai_manual", MANUAL_FALLBACK_REASON, "ai");
    }

    /** 读正文（新文章审核用；读不到时给空串，由 AI 对空正文判 manual/approve） */
    private String loadContentMd(String articleId) {
        ArticleContent content = contentDao.getById(articleId);
        return content != null && content.getContentMd() != null ? content.getContentMd() : "";
    }
}
```

> 消息体解析：`ArticleModerationMessage` 在 oy-blog-common，本模块依赖 common；`@RabbitListener` 参数直接用 `ArticleModerationMessage body`（默认 Jackson converter 由 Spring Boot 自动配置）。import 补 `com.oyproj.common.mq.domain.ArticleModerationMessage`。

- [ ] **Step 4: 运行确认通过 + 全量回归**

Run: 同 Step 2 命令 + 全量回归命令（Global Constraints 模板不带 -Dtest）
Expected: 10 个测试全 PASS；全量回归无新增失败

- [ ] **Step 5: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add oy-blog-service/article-service/src/main/java/com/oyproj/consumer/ArticleModerationConsumer.java oy-blog-service/article-service/src/main/java/com/oyproj/service/ArticleIndexMessageService.java oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ModerationAdminBizServiceImpl.java && git commit -m "feat: 审核消费者（幂等闸+三态流转+失败重试/转人工）+ loadTagNames 共享抽取"
```

---

## Task 6: ModerationStuckScanner（兜底扫描）

**Files:**
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/scheduler/ModerationStuckScanner.java`
- Test: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/test/java/com/oyproj/scheduler/ModerationStuckScannerTest.java`

**Interfaces:**
- Consumes: Task 2 `ModerationProperties`（stuckTimeoutMinutes/scanIntervalMs）、已有 `articleDao/pendingContentMapper/moderationService`
- Produces: 兜底语义——`ai_reviewing` 超 15 分钟 → 转人工（新文章 status=pending_review；编辑 review_status=manual 保留待生效区）

- [ ] **Step 1: 写失败测试**

```java
package com.oyproj.scheduler;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.config.ModerationProperties;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticlePendingContent;
import com.oyproj.dto.ArticleDao;
import com.oyproj.mapper.ArticlePendingContentMapper;
import com.oyproj.service.ModerationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 兜底扫描测试：超时转人工、未超时不动
 */
@ExtendWith(MockitoExtension.class)
class ModerationStuckScannerTest {

    @Mock private ArticleDao articleDao;
    @Mock private ArticlePendingContentMapper pendingContentMapper;
    @Mock private ModerationService moderationService;

    private ModerationStuckScanner scanner;

    @BeforeAll
    static void initI18n() throws Exception {
        Field field = I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, (MessageSource) new StaticMessageSource());
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ModerationProperties props = new ModerationProperties();
        scanner = new ModerationStuckScanner(articleDao, pendingContentMapper, moderationService, props);
    }

    private Article stuckNew() {
        Article a = new Article();
        a.setId("a1");
        a.setStatus("ai_reviewing");
        a.setReviewStatus("ai_reviewing");
        a.setUpdateAt(LocalDateTime.now().minusMinutes(20)); // 超过 15 分钟
        return a;
    }

    @Test
    void stuckNewArticleGoesManual() {
        // 两个查询各 stub 一次：第一次（ai_reviewing 列表）返回超时文章，第二次（编辑待审列表）返回空
        when(articleDao.list(any(Wrapper.class))).thenReturn(List.of(stuckNew()), java.util.Collections.emptyList());
        when(pendingContentMapper.selectById("a1")).thenReturn(null);

        scanner.scanStuck();

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao).updateById(captor.capture());
        assertEquals("pending_review", captor.getValue().getStatus());
        assertEquals("manual", captor.getValue().getReviewStatus());
        assertEquals("审核超时，转人工审核", captor.getValue().getReviewReason());
        verify(moderationService).writeLog("a1", "ai_manual", "审核超时，转人工审核", "system");
    }

    @Test
    void freshReviewingArticleIsNotTouched() {
        Article fresh = stuckNew();
        fresh.setUpdateAt(LocalDateTime.now().minusMinutes(5)); // 未超 15 分钟
        when(articleDao.list(any(Wrapper.class))).thenReturn(List.of(fresh), java.util.Collections.emptyList());

        scanner.scanStuck();

        verify(articleDao, never()).updateById(any(Article.class));
    }

    @Test
    void stuckEditReviewGoesManualAndKeepsPending() {
        Article published = new Article();
        published.setId("a1");
        published.setStatus("published");
        published.setReviewStatus("ai_reviewing");
        published.setUpdateAt(LocalDateTime.now());
        // 第一次查询（ai_reviewing 列表）返回空，第二次查询（编辑待审列表）返回该文章
        when(articleDao.list(any(Wrapper.class))).thenReturn(java.util.Collections.emptyList(), List.of(published));
        ArticlePendingContent pending = ArticlePendingContent.builder()
                .articleId("a1").pendingTitle("新标题").createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now().minusMinutes(20))
                .build();
        when(pendingContentMapper.selectById("a1")).thenReturn(pending);

        scanner.scanStuck();

        verify(pendingContentMapper, never()).deleteById(anyString()); // 待生效区保留，进人工队列
        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao).updateById(captor.capture());
        assertEquals("published", captor.getValue().getStatus()); // 旧版继续展示
        assertEquals("manual", captor.getValue().getReviewStatus());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: Global Constraints 模板（`-Dtest=ModerationStuckScannerTest`）
Expected: 编译失败（类不存在）

- [ ] **Step 3: 实现**

```java
package com.oyproj.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oyproj.config.ModerationProperties;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticlePendingContent;
import com.oyproj.dto.ArticleDao;
import com.oyproj.mapper.ArticlePendingContentMapper;
import com.oyproj.service.ModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审核兜底扫描：防 MQ 消息丢失/消费端挂死导致文章卡死在"AI 审核中"。
 * 每 scanIntervalMs 扫描一次，ai_reviewing 超 stuckTimeoutMinutes 无结果 → 转人工（fail-closed）。
 * 幂等：转人工后状态已非审核中，消费者幂等闸会跳过迟到消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationStuckScanner {

    private static final String STUCK_REASON = "审核超时，转人工审核";

    private final ArticleDao articleDao;
    private final ArticlePendingContentMapper pendingContentMapper;
    private final ModerationService moderationService;
    private final ModerationProperties properties;

    @Scheduled(fixedDelayString = "${oy-blog.article.moderation.scan-interval-ms:300000}",
               initialDelayString = "${oy-blog.article.moderation.scan-interval-ms:300000}")
    public void scanStuck() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(properties.getStuckTimeoutMinutes());
        // 新文章：status=ai_reviewing 且超时
        List<Article> stuckNew = articleDao.list(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "ai_reviewing")
                .lt(Article::getUpdateAt, deadline));
        for (Article article : stuckNew) {
            article.setStatus("pending_review");
            article.setReviewStatus("manual");
            article.setReviewReason(STUCK_REASON);
            article.setUpdateAt(LocalDateTime.now());
            articleDao.updateById(article);
            moderationService.writeLog(article.getId(), "ai_manual", STUCK_REASON, "system");
            log.warn("审核超时转人工（新文章）, articleId: {}", article.getId());
        }

        // 编辑待审：review_status=ai_reviewing 且待生效区行超时
        List<Article> stuckEdits = articleDao.list(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "published")
                .eq(Article::getReviewStatus, "ai_reviewing"));
        for (Article article : stuckEdits) {
            ArticlePendingContent pending = pendingContentMapper.selectById(article.getId());
            if (pending == null || pending.getUpdatedAt() == null || pending.getUpdatedAt().isAfter(deadline)) {
                continue;
            }
            article.setReviewStatus("manual");
            article.setReviewReason(STUCK_REASON);
            article.setUpdateAt(LocalDateTime.now());
            articleDao.updateById(article);
            moderationService.writeLog(article.getId(), "ai_manual", STUCK_REASON, "system");
            log.warn("审核超时转人工（编辑待审）, articleId: {}", article.getId());
        }
    }
}
```

- [ ] **Step 4: 运行确认通过 + 全量回归**

Run: 同 Step 2 命令 + 全量回归
Expected: 3 个测试全 PASS；全量回归无新增失败

- [ ] **Step 5: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add oy-blog-service/article-service/src/main/java/com/oyproj/scheduler/ModerationStuckScanner.java && git commit -m "feat: 审核兜底扫描（15 分钟超时转人工，防消息丢失卡死）"
```

---

## Task 7: 回归 + 验收文档更新

**Files:**
- Modify: `/g/JavaWorkSpace/oy-blog-dev1/doc/article-moderation-acceptance.md`

- [ ] **Step 1: 全量回归**

Run: `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/article-service -am test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 全部通过（既有 47 个中 publish 相关已重构 + 新增 producer/retry/consumer/scanner 测试；总数以实际为准，无失败）

- [ ] **Step 2: 更新验收文档**

在 `doc/article-moderation-acceptance.md` 追加"异步化"一节：

```markdown
## 异步化补充（2026-08-29，spec: docs/superpowers/specs/2026-08-29-async-moderation-design.md）

### 本机已验证
- [X] 单测：publish 异步化（提交即审/审核中守卫/编辑待生效）重构全绿
- [X] 单测：审核消费者幂等闸四分支 + 三态流转 + 失败重试/转人工
- [X] 单测：延迟重试 TTL 序列（10s/30s/90s）+ x-attempt 头
- [X] 单测：兜底扫描超时转人工
- [X] article-service 全量测试通过

### 待部署后验证（🔶 本机无 RabbitMQ/MySQL）
- [ ] 发布秒回"AI 审核中"→ 1 分钟内列表状态自动流转
- [ ] 编辑已发布文章 → 旧版持续展示 → 通过后自动替换
- [ ] 断 BlogAgent → 观察重试回路（10s/30s/90s）→ 转人工
- [ ] 杀消费端/丢消息 → 15 分钟兜底扫描转人工
- [ ] 审核中删除文章 → 消费端收尾
- [ ] 幂等实测：手工重发消息无害

### 前端配合项（另行处理）
- [ ] publish 返回 verdict=ai_reviewing → 关闭 loading、提示"已提交审核"
- [ ] 创作中心列表 10~20 秒自动轮询，按 status/reviewStatus 显示"AI 审核中/编辑审核中/待人工审核/已驳回+原因"
```

- [ ] **Step 3: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add doc/article-moderation-acceptance.md && git commit -m "docs: 验收文档补异步化小节（已验证/待部署/前端配合三清单）"
```

---

## 自审记录（plan 写完后的核对）

**Spec 覆盖检查：**
- §2 状态机 ai_reviewing → Task 4（落库）+ Task 5（流转）
- §4.1 发布秒回 → Task 4；§4.2 编辑异步 → Task 4；§4.3 幂等闸四分支 → Task 5 测试 4 例
- §4.4 失败路径 → Task 3（TTL 重试）+ Task 5（attempt 判定）+ Task 6（兜底扫描）
- §5.1 MQ 基建 → Task 1；§5.2 各组件 → Task 2/3/4/5/6；§5.3 零改动方 → 计划未触碰 Python/DB/admin-service
- §6 测试方案 → 各任务 Step 1
- §8 风险"幂等是正确性核心" → Task 5 幂等闸独立测试；"重试回路 RabbitMQ 细节需实测" → Task 7 待部署清单

**遗留事项（有意不在本计划内）：**
- RabbitMQ 重试回路的 TTL 死信行为（header/expiration 保留）需部署后实测——本机无 RabbitMQ，Task 7 已列实测项
- 前端轮询与提示属前端仓库，Task 7 记录配合项
- 同步版 spec/验收记录保留为历史，不删除不改写
