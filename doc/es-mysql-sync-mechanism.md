# ES-MySQL 文章索引同步机制

## 概述

oy-blog 使用 **Elasticsearch** 提供文章全文搜索能力，**MySQL** 作为数据主存储。两者之间通过 **双层同步策略** 保持数据一致：

| 层级 | 机制 | 延迟 | 用途 |
|------|------|------|------|
| Layer 1 | RabbitMQ 实时消息 | 秒级 | 文章发布/更新/删除后即时同步 |
| Layer 2 | 定时全量对账 | 分钟级（默认 30 分钟） | 修复遗漏消息、清理僵尸数据、兜底保障 |

设计原则：**MySQL 是唯一数据源，ES 是派生搜索索引**。所有修复方向为 MySQL → ES，绝不反向。

---

## 架构图

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Layer 1: 实时增量同步 (MQ)                      │
│                                                                        │
│  article-service (生产者)                search-service (消费者)         │
│  ┌────────────────────┐   RabbitMQ     ┌──────────────────────────┐   │
│  │ publish()  → CREATE/UPDATE ─────────→│ ArticleIndexConsumer     │   │
│  │ delete()   → DELETE  ──────────────→│   ↓ save/delete          │   │
│  │            ↓ 失败?                   │ Elasticsearch            │   │
│  │       mq_retry_log 表               │   ↓ 消费失败?            │   │
│  │       → RetryMqScheduler 重试        │   Dead Letter Queue      │   │
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

## Layer 1: 实时 MQ 同步

### 数据流

```
1. 用户操作（发布/更新/删除文章）
2. article-service 写入 MySQL（事务内）
3. TransactionSynchronization.afterCommit() 回调
4. ArticleMessageProducer 发送消息到 RabbitMQ
5. search-service ArticleIndexConsumer 消费消息
6. 写入/删除 Elasticsearch 文档
```

### 消息格式

**ArticleIndexMessage** (位于 `oy-blog-common`)

| 字段 | 类型 | 说明 |
|------|------|------|
| `operation` | MQOperation | CREATE / UPDATE / DELETE |
| `articleId` | String | 文章 ID |
| `title` | String | 标题 |
| `summary` | String | 摘要 |
| `contentMd` | String | Markdown 原文（消费者侧清洗为纯文本供 IK 分词） |
| `author` | String | 作者名 |
| `authorId` | String | 作者 ID |
| `viewCount` | Long | 浏览数 |
| `likeCount` | Long | 点赞数 |
| `commentCount` | Long | 评论数 |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |
| `status` | String | 文章状态 |
| `tags` | List\<String\> | 标签 |
| `operationTime` | LocalDateTime | 操作时间 |

### RabbitMQ 拓扑

```
article.index.exchange (DirectExchange)
    ├── article.index.queue  (routing-key: article.index)
    │       ↓ DLX → article.index.dlx
    │               ↓ → article.index.dlq
    └── article.delete.queue (routing-key: article.delete)
            ↓ DLX → article.index.dlx
                    ↓ → article.index.dlq
```

### 容错机制

| 环节 | 机制 | 说明 |
|------|------|------|
| 生产者发送失败 | `mq_retry_log` 表 + `RetryMqScheduler` 重试 | 每分钟重试，最多 5 次 |
| 消费者处理失败 | DLQ (Dead Letter Queue) | 重试 3 次后退避进入 DLQ |
| 消息丢失 | Layer 2 全量对账兜底 | 30 分钟内修复 |

---

## Layer 2: 定时全量对账

### IndexReconciler

位置：`search-service/.../scheduler/IndexReconciler.java`

执行流程：
1. 通过 Feign 分页拉取 article-service 全量已发布文章
2. 每批 100 条，批量 upsert 到 ES（全量快照，最后写入胜出）
3. Scroll 查询 ES 中所有文档 ID
4. 删除 ES 中存在但 MySQL 中不存在的文档（僵尸清理）

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

