# ES-MySQL 文章索引同步机制

## 概述

oy-blog 使用 **Elasticsearch** 提供文章全文搜索能力，**MySQL** 作为数据主存储。两者之间通过 **双层同步策略** 保持数据一致：

| 层级 | 机制 | 延迟 | 用途 |
|------|------|------|------|
| Layer 1 | Redpanda 压实主题 `article.index` 实时事件流 | 秒级 | 文章发布/更新/删除后即时同步 |
| Layer 2 | 定时全量对账（Feign 快照） | 分钟级（默认 30 分钟） | 修复遗漏消息、清理僵尸数据、兜底保障 |

设计原则：**MySQL 是唯一数据源，ES 是派生搜索索引**。所有修复方向为 MySQL → ES，绝不反向。

> **传输层已于 2026-09-13 从 RabbitMQ 迁移到 Redpanda 压实主题 `article.index`。**
> 迁移的核心收益是**可重放**：索引事件成为一条事件流，"从 topic 重建整个 ES 索引"不再依赖
> article-service 拉快照（见下文「重放重建索引」一节）。
> 设计文档：`docs/superpowers/specs/2026-09-13-es-index-kafka-migration-design.md`
> （前置的方案 A：`docs/superpowers/specs/2026-09-12-redpanda-event-stream-design.md`）。
>
> **AI 审核链路仍在 RabbitMQ 上，本次一行未改**——它依赖 TTL + DLX 的延迟重试阶梯
> （`[10s, 30s, 90s]`），而 Kafka 没有原生延迟消息。由消息语义决定载体，不由技术新旧决定。
> 审核链路的机制见 `doc/article-moderation-mechanism.md`。

---

## 架构图

```
┌──────────────────────────────────────────────────────────────────────┐
│       Layer 1: 实时增量同步（Redpanda 压实主题 article.index）          │
│                                                                        │
│  article-service (生产者)              search-service (消费者)          │
│  ┌────────────────────┐ article.index ┌──────────────────────────┐   │
│  │ publish() CREATE/UPDATE ────────────→│ ArticleIndexConsumer    │   │
│  │ delete()  tombstone(value=null) ────→│  value=null → 按 KEY 删 │   │
│  │            ↓ 发送失败?                │  ↓ 正常：upsert         │   │
│  │       mq_retry_log 表                │ Elasticsearch           │   │
│  │       → RetryMqScheduler 重试         │  ↓ 消费失败?            │   │
│  │         （DELETE 转 null 再发）       │  @RetryableTopic → DLT  │   │
│  └────────────────────┘                └──────────────────────────┘   │
│                                                                        │
├──────────────────────────────────────────────────────────────────────┤
│                     Layer 2: 定时全量对账 (Scheduled)                    │
│                                                                        │
│  search-service                          article-service               │
│  ┌────────────────────────┐  Feign      ┌──────────────────────┐     │
│  │ IndexReconciler        │──→ GET /internal/index/snapshot ──→│     │
│  │ 每 30 分钟:            │  (分页拉取)   │ 返回已发布文章全量数据 │     │
│  │  1. 全量拉取 MySQL 数据 │             └──────────────────────┘     │
│  │  2. 批量写入 ES         │                                          │
│  │  3. 清理 ES 僵尸文档    │                                          │
│  └────────────────────────┘                                          │
│                                                                        │
│  手动触发: POST /admin/reindex                                        │
│  状态查询: GET  /admin/sync-status                                    │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Layer 1: 实时事件流同步

### 数据流

```
1. 用户操作（发布/更新/删除文章）
2. article-service 写入 MySQL（事务内）
3. TransactionSynchronization.afterCommit() 回调
4. ArticleMessageProducerImpl 发一条记录到 topic article.index
     CREATE/UPDATE → value = ArticleIndexMessage（key = articleId）
     DELETE        → value = null（tombstone，key = articleId）
5. search-service ArticleIndexConsumer 消费
6. 写入/删除 Elasticsearch 文档（_id = articleId）
```

> Kafka 的 `send()` 是异步的，**发送失败不会在调用点抛出**；失败信号来自
> `acks=all` 的确认回调与元数据获取异常（`max.block.ms: 100` 让异常路径快速失败），
> 落库兜底见「容错机制」第 1/2 层。

### 消息格式

**ArticleIndexMessage**（位于 `oy-blog-common`，17 个字段——迁移只换传输层，字段一个没改）

| 字段 | 类型 | 说明 |
|------|------|------|
| `operation` | MQOperation | CREATE / UPDATE / DELETE（tombstone 走 key，不带 body） |
| `articleId` | String | 文章 ID（同时是消息 KEY / 分区键） |
| `slug` | String | URL 友好标识 |
| `title` | String | 标题 |
| `summary` | String | 摘要 |
| `contentMd` | String | Markdown 原文（**生产端已清洗**为纯文本供 IK 分词） |
| `authorName` | String | 作者名 |
| `authorAvatar` | String | 作者头像 |
| `authorId` | String | 作者 ID |
| `viewCount` | Long | 浏览数 |
| `likeCount` | Long | 点赞数 |
| `commentCount` | Long | 评论数 |
| `createdAt` | LocalDateTime | 创建时间 |
| `publishAt` | LocalDateTime | 发布时间 |
| `updatedAt` | LocalDateTime | 更新时间 |
| `status` | String | 文章状态 |
| `tags` | List\<String\> | 标签 |

> 它是**文章的完整快照**，恰好就是 ES 文档的字段来源——所以它天然就是"重建 ES 索引所需的一切"，
> 也是压实主题能当"花名册"用的前提。

### topic 拓扑

```
article.index        （3 分区 / 副本 1 / cleanup.policy=compact / 不设 retention.ms）
    key   = articleId（同时是分区键：同篇保序 + 跨篇并行）
    value = ArticleIndexMessage 的 JSON，或 null（tombstone）