## 关键配置汇总

### search-service (application.yml)

```yaml
spring:
  elasticsearch:
    uris: http://192.168.200.130:9200
  rabbitmq:
    # ... 连接信息
    listener:
      simple:
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1000
          multiplier: 2
          max-interval: 10000
        default-requeue-rejected: false    # 失败后进 DLQ，不重回队列

oy-blog:
  sync:
    reconcile-interval-ms: 1800000
    reconcile-initial-delay-ms: 60000
```

### article-service (application.yml)

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true

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
    message_body TEXT NOT NULL,              -- JSON
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

> **注意**：索引由 Spring Data Elasticsearch 根据 `ArticleDocument` 注解自动创建，无需手动建 mapping。但 **IK 分词器插件必须提前安装在 ES 服务端**。

---

## 运维指南

### 首次部署 / 索引重建

> **注意**：先停 search-service 再删索引，避免运行中的 MQ 消费者在删索引后写入，
> 触发 ES 服务端 auto_create_index 自动建出**无 mapping 的索引**。

```bash
# 0. 停掉 search-service

# 1. 删除旧索引（如果存在）
curl -X DELETE http://192.168.200.130:9200/articles

# 2. 启动 search-service（自动按 @Setting/@Field 注解创建索引 mapping）

# 3. 手动触发全量重建（不触发也会在 60s 后由对账调度器自动补齐）
curl -X POST http://localhost:8099/admin/reindex
```

### 排查数据不一致

```bash
# 查看对账状态
curl http://localhost:8099/admin/sync-status

# 查看 MySQL 文章数
# SELECT COUNT(*) FROM article WHERE status='published' AND deleted_at IS NULL;

# 查看 ES 文档数
curl http://192.168.200.130:9200/articles/_count

# 查看死信队列中的失败消息
# 在 RabbitMQ 管理界面查看 article.index.dlq
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
| `common/.../mq/domain/ArticleIndexMessage.java` | common | MQ 消息体定义 |
| `common/.../mq/config/RabbitMQConfig.java` | common | 队列/交换机/DLQ 声明 |
| `common/.../mq/constants/ArticleMQConstant.java` | common | MQ 常量 |
| `article-service/.../impl/ArticleBizServiceImpl.java` | article | 生产者触发点（publish/delete） |
| `article-service/.../impl/ArticleMessageProducerImpl.java` | article | MQ 生产者 + 降级存储 |
| `article-service/.../scheduler/RetryMqScheduler.java` | article | 失败消息重试调度 |
| `article-service/.../controller/ArticleIndexController.java` | article | 对账数据快照端点 |
| `search-service/.../consumer/ArticleIndexConsumer.java` | search | MQ 消费者 |
| `search-service/.../scheduler/IndexReconciler.java` | search | 全量对账调度器 |
| `search-service/.../controller/AdminSyncController.java` | search | 手动重建/状态查询 API |
| `search-service/.../util/MarkdownSanitizer.java` | search | Markdown 清洗工具 |
| `search-service/.../entity/ArticleDocument.java` | search | ES 文档映射 |
| `service-api/.../ArticleIndexClient.java` | api | Feign 接口 |
| `doc/sql/mq_retry_log.sql` | doc | DDL 脚本 |

---

## 设计取舍

| 决策 | 原因 |
|------|------|
| 不做 Canal/Debezium CDC | 博客规模不需要；额外运维成本高于收益 |
| 不做 Kafka 迁移 | RabbitMQ 已满足需求；迁移无收益 |
| 全量快照对账而非增量 | 文章量级小，全量更简单可靠，自动修复所有历史问题 |
| 同步发送而非异步 | publish 非热点路径，同步发送消除消息丢失窗口 |
| 统计数走 MQ 快照而非实时 | 浏览/点赞高频更新不适合逐次 MQ；30 分钟对账即可同步最新统计 |