```

`@RetryableTopic` 在消费失败时自动建的辅助 topic（1 分区 1 副本，无需人工维护）：

```
article.index-retry-1000 → article.index-retry-2000 → article.index-dlt
```

> 迁移前的 `article.index.exchange` / `article.index.queue` / `article.delete.queue` /
> `article.index.dlx` / `article.index.dlq` **已从代码中删除**；RabbitMQ 里的队列是 durable 的，
> 删代码声明不会删已有队列（回滚时旧 jar 启动即幂等重建，无需手工建拓扑）。

### 删除语义：tombstone

- **删除 = 发一条 `value = null` 的记录**（Kafka 原生 tombstone），而不是带内容的 DELETE 消息。
  生产端：`ArticleMessageProducerImpl.sendArticleDeleteMessage()` → `kafkaTemplate.send(topic, articleId, null)`。
- **文章 id 在消息 KEY 上。** 分区键就是 `articleId`，tombstone **没有 body**，消费端只能从 key 取 id：
  `ArticleIndexConsumer.handleArticleIndex(ConsumerRecord<String, ArticleIndexMessage> record)` 里
  `record.value() == null` 的分支 → 按 key 删 ES 文档。**任何"从 value 里取 articleId"的写法在删除路径上都是空指针或删错。**
- **降级落库的连带处理**：`mq_retry_log.message_body` 是普通列、存不了 `null`，所以落库存**完整消息**
  （`operation=DELETE`），`RetryMqScheduler` 重发时**再转回 `null`**。漏了这一步，重试路径就会漏发 tombstone。
- 消费端仍保留对"带内容的 DELETE 消息"的兼容分支（迁移期/异常路径），但正常路径现在只有 tombstone 一种。
- tombstone 在 topic 上保留多久由 `delete.retention.ms` 决定（当前 `-1`），见下文「运维须知」。

### 容错机制（四层兜底 + 发布方确认，共 5 道）

| # | 层 | 实现（2026-09-13 起） | 说明 |
|---|----|----------------------|------|
| 0 | 生产者确认 | `acks=all` + `enable.idempotence=true` | 迁移前是 `publisher-confirm-type: correlated` + `publisher-returns` + `template.mandatory`。Kafka 侧**更强**：确认落盘 + 生产者幂等防重发。两个键必须写在 `spring.kafka.producer.properties` 下（`KafkaProperties.Producer` 没有对应绑定目标，写在外层会被静默忽略） |
| 1 | 发送失败落库 | `mq_retry_log` 表（`ArticleMessageProducerImpl`） | **原样保留**（与传输层无关） |
| 2 | 定时重发 | `RetryMqScheduler` 每 60s 扫 PENDING、重发，超 5 次标 FAILED | **保留，改为发 Kafka**；识别 `operation=DELETE` → 转 `null` 发出（见「删除语义」） |
| 3 | 消费失败重试 | `@RetryableTopic(attempts=3, backoff=@Backoff(delay=1000, multiplier=2.0, maxDelay=10000))` **+ `ErrorHandlingDeserializer`** | 替换原来手工搭的"退避重试 3 次 + `article.index.dlq`"（`attempts=3` = 首次 + 2 次重试，间隔 1s、2s；`maxDelay` 只是封顶）。**两者都要**：反序列化异常发生在 `@KafkaListener` 方法体**之前**，只有 `@RetryableTopic` 挡不住毒消息——它会变成"重新 poll 同一条 → 位点永不前进"的无限热循环，比"重试 3 次"严重得多 |
| 4 | 全量对账 | `IndexReconciler` 每 30 分钟 | **原样保留**（与传输层无关，是最后一道防线） |

> **换 Kafka ≠ 不用写兜底。** Kafka 是 offset 提交制：消费失败若不提交 offset 会**阻塞整个分区**
> （队头阻塞），RabbitMQ 的 DLX 天然无此问题。所以第 3 层不是消失，而是从"手工搭 DLX/DLQ"
> 变成"一个注解"。
>
> **两条链路的取舍相反**：热榜链路"catch 一切异常照样 ack"（装饰功能，绝不阻塞分区），
> 索引链路必须**成功才提交**（ES 索引错了用户直接搜到）。这是由业务重要性决定的，不是不一致。

---

## Layer 2: 定时全量对账

### IndexReconciler

位置：`search-service/.../scheduler/IndexReconciler.java`

执行流程：
1. 通过 Feign 分页拉取 article-service 全量已发布文章（`pageNum` 从 **1** 开始，见下方注意事项）
2. 每批 100 条，批量 upsert 到 ES（全量快照，最后写入胜出）
3. Scroll 查询 ES 中所有文档 ID
4. 删除 ES 中存在但 MySQL 中不存在的文档（僵尸清理）

> ⚠️ **分页页号是 1-based**：`/internal/index/snapshot` 的 `pageNum` 直达 MyBatis-Plus 的
> `new Page<>(pageNum, size)`，而 MP 的 `Page` 是 1-based（`IPage.offset()`：`current <= 1 → 0`）——
> 传 0 与传 1 命中**同一页**。从 0 开始翻会重复第一页、此后页码整体错位一页；若循环以总页数为上界，
> 会更早 break 而**漏掉末尾那一页**（对账器曾经正是这样把末尾文章漏出权威集合、当僵尸误删，
> 见提交 `865a8ad`）。另外**不能靠"某页为空"终止**：生产拦截器 `overflow=true` 在 `current > pages` 时
> 会把 current 拨回 1（第一页），越界翻页永远拿得到非空页 → 死循环。细节见
> `MybatisPlusConfig` / `ArticleIndexClient` / `IndexReconciler` 的注释。

### 对账配置

```yaml
# search-service application.yml
oy-blog:
  sync:
    reconcile-interval-ms: 1800000     # 对账间隔，默认 30 分钟
    reconcile-initial-delay-ms: 60000  # 启动后首次对账延迟，默认 60 秒
```

### 手动操作

```bash
# 手动触发全量重建
curl -X POST http://localhost:8099/admin/reindex

# 查询最近一次对账状态
curl http://localhost:8099/admin/sync-status
```

响应示例：
```json
{
  "data": {
    "startTime": "2026-08-10T03:00:00",
    "upserted": 150,
    "orphansDeleted": 3,
    "httpErrors": 0,
    "durationMs": 2350,
    "completed": true
  }
}
```

---

## 重放重建索引（topic → ES）

### 为什么成立

`article.index` 是**压实主题**（`cleanup.policy=compact`）：每个 key（= articleId）只保留最新一条值。
新消费者从 offset 0 顺序读一遍，后者覆盖前者、最后一条胜出——**读完整即得"所有文章的当前状态"**，
也就是重建了整个 ES 索引。压实是后台周期性执行的，故 topic 内可能同时存在同一 key 的新旧多版本，
**无害**（与 ES 侧 `_id = articleId` 的 upsert 行为一致）。

迁到压实主题的价值就在这里：**topic 本身就是一份全量快照**，重建不依赖 article-service 的 Feign 快照端点
（也就绕开了 `865a8ad` 那条"快照拉取失败 → 误清索引"的失败路径）。

### 四步流程

```bash
# ① 停 search-service —— 消费组必须为空，否则 seek 被拒
docker stop oy-blog-search-service
#    轮询确认组真的空了（STATE=Empty / MEMBERS=0）：
docker exec redpanda rpk group describe article-index -X brokers=redpanda:29092

# ② 把消费组位点归零
docker exec redpanda rpk group seek article-index --to start --topics article.index -X brokers=redpanda:29092
#    TOPIC          PARTITION  PRIOR-OFFSET  CURRENT-OFFSET
#    article.index  0          9036          0
#    article.index  1          5028          0
#    article.index  2          10036         0
#    ↑ rc=0、三分区 CURRENT-OFFSET 全为 0 才算成功

# ③ 启动 search-service —— 它从 offset 0 顺序读、逐条 upsert（_id = articleId）
docker start oy-blog-search-service
#    确认真的从 0 开始：rpk group describe article-index → CURRENT-OFFSET=0、TOTAL-LAG>0
#    读完后 TOTAL-LAG 归 0（生产实测 24,100 条约 38 分钟，约 630 条/分）

# ④ 核对重建结果：ES 文档数 == 库内已发布数
curl http://192.168.200.130:9200/articles/_count
# SELECT COUNT(*) FROM article WHERE status='published' AND deleted_at IS NULL;
```

> 第 ④ 步若不一致：先等一轮 `IndexReconciler`（30 分钟）再对；仍不一致时查 `article.index-dlt`
> 与消费端日志（`文章索引成功` / `收到索引 tombstone`）。
>
> 若第 ③ 步发现消费端没从 0 开始：说明 `seek` 实际没生效（多半是组当时非空），回到第 ① 步重来。

### 三个坑

| 坑 | 现象 | 做法 |
|----|------|------|
| 容器内跑 `rpk` 必须显式给 brokers | 不带 `-X brokers=redpanda:29092` 时 `rpk` 按默认 `localhost:9092` 去连，命令失败或返回空 | 每条 `rpk` 命令都带 `-X brokers=redpanda:29092` |
| 组非空时 `seek` 被拒 | 报 `seeking a non-empty group is not allowed` | 先停 search-service，并**轮询到组真的变 Empty** 再 seek（本地观察要等 ~45s 让 session 超时；生产 `docker stop` 后组会立刻变 Empty） |
| 手工翻 `/internal/index/snapshot` 必须显式传 `pageNum` | 该端点的 `@RequestParam(defaultValue = "0")` 是**历史遗留**：它直达 MP 的 `new Page<>(pageNum, size)`，而 MP 的 Page 是 **1-based**（`current <= 1 → 0`）——**传 0 与传 1 命中同一页**，逐页翻会重复第一页、并整体错位一页 | 显式传 `pageNum`，从 **1** 开始，别依赖默认值 |

> 重放**只读 topic、只写 ES**：不动 MySQL，也不改 topic 本身。过程中**不要**执行
> `rpk topic trim-prefix` 或任何 topic 配置变更（理由见下一节）。

---

## 运维须知：topic 的四个安全前提

**这四条是"重放能力"成立的前提。动它们之前先读完本节。**

### 1. `article.index` 必须保持 `cleanup.policy=compact`，**永远不要加 `delete`**

本 topic **刻意不设 `retention.ms`**，但集群有一个继承来的默认值（实测 `604800000` = 7 天，
来源 `DEFAULT_CONFIG`）。只在 `compact` 下它**惰性不生效**；一旦改成 `compact,delete`，
这个 7 天保留**立刻生效**——而 Kafka 的保留是按**段**判定的：一个段里**最新**的记录超过保留期，
整段连同其全部记录一起删除，**包括某篇文章的唯一最新版本**。于是**"长期没更新的文章"会从 topic 里消失**，
重放再也重建不出它。存储成本本就极小（每篇一条：68 篇 ≈ 136 KB，10 万篇 ≈ 200 MB），这个交换不划算。

### 2. 真判别式是 `LOG-START-OFFSET` 恒为 **0**

```bash
docker exec redpanda rpk topic describe article.index -p -X brokers=redpanda:29092
# PARTITION  LEADER  EPOCH  REPLICAS  LOG-START-OFFSET  HIGH-WATERMARK
# 0          ...                        0                 ...
```

任何时间/大小删除或 `trim-prefix` 都会**推进** `LOG-START-OFFSET`；它一旦 > 0，就说明保留正在生效、
**重放已经残缺**。基线取于 **2026-09-13**（三分区均为 0，当时 topic 刚建、为空），
**应在 2026-09-20（topic 满 7 天）之后复核仍为 0**。

- ⚠️ **重放演练查不出这件事**：topic 建于 2026-09-13，任何段都达不到 7 天龄——演练当天无论通过与否，
  都**不能证伪**"时间删除在生效"。演练要有"段龄 > 保留期、且含长期未更新文章"的语料才有判别力。
- ⚠️ 重放会把**消费组位点**归零，所以"位点归零"不能反过来当"topic 没被裁过"的证据；
  判据只看 `LOG-START-OFFSET`（配合 `HIGH-WATERMARK` 一起看）。

### 3. `delete.retention.ms` 必须保持 `-1`

它是**唯一一个对 compact-only 主题真正生效的保留开关**（tombstone 的删除视界）。
当前 `-1`（不按时间清除）**正好是重放安全的那一侧**；一旦变成正数，压实会清掉 tombstone，
**"删除"在重放里就彻底不可见了**——重建出来的索引会把已删文章当成存在的。应与"永不加 `delete`"并列受监控。

### 4. `spring.rabbitmq.template.*` 对本项目是**空操作**，别再往里加东西

本项目自定义了 `RabbitTemplate` bean（`RabbitMQConfig.rabbitTemplate(ConnectionFactory)`），
而 Boot 的自动配置带 `@ConditionalOnMissingBean`，被这个 bean 顶掉 →
**yml 里的 `spring.rabbitmq.template.*` 根本到不了那个 `RabbitTemplate`**。

今天唯一受影响的是 `template.mandatory`：它已经在代码里 `setMandatory(true)`（不设的话，
即使 `publisher-returns: true`，不可路由的消息也不会触发 `returnsCallback`，会静默丢弃）。
所以 yml 里那行 `spring.rabbitmq.template.mandatory: true` 是**死配置**；
**不要**以为加 `spring.rabbitmq.template.retry.*` 会生效——那里的重试参数永远不会被读到。

---

## 关键配置汇总

### search-service (application.yml)

```yaml
spring:
  elasticsearch:
    uris: http://192.168.200.130:9200
    # 索引的创建/mapping 由启动时的 IndexInitializer 按 ArticleDocument 的 @Field 注解完成
    # （索引不存在则 createWithMapping，存在则 putMapping 增量同步）
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:192.168.200.130:9092}
    consumer:
      group-id: article-index
      auto-offset-reset: earliest        # 组内无位点时从 topic 头读起 = "重建索引"语义；已有位点则续读
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      # 【必须包一层】把反序列化失败从 poll() 移到记录级，毒消息才会走重试→DLT 通道
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.value.default.type: com.oyproj.common.mq.domain.ArticleIndexMessage
        spring.json.trusted.packages: com.oyproj.common.mq.domain
    producer:
      # @RetryableTopic 要把失败记录转发到 retry topic，消费端也需要生产者配置
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        spring.json.add.type.headers: false

oy-blog:
  sync:
    reconcile-interval-ms: 1800000
    reconcile-initial-delay-ms: 60000
```

### article-service (application.yml)

```yaml
spring:
  # RabbitMQ 现在只服务 AI 审核链路（索引链路已迁走；队列/交换机声明在 ModerationRabbitConfig）
  rabbitmq:
    host: ${MQ_HOST:192.168.200.130}
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true        # ← 死配置：被自定义 RabbitTemplate bean 顶掉，实际生效的是代码里的 setMandatory(true)
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:192.168.200.130:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all              # 第 0 层：确认落盘
      properties:
        # 以下三行【必须写在 properties 里】：KafkaProperties.Producer 没有对应绑定目标，
        # 写在外层（spring.kafka.producer.enable-idempotence / max-block-ms）会被静默忽略
        enable.idempotence: true          # 第 0 层：生产者幂等，防重发
        max.block.ms: 100                 # send() 在用户请求线程上同步调用，异常路径必须快速失败
        spring.json.add.type.headers: false

oy-blog:
  mq:
    retry-interval-ms: 60000
    retry-initial-delay-ms: 30000
```

---

## 数据库表

### mq_retry_log（MQ 重试日志）

```sql
CREATE TABLE mq_retry_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_type VARCHAR(50) NOT NULL,       -- ARTICLE_INDEX / ARTICLE_DELETE
    message_body TEXT NOT NULL,              -- JSON（存的是完整消息；DELETE 重发时才转成 null）
    retry_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'PENDING',   -- PENDING / SUCCESS / FAILED
    error_msg VARCHAR(500),
    created_at DATETIME,
    updated_at DATETIME
);
```

---

## ES 索引结构

索引名：`articles`

| 字段 | 类型 | 分词器 |
|------|------|--------|
| `id` | Keyword | - |
| `title` | Text | ik_max_word / ik_smart |
| `content` | Text | ik_max_word / ik_smart |
| `summary` | Text | - |
| `author` | Keyword | - |
| `authorId` | Keyword | - |
| `status` | Keyword | - |
| `tags` | Keyword[] | - |
| `createdAt` | Date | `date_hour_minute_second_millis` |
| `updatedAt` | Date | `date_hour_minute_second_millis` |
| `viewCount` | Long | - |
| `likeCount` | Long | - |
| `commentCount` | Long | - |

> **注意**：索引由 search-service 启动时的 `IndexInitializer` 依 `ArticleDocument` 的注解创建/同步
> （不存在则 `createWithMapping`，存在则 `putMapping` 增量同步新字段），**无需手工建 mapping**。
> 但 **IK 分词器插件必须提前安装在 ES 服务端**。
>
> ⚠️ 已知遗留：现网 `articles` 索引的 settings 里没有 `ik_smart_lc` 分析器，导致每次启动
> `IndexInitializer` 的 `putMapping` 被 ES 拒绝并记一条 ERROR（不影响启动，也不影响写入）。
> 这是迁移前就存在的索引定义漂移，**与传输层迁移无关**，待单独立项。

---

## 运维指南

### 索引重建

> **首选「重放重建索引」**（见上一节）——它从 topic 事件流重建，不依赖 article-service。

只有在 topic 不可用（或需要修正 mapping）时才走"删索引重建"这条老路：

```bash
# 0. 停掉 search-service（避免运行中的消费者在删索引后写入，
#    触发 ES 服务端 auto_create_index 建出**无 mapping 的索引**）

# 1. 删除旧索引（如果存在）
curl -X DELETE http://192.168.200.130:9200/articles

# 2. 启动 search-service（启动时 IndexInitializer 按注解创建索引 + mapping；
#    消费者从消费组**已提交的位点**续读——auto-offset-reset=earliest
#    只在组内没有位点时才生效。想强制从 topic 头全量重建，走「重放重建索引」的 seek）

# 3. 如果 topic 里的内容也不可信/为空，先补播种，再手动触发一次全量重建：
curl -X POST http://<article-service>/internal/index/seed-topic
curl -X POST http://localhost:8099/admin/reindex     # 走 Feign 快照，不触发也会在 60s 后由对账调度器自动补齐
```

### 排查数据不一致

```bash
# 查看对账状态
curl http://localhost:8099/admin/sync-status

# 查看 MySQL 文章数
# SELECT COUNT(*) FROM article WHERE status='published' AND deleted_at IS NULL;

# 查看 ES 文档数
curl http://192.168.200.130:9200/articles/_count

# 查看消费组位点与积压（TOTAL-LAG）
docker exec redpanda rpk group describe article-index -X brokers=redpanda:29092

# 查看重试/死信 topic 里的失败消息（原 RabbitMQ 管理界面的 DLQ 位置）
docker exec redpanda rpk topic consume article.index-dlt -X brokers=redpanda:29092
docker exec redpanda rpk topic consume article.index-retry-2000 -X brokers=redpanda:29092

# 确认 topic 的保留没在起作用（见「运维须知」2）
docker exec redpanda rpk topic describe article.index -p -X brokers=redpanda:29092
```

### 调整对账频率

```yaml
# 开发环境：更频繁
oy-blog.sync.reconcile-interval-ms: 300000   # 5 分钟

# 生产环境：适当放宽
oy-blog.sync.reconcile-interval-ms: 1800000  # 30 分钟（默认）
```

---

## 关键文件索引

| 文件 | 模块 | 说明 |
|------|------|------|
| `common/.../mq/domain/ArticleIndexMessage.java` | common | 索引消息体（17 字段，迁移未改） |
| `common/.../mq/config/KafkaTopicConfig.java` | common | `article.index` topic 声明（3 分区 / compact / 无 retention.ms） |
| `common/.../mq/config/RabbitMQConfig.java` | common | **仅审核链路**：`rabbitTemplate` + JSON 转换器（索引拓扑已删） |
| `common/.../util/MarkdownSanitizer.java` | common | Markdown 清洗工具（生产端用它、消费端不再清洗） |
| `article-service/.../impl/ArticleBizServiceImpl.java` | article | 生产者触发点（publish/delete，afterCommit 后发） |
| `article-service/.../impl/ArticleMessageProducerImpl.java` | article | Kafka 生产者 + 降级落库 + tombstone |
| `article-service/.../scheduler/RetryMqScheduler.java` | article | 失败消息重试调度（发 Kafka；DELETE→null） |
| `article-service/.../service/ArticleIndexSeedService.java` | article | 播种：全量快照 → 逐条确认发 topic（`POST /internal/index/seed-topic`） |
| `article-service/.../controller/ArticleIndexController.java` | article | 对账快照端点 + 播种端点 |
| `search-service/.../consumer/ArticleIndexConsumer.java` | search | `@KafkaListener` + `@RetryableTopic` + tombstone 分支 |
| `search-service/.../scheduler/IndexReconciler.java` | search | 全量对账调度器 |
| `search-service/.../controller/AdminSyncController.java` | search | 手动重建/状态查询 API |
| `search-service/.../config/IndexInitializer.java` | search | 启动时创建索引 / 同步 mapping |
| `search-service/.../entity/ArticleDocument.java` | search | ES 文档映射 |
| `service-api/.../ArticleIndexClient.java` | api | Feign 接口（对账用；`pageNum` 1-based） |
| `doc/sql/mq_retry_log.sql` | doc | DDL 脚本 |

---

## 设计取舍

| 决策 | 原因 |
|------|------|
| 不做 Canal/Debezium CDC | 博客规模不需要；额外运维成本高于收益 |
| **2026-09-13 已迁移到 Kafka 压实主题**（`article.index`） | 原判断"不做 Kafka 迁移：RabbitMQ 已满足需求、迁移无收益"**已被推翻**。迁移的理由不是"Kafka 更时髦"，而是**压实主题让索引事件成为可重放的事件流**：任一时点都能从 topic 重建完整 ES 索引，**且不依赖 article-service 拉快照**；顺带拿到"同篇保序 + 跨篇并行"（原先靠全局单线程换保序）。设计：`docs/superpowers/specs/2026-09-13-es-index-kafka-migration-design.md`（前置方案 A：`docs/superpowers/specs/2026-09-12-redpanda-event-stream-design.md`）。**AI 审核链路不迁**——Kafka 无原生延迟消息，而它依赖 TTL+DLX 的重试阶梯 |
| 全量快照对账而非增量 | 文章量级小，全量更简单可靠，自动修复所有历史问题 |
| 同步发送而非异步 | publish 非热点路径，同步发送消除消息丢失窗口 |
| 统计数走 MQ 快照而非实时 | 浏览/点赞高频更新不适合逐次 MQ；30 分钟对账即可同步最新统计 |
| 索引链路消费失败**必须重试**（与热榜相反） | ES 索引错了用户直接搜到；热榜是装饰功能，绝不阻塞分区。两条链路策略相反是业务重要性决定的 |
| tombstone 走 `value=null` 而非 DELETE 消息体 | Kafka 原生删除语义；压实后该 key 只留最新一条，重放时"删除"与"创建"按同一套 offset 顺序生效 |
