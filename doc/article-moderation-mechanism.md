# 文章 AI 审核机制

> 日期: 2026-09-11 | 状态: **已实现（master）** | 最近更新: 2026-09-12（并发化改造）
> 关联文档:
> - 实现前设计规格: `docs/superpowers/specs/2026-08-29-async-moderation-design.md`（含改动清单、验收清单）
> - 同步版设计: `docs/superpowers/specs/2026-08-29-article-ai-moderation-design.md`
> - 并发化改造设计: `docs/superpowers/specs/2026-09-12-moderation-concurrency-design.md`（两段短事务 + 并发度旋钮）
> - 验收记录: `doc/article-moderation-acceptance.md`
> - 建表 SQL: `doc/sql/article_moderation_migration.sql`、`doc/sql/article_status_enum_to_varchar_migration.sql`
>
> 本文定位：**讲清楚"这条链路到底是怎么跑起来的"以及"为什么这么设计"**，面向需要接手/排障的人。改动清单和验收清单不在此重复。

---

## 一、先看业务：不写一行代码地讲一遍

### 1.1 为什么需要审核

博客允许用户投稿。文章一旦发布就对外可见（列表、详情、ES 检索），所以必须有一道门：**内容先过 AI，过了才放行**。

### 1.2 用户体验长什么样

```
作者点"发布"
   │
   ├─ 0.1 秒后：页面提示"已提交审核"          ← 立刻返回，不等 AI
   │
   ├─ 几秒~十几秒后（前端每 10~20 秒轮询一次列表）：
   │     ├─ 审核通过 → 文章变为"已发布"，列表和搜索里都能看到
   │     ├─ 审核驳回 → 显示"已驳回" + 驳回原因，作者可修改后重发
   │     └─ 拿不准   → 显示"待人工审核"，进管理员队列
   │
   └─ 如果 AI 服务挂了：自动重试 3 次，仍失败 → 也是"待人工审核"
```

**关键体感：作者永远不会"卡在转圈"。** 要么很快出结果，要么落到人工，不会永远停在"审核中"。

### 1.3 为什么要做成异步

最早的同步版是：点发布 → 后端同步调 AI → 等 3~15 秒 → 返回结果。作者就盯着转圈等十几秒，体验很差。

异步版把这段等待挪到后台：发布接口只负责"落库 + 丢一条消息进队列"，立刻返回。AI 调用由队列的消费者在后台慢慢做，做完直接改库里的文章状态，前端轮询自然就看到变化了。

> **代价**：异步意味着"发布成功了但审核还没做"，中间这段时间文章处于一个中间态。整个设计的一大半复杂度，都是在处理这个中间态——状态怎么表示、消息丢了怎么办、消费者挂了怎么办、AI 挂了怎么办。第五节开始都是讲这个。

---

## 二、登场角色

| 角色 | 是什么 | 在哪 |
|---|---|---|
| **article-service** | 主体。接收发布请求、消费审核消息、改文章状态 | 本仓库 `oy-blog-service/article-service` |
| **BlogAgent** | Python 写的 AI 审核服务，HTTP 接口 | **不在本仓库**，`G:\agentWorkplace\BlogAgent` |
| **RabbitMQ** | 消息队列，负责"把审核任务送到后台"和"失败后延迟重投" | 中间件 |
| **MySQL** | 存文章状态、待生效区、审核日志 | 中间件 |
| **Elasticsearch** | 搜索索引。**只有审核通过的文章才会进 ES** | 中间件 |
| **管理员** | 人工审核兜底（AI 判 manual 或各种故障时接管） | admin-service |

审核服务对外只有一个接口：

```
POST {baseUrl}/moderate/article     →  返回 reject / approve / 其它
```

`baseUrl` 由 `oy-blog.article.moderation.base-url` 配置（默认 `http://localhost:8001`）。

---

## 三、状态机：两个字段，别搞混

文章表上有**两个**跟审核有关的字段，这是最容易糊涂的地方，先讲透。

### 3.1 两个字段各自的取值

**`status` —— 这篇文章处于生命周期的哪一段**

| 值 | 含义 | 公众可见 |
|---|---|---|
| `draft` | 草稿 | ❌ |
| `ai_reviewing` | AI 审核中（新文章还没上线） | ❌ |
| `published` | 已发布 | ✅ |
| `pending_review` | 待人工审核 | ❌ |
| `rejected` | 已驳回 | ❌ |

**`review_status` —— 最近一次审核的结论**

| 值 | 含义 |
|---|---|
| `pending` | 从未送审 |
| `ai_reviewing` | 正在审（AI 处理中） |
| `approved` | 通过（AI 或人工） |
| `rejected` | 驳回（AI 或人工） |
| `manual` | 转人工处理中 |
| `exempt` | 豁免，跳过审核 |

### 3.2 为什么非要两个字段

因为有一个状态**用一个字段根本表达不了**：**已发布的文章正在被编辑审核**。

作者改了一篇已经上线的文章。这时候：

- 文章本身还在线上，读者还能看到**旧版** → `status` 必须是 `published`
- 但它提交的**新版内容**正在被 AI 审 → `review_status` 是 `ai_reviewing`

如果只有一个字段，你没法同时说"它在线上"和"它正在被审"。所以拆成两个正交的轴：

> **`status` 管"这篇文章活着吗、对外可见吗"；`review_status` 管"它最近一次送审是什么结论"。**

理解了这一点，下面所有组合就都顺了：

| status | review_status | 实际含义 |
|---|---|---|
| `ai_reviewing` | `ai_reviewing` | 新文章在审，暂不可见 |
| `published` | `ai_reviewing` | **旧版在线，新版在审** ← 只有两字段才能表达 |
| `published` | `approved` | 正常在线 |
| `published` | `exempt` | 豁免放行，直接在线 |
| `pending_review` | `manual` | 等人工审（文章还没上线） |
| `rejected` | `rejected` | 已驳回 |

### 3.3 待生效区 `article_pending_content`

编辑已发布文章时，新版内容不直接覆盖正文，而是先存到**待生效区**这张表。

| 字段 | 说明 |
|---|---|
| `article_id` | 主键，直接复用 `article.id`（**一篇最多一份待审编辑**，这是个刻意的约束） |
| `pending_title` / `pending_summary` / `pending_content_md` / `pending_content_html` | 新版内容 |
| `review_reason` | 审核理由（进人工队列时管理员要看的） |
| `created_at` / `updated_at` | 时间戳，**兜底扫描靠 `updated_at` 判断是否卡死** |

> **类比**：正文是"现在挂在墙上的画"，待生效区是"旁边竖着的新画"。AI 说行，就把旧画摘下来换新的；AI 说不行，新画直接扔掉，墙上那张纹丝不动。

---

## 四、四条业务主流程

### 4.1 新文章发布

```
作者点发布
   │
   ▼
ArticleBizServiceImpl.publish()
   │
   ├─ ① 审核中守卫：已经在 ai_reviewing？ → 拒绝，提示"审核中，请稍候"
   ├─ ② 豁免判定：开关关闭 or 用户是 ADMIN？ → 直接 published/exempt，发 ES，秒过（不走 AI）
   │
   └─ ③ 非豁免 → 落库 status=ai_reviewing, review_status=ai_reviewing
                  │
                  └─ 事务提交后（afterCommit）发 MQ 审核消息
                  └─ 接口立刻返回 {verdict: "ai_reviewing"}
   │
   ▼ （以下全部发生在后台）
Consumer 收到消息 → Workflow 调 BlogAgent → 三态：
   ├─ approve → status=published, review_status=approved
   │            + publishAt=now + 发 ES 索引(CREATE)
   ├─ reject  → status=rejected, review_status=rejected（不发 ES）
   └─ manual  → status=pending_review, review_status=manual → 进人工队列
```

**为什么发布消息要在事务提交后才发**（`sendModerationAfterCommit`，`:136-144`）：

用 `TransactionSynchronizationManager.registerSynchronization` 挂在 `afterCommit` 上。因为消费者拿到 `articleId` 会立刻去 DB 读状态——如果消息比事务先到，消费者读到的还是旧状态，幂等闸会判定"非审核中"直接跳过，这条消息就白发了。**必须等 DB 落定再发。**

**一个容易忽略的细节：驳回重发的 ES 动作是 `CREATE` 不是 `UPDATE`。**

`ArticleBizServiceImpl.java:109-111` 特意区分了这一点：

```java
boolean isRejectedResubmit = existing != null && "rejected".equals(existing.getStatus());
MQOperation op = (isNew || isRejectedResubmit) ? MQOperation.CREATE : MQOperation.UPDATE;
```

因为**驳回路径从不发 ES 索引消息**——被驳回的文章压根没进过索引。所以作者改完重发、审核通过时，ES 里是"新建文档"而不是"更新已有文档"。如果这里错用 `UPDATE`，ES 会找不到文档（或写入一个不完整的文档）。

### 4.2 编辑已发布文章（待生效区上场）

```
作者改一篇已上线的文章，点提交
   │
   ├─ ① 编辑审核中守卫：review_status 已是 ai_reviewing？ → 拒绝（一次只允许一份待审编辑）
   ├─ ② 新版内容写入 article_pending_content（覆盖或新建）
   │     文章 status 保持 published ← 旧版继续展示
   │     review_status 改为 ai_reviewing
   │     封面/允许评论/标签等未审字段立即生效
   └─ ③ 事务提交后发 MQ 审核消息，接口秒回
   │
   ▼ （后台）
Workflow 用【待生效区的内容】去审（不是线上正文）→ 三态：
   ├─ approve → 待生效区内容替换进正文 + 重建章节 + 清待生效区
   │            + review_status=approved + 发 ES 索引(UPDATE)
   ├─ reject  → 清待生效区（本次编辑丢弃），旧版不动，review_status=rejected
   └─ manual  → 待生效区【保留】，review_status=manual → 进人工队列
```

> **注意 approve 和 reject 对旧版的差别**：approve 是"换上新画"，reject 是"扔掉新画、留着旧画"。**任何情况下读者都不会看到被驳回的内容。** 这就是"先审后生效"语义。

### 4.3 人工审核

人工队列有两个类目（`ModerationAdminBizServiceImpl.adminPage`）：

- **NEW 类目**：`status=pending_review` 的文章 —— 新文章没过 AI
- **EDIT 类目**：`article_pending_content` 表里有行的 —— 编辑没过 AI

管理员通过 admin-service 的 `POST /admin/moderation/audit` 通过或驳回，走的是和 AI 完全一样的三态流转（新文/编辑各自一套），只是审核日志的 `operator_id` 从 `"ai"` 变成管理员 ID 或 `"system"`。

### 4.4 豁免路径

两条豁免：`moderation.enabled=false`（总开关关了）或 用户角色在 `exempt-roles` 里（默认 `["ADMIN"]`）。

豁免时**完全不发审核消息**，同步落库 `published` + `review_status=exempt`，直接发 ES 索引。等于把这道门整个拆掉。

---

## 五、MQ 拓扑详解（重点）

这一节回答"那几个常量和两个交换机到底在干嘛"。

### 5.1 为什么用消息队列

需要三个特性：

1. **发布接口和 AI 调用解耦** —— 发布不等审核
2. **失败要能延迟重试** —— AI 服务偶尔抽风，10 秒后重试往往就好了
3. **消息不能丢** —— 丢了文章就永远卡在"审核中"

RabbitMQ 的 **TTL + 死信队列**机制正好能实现第 2 点，这是整个设计里最巧的一块。

### 5.2 涉及四个常量，三个是队列/交换机，一个是路由键

`oy-blog-common/.../mq/constants/ArticleMQConstant.java:16-21`

| 常量 | 值 | 是什么 |
|---|---|---|
| `ARTICLE_MODERATION_EXCHANGE` | `article.moderation.exchange` | 主交换机 |
| `ARTICLE_MODERATION_QUEUE` | `article.moderation.queue` | 主队列，**有消费者** |
| `ARTICLE_MODERATION_RETRY_EXCHANGE` | `article.moderation.retry.exchange` | 重试交换机 |
| `ARTICLE_MODERATION_RETRY_QUEUE` | `article.moderation.retry.queue` | 重试队列，**没有消费者** |
| `ARTICLE_MODERATION_ROUTING_KEY` | `article.moderation` | **路由键**（一个字符串，被三处复用） |

> **交换机（Exchange）、路由键（Routing Key）、队列（Queue）的关系**：
> 生产者从不直接往队列里发消息，而是发给**交换机**，并附上一个**路由键**；交换机再根据路由键决定投进哪个队列。
> 这里用的是 Direct 类型交换机 —— **路由键必须一字不差地匹配绑定规则**才投递。
> 类比：交换机是分拣中心，路由键是信封上的分拣码，队列是收件人的信箱。

### 5.3 主链路

```
Producer                    主交换机                 主队列            消费者
   │                          │                       │                │
   │── articleId ────────────►│                       │                │
   │   routing key=           │── 绑定匹配 ──────────►│── 投递 ───────►│
   │   "article.moderation"   │   "article.moderation"│                │
```

代码：

- 发消息：`ArticleModerationProducerImpl.java:29-33`
- 绑定：`ModerationRabbitConfig.java:46-51`（把主队列绑到主交换机，路由键 = `article.moderation`）
- 消费：`ArticleModerationConsumer.java:26` 的 `@RabbitListener(queues = ARTICLE_MODERATION_QUEUE)`

**消息体极简**，只有一个字段：

```java
ArticleModerationMessage { String articleId; }
```

> 消息体里**不带**文章内容、不带状态、不带重试次数。消费端拿到 `articleId` 后自己去 DB 读最新状态（这是幂等设计的基础，见 6.1）。
> 重试次数也没放消息体，而是放在**消息头 `x-attempt`**（下一节）。

### 5.4 延迟重试回路（最精妙的一块）

AI 调用失败时，不能立刻重试（下游可能正要恢复，立刻重试还是失败），也不能无限重试。需要"**10 秒后再试一次**"这种延迟能力。

RabbitMQ 有个特性：**消息在队列里可以设置 TTL（存活时间），到期后如果这个队列配置了死信交换机（DLX），消息会被自动转发到 DLX 指定的交换机上重新投递。**

于是可以这样搭：

```
        ┌────────────────────── ⑤ 死信回流，重新投递 ──────────────────────┐
        │                                                                  │
        │              路由键同样是 "article.moderation"                     │
        ▼                                                                  │
  ② 主交换机 ──────► ③ 主队列 ──────► ④ 消费者 ──────► HTTP ──► BlogAgent
   article.          article.          │
   moderation.       moderation.       │ 调用失败
   exchange          queue             │
                                      ▼
                            ⑥ ModerationRetrySender
                                      │ 发一条带 TTL 的新消息
                                      │ 消息头 x-attempt = nextAttempt
                                      │ 过期时间 = retryTtlMs[nextAttempt-1]
                                      ▼
  ⑦ 重试交换机 ──────────────────► ⑧ 重试队列
     retry.exchange                 retry.queue
                                    ★ 故意不配消费者 ★
                                    消息在这里静静躺着，
                                    等 TTL 到期 → 变成死信
                                    → 按 deadLetterRoutingKey 回流到主交换机
```

**为什么重试要用一个独立的交换机？** 因为 RabbitMQ 的死信机制只能转发到**另一个**交换机。如果重试消息直接发进主交换机，它会**立刻**被主队列消费掉，延迟完全失效。所以必须另起一个 `retry.exchange` 当"等候室入口"，消息进去后躺在没人消费的 `retry.queue` 里耗时间，到期才被"释放"回主链路。

代码：

- 发重试：`ModerationRetrySenderImpl.java:29-45`
- 重试队列声明 + DLX 指回主交换机：`ModerationRabbitConfig.java:38-44`
  ```java
  QueueBuilder.durable(ARTICLE_MODERATION_RETRY_QUEUE)
      .deadLetterExchange(ARTICLE_MODERATION_EXCHANGE)      // 死信回到主交换机
      .deadLetterRoutingKey(ARTICLE_MODERATION_ROUTING_KEY) // 用这个路由键重新投递
      .build();
  ```

### 5.5 为什么 `ARTICLE_MODERATION_ROUTING_KEY` 被复用了三次

这是你最初的那个问题。同一把"钥匙"要开三把锁：

| 用在哪 | 代码位置 | 作用 |
|---|---|---|
| 主队列的绑定 | `ModerationRabbitConfig.java:50` | 消息从主交换机进主队列 |
| 重试队列的绑定 | `ModerationRabbitConfig.java:57` | 重试消息从重试交换机进重试队列 |
| 重试队列的 `deadLetterRoutingKey` | `ModerationRabbitConfig.java:42` | **死信回流时用的路由键** |

第三处是闭环的关键：消息 TTL 到期被"释放"时，RabbitMQ 需要知道往哪个路由键上投。填了 `article.moderation`，它就会重新撞上主队列的绑定，落回主队列，消费者再次拿到它。

而 Direct 交换机是**精确匹配**的：投递用的键和绑定的键必须一模一样。如果第三处填了别的字符串，消息会**静默丢失**——交换机找不到匹配的队列时默认不报错，直接丢弃。

> **结论**：`ARTICLE_MODERATION_ROUTING_KEY` 不是"消费管线"的名字。它是**消息的投递地址标签**，同时兼职**死信回流的地址**，把主链路和重试回路黏成了一个闭环。真正代表"消费管线"的是 `ARTICLE_MODERATION_QUEUE`。

### 5.6 一次完整重试的时间轴

`maxAttempt = 3`，`retryTtlMs = [10000, 30000, 90000]`。注意语义：**初始 1 次 + 3 次重试 = 一共 4 次调用**。

```
t = 0s       作者点发布 → 落库 ai_reviewing → 发 MQ → 秒回"审核中"
t ≈ 0.1s     消费者 attempt=0 调 AI → 失败
             → attempt(0) < 3 → 发重试，x-attempt=1，TTL = retryTtlMs[0] = 10s
t ≈ 10.1s    消费者 attempt=1 调 AI → 失败
             → 发重试，x-attempt=2，TTL = retryTtlMs[1] = 30s
t ≈ 40.1s    消费者 attempt=2 调 AI → 失败
             → 发重试，x-attempt=3，TTL = retryTtlMs[2] = 90s
t ≈ 130.1s   消费者 attempt=3 调 AI → 失败
             → attempt(3) >= maxAttempt(3) → 【转人工】
             文章 → pending_review / manual
             review_reason = "审核服务不可用，转人工审核"
```

最坏情况约 **130 秒 + 4 次 AI 调用耗时**，然后落到人工。失败路径绝不放行，这就是 **fail-closed（失败即关闭）**。

---

## 六、消费端的三道闸门

三道闸长在消费端的状态机 `ArticleModerationWorkflowImpl` 上——MQ 入口壳 `ArticleModerationConsumer` 只管解析消息、转交、吞异常（见 6.4）。它们不再挤在同一个方法里，而是分居一条消息的三段处理中：

| 闸 | 在哪一段 | 挡什么 |
|---|---|---|
| 幂等闸（6.1） | TX1 读闸 | 重复消息、已经不作数的消息 |
| 竞态闸（6.2） | TX2 应用 | 审核期间世界变了（软删、人工已处置） |
| 失败闸（6.3） | TX2b 失败路径 | 决定延迟重试还是转人工 |

三道闸的语义与旧版完全一致（这是一次迁移，不是重新设计），变的只是事务边界——每道闸各待在自己那一小段事务里，中间那段 3~15 秒的 AI 调用谁都占不住。

### 6.1 第一道：幂等闸（挡重复消息）

```java
// ArticleModerationWorkflowImpl.java:87-95（TX1 读闸）
boolean newReviewing = "ai_reviewing".equals(article.getStatus())
        && "ai_reviewing".equals(article.getReviewStatus());
boolean editReviewing = "published".equals(article.getStatus())
        && "ai_reviewing".equals(article.getReviewStatus())
        && pending != null;
if (!newReviewing && !editReviewing) {
    log.debug("审核消息跳过（状态已非审核中）, articleId: {}", articleId);
    return;                                    // 直接返回，正常确认消息
}
```

这一闸是 TX1 的全部工作。闸门放行时顺手把**审核输入**一起取走（新文章取正文表的内容，编辑场景取待生效区的内容），事务随即提交、连接归还 DB 池——所以这段是毫秒级的。

**为什么需要**：同一条消息可能被处理多次——

- 重试回路和兜底扫描可能同时盯上同一篇文章
- 消费者处理后 ack 前崩溃，MQ 会重新投递
- 作者删稿/人工处理完，迟到的重试消息才到达

**怎么挡**：动手之前先看 DB 状态。**只要状态已经不是"审核中"，说明这件事已经被处理过了（或已经不作数了），直接返回。** 这样重复消息天然无害，不需要额外的去重表。

> 状态检查放在最前面，是这条链路的铁律：**任何动作之前，先查 DB 状态闸。**

### 6.2 第二道：竞态闸（挡"审核期间世界变了"）

AI 调用要花 3~15 秒，这段时间里可能发生：

- 作者等不及，把文章删了（软删）
- 兜底扫描判定超时，已经转人工了
- 管理员从队列里看到并处理了

如果不复核就写库，就会出现"文章已被软删，审核结果却把它 updateById 复活了"这种事故。

```java
// ArticleModerationWorkflowImpl.java:109-115（TX2 应用）
Article latest = articleDao.getOne(new LambdaQueryWrapper<Article>()
        .eq(Article::getId, articleId)
        .last("FOR UPDATE"));                  // ← 关键：当前读
if (latest == null || latest.getDeletedAt() != null) {
    pendingContentMapper.deleteById(articleId);  // 已被删 → 清理待生效区收尾
    return;                                      // 不写任何状态，不发 ES
}
```

**`FOR UPDATE` 这行是重点，原因值得单独讲：**

它来自一个真实的坑。**MySQL 默认隔离级别是 REPEATABLE READ（可重复读）**：在这个级别下，事务里第一次普通查询会建立一个**快照**，后续所有普通查询都复用这个快照。改造前整条链路是一个长事务（幂等闸那次读建立快照 → 调 AI 十几秒 → 落结论），于是 **AI 跑的那十几秒里别人提交的软删，普通 `getById` 是读不到的**。

读不到 → 闸门放行 → `updateById` 把已软删的行复活 + ES 索引消息照发。这就是事故。

改造后事务边界变了（快照不再跨越 AI 调用），但这行 `FOR UPDATE` 必须留着——它现在担两件事：

1. **当前读**：绕过快照，直接读最新已提交的版本。语义永远是"此刻库里是什么样"，与这个事务里之前有没有发生过更早的读无关。
2. **行锁**：锁住这一行直到 TX2 提交。这是并发下**同一篇文章的两条消息只能有一个落下结论**的保证——后进的那个人拿锁时前者已提交，它重读到的就是"已非审核中"，于是走跳过分支空转返回（这正是第十节 R10 记的那笔代价）。

> **一句话记住**：事务里要判断"别人刚刚有没有改过这行"，不能用普通查询，必须 `FOR UPDATE`。

### 6.3 第三道：失败闸（决定重试还是转人工）

```java
// ArticleModerationWorkflowImpl.java:224-257（TX2b 失败路径）
private void handleFailure(String articleId, int attempt) {
    if (attempt < properties.getMaxAttempt()) {
        retrySender.sendRetry(articleId, attempt + 1);   // 还有额度 → 延迟重试
        return;
    }
    // 额度用尽 → 转人工，但先 FOR UPDATE 重读
    Article article = articleDao.getOne(... .last("FOR UPDATE"));
    if (article == null || article.getDeletedAt() != null) { ... return; }
    if (!"ai_reviewing".equals(article.getReviewStatus())) {
        log.debug("审核失败兜底跳过（状态已被人工变更）, articleId: {}", articleId);
        return;                                          // ← 绝不覆盖人工结论
    }
    ...
    article.setReviewStatus("manual");
    article.setReviewReason("审核服务不可用，转人工审核");
}
```

**转人工之前必须重读状态**，因为从"调用失败"到"决定转人工"之间，人工可能已经把这篇处理完了。重读发现状态已经不是"审核中"，就直接放弃，**绝不覆盖人工的结论**。

### 6.4 事务边界：两段短事务，AI 调用在事务外

一条消息的处理被切成三段（`ArticleModerationWorkflowImpl.process`）：

```
TX1 读闸（毫秒级）   读状态 + 过幂等闸 + 取审核输入 → 提交，DB 连接立刻归还
事务外              调 BlogAgent（3~15 秒）        → 不占连接、不持行锁
TX2 应用（毫秒级）   FOR UPDATE 重读 + 竞态闸 + 落结论
```

**为什么不能像早期版本那样把事务注在 `onMessage` 上**：那等于让一次 3~15 秒的外部 HTTP 调用一直搂在事务里，期间占着一条 DB 连接、还锁着这篇文章的行——单线程时看不出毛病，并发度一提高就自己把自己堵死。

**保留的坑记录**：`@Transactional` 靠 AOP 代理生效，同类自调用（`this.handle(...)`）不经过代理，注解放 `handle` 上从未生效（每条写独立提交）。改造后改用 `TransactionTemplate` 编程式边界，不依赖代理，这个问题彻底消失；顺带还多一个好处——**"AI 调用落在两段事务之间"是肉眼可见的**，看 `process()` 那几行就知道，不必去猜注解到底有没有生效。

**判定权威**：审核输入取 TX1 时点的内容（审的就是"提交那一刻的稿子"），但走哪条 apply 分支只认 TX2 的重读状态——AI 跑的那几秒里作者撤稿、人工插手，都靠这一条挡。

失败路径走 TX2b：`attempt < 3` 发延迟重试（10s/30s/90s），否则 `FOR UPDATE` 重读后仍审核中才转人工。

> **通用规律（坑记录的另一半）**：Spring 里 `@Transactional` / `@Async` / `@Cacheable` 这类注解，**自己调自己一律失效**，必须经过代理（容器调用、或注入自己、或 `AopContext.currentProxy()`）。

### 6.5 为什么异常要被吞掉

```java
// ArticleModerationConsumer.java:31-38
} catch (Exception e) {
    log.error("审核消费处理异常, articleId: {}, 错误: {}", articleId, e.getMessage(), e);
    // 不抛出 → RabbitMQ 不会无限 requeue
}
```

如果异常抛出去，RabbitMQ 默认会**把消息重新放回队列**，然后立刻再次投递 → 再次抛异常 → 无限循环，日志刷爆、CPU 打满。

所以这里**一律吞掉**，然后由兜底扫描（第七节）来收尾。

> 注意：走到这个 catch 时**不会**调 `handleFailure` 转人工。因为异常可能发生在 `apply` 写状态的中途，状态可能已经改过了，再走转人工会污染结果。交给兜底扫描去判断更好。
>
> 另外，吞异常只发生在 `ArticleModerationConsumer` 这一层：`process()` 只吞"调 AI 失败"（转 TX2b 重试/转人工），TX1/TX2 抛出的 DB 异常一路上冒到这里，记日志收场。

---

## 七、兜底扫描：保证永不卡死

`ModerationStuckScanner` 每 5 分钟跑一次，专门救那些"消息丢了 / 消费者挂了 / 异常被吞了"导致卡在审核中的文章。

```java
@Scheduled(fixedDelayString = "${oy-blog.article.moderation.scan-interval-ms:300000}", ...)
public void scanStuck() {
    LocalDateTime deadline = now().minusMinutes(stuckTimeoutMinutes);   // 15 分钟前
    // 扫两类：
    // ① status=ai_reviewing 且 update_at < deadline  → 新文转 pending_review/manual
    // ② status=published && review_status=ai_reviewing 且待生效区行更新超时 → 转 manual
}
```

它自己也有两道防误伤措施：

1. **Java 侧二次校验超时** —— 查询条件和内存判断都过一遍，防时钟偏差误伤刚发布的文章
2. **更新前状态复核** —— `getById` 重读，确认状态还是"审核中"才动手，防覆盖消费者刚刚落下的结果

转人工后写的审核日志 `operator_id = "system"`（区别于 `"ai"` 和人工的管理员 ID）。

### 故障全景

| 故障 | 谁来兜 | 最终结果 |
|---|---|---|
| BlogAgent 超时/报错/限流 | workflow `handleFailure`（TX2b） | 重试 3 次 → 转人工 |
| 发布时 MQ 发送失败 | `ArticleModerationProducerImpl` 只记日志不抛 + 兜底扫描 | 15 分钟后转人工 |
| MQ 消息丢失 | 兜底扫描 | 15 分钟后转人工 |
| 消费者进程挂死 | 兜底扫描 | 15 分钟后转人工 |
| 消费者处理抛异常 | 异常吞掉 + 兜底扫描 | 15 分钟后转人工 |
| 重复消息（重试 + 扫描并发） | 幂等闸 | 无害跳过 |
| 审核期间作者删稿 | 竞态闸 | 清理待生效区，不写状态 |
| 审核期间人工已处理 | 竞态闸 / 失败闸重读 | 不覆盖人工结论 |
| 审核通过但 ES 索引消息发送失败 | `mq_retry_log` + `RetryMqScheduler` | 每分钟重发，最多 5 次 |

> **所有故障路径的终点都是"转人工"，没有任何一条会直接放行。** 这就是 fail-closed：审核是安全门，宁可让人多等，也不能让没审过的内容漏出去。

---

## 八、配置项

### 8.1 审核参数（`ModerationProperties`，前缀 `oy-blog.article.moderation`）

`ModerationProperties.java:16-33`：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 总开关，`false` = 全放行（等于关闭审核门） |
| `exempt-roles` | `[ADMIN]` | 豁免角色，命中跳过 AI 审核 |
| `base-url` | `http://localhost:8001` | BlogAgent 地址 |
| `timeout-ms` | `30000` | 审核调用超时（连接+读取共用） |
| `retry-ttl-ms` | `[10000, 30000, 90000]` | 第 1/2/3 次重试的延迟 |
| `max-attempt` | `3` | 最大重试次数（attempt 从 0 起） |
| `stuck-timeout-minutes` | `15` | 兜底扫描：审核中超这么久 → 转人工 |
| `scan-interval-ms` | `300000` | 兜底扫描间隔（5 分钟） |

`application.yml` 中只显式配了前四项（`:87-91`），后四项走代码默认值。

### 8.2 并发与连接池（Spring 原生配置，不属上面的 `ModerationProperties`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `spring.rabbitmq.listener.simple.concurrency` | `${MODERATION_CONCURRENCY:4}` | 审核消费者并发线程数，即同时有多少条消息在"事务外调 AI"这一段并行 |
| `spring.rabbitmq.listener.simple.prefetch` | `${MODERATION_PREFETCH:2}` | 每条消费者最多预取几条在途消息。默认 250 会让消息被单一消费者预取光——并发度配置就成了摆设（隐性串行） |
| `spring.datasource.hikari.maximum-pool-size` | `${DB_POOL_MAX:16}` | 连接池上限。两段短事务把连接占用时间压到毫秒级，池子不必跟着并发度一起放大 |

前两项在 `application.yml:34-51`，第三项在 `:10-13`。三者在部署侧都通过环境变量暴露（`deploy/docker-compose.env.example`），**改环境变量即可，不需要重新打包**——这很重要，本项目的服务器 jar 里烘焙着配置，改 yml 得重新打包发布。

> **并发度天花板由 BlogAgent / DeepSeek 的并发额度决定**，不是越高越好：429 限流会被重试阶梯（10s/30s/90s）接住，最坏转人工，属于 fail-closed，但白白浪费了时间。调之前先确认下游扛得住。

---

## 九、关键文件索引

| 文件 | 职责 |
|---|---|
| `oy-blog-common/.../mq/constants/ArticleMQConstant.java` | MQ 常量 |
| `oy-blog-common/.../mq/domain/ArticleModerationMessage.java` | 消息体（只有 articleId） |
| `article-service/.../config/ModerationRabbitConfig.java` | MQ 拓扑声明（交换机/队列/绑定/DLX） |
| `article-service/.../config/ModerationProperties.java` | 审核配置 |
| `article-service/.../config/ModerationTxConfig.java` | `TransactionTemplate` Bean，两段式事务边界靠它 |
| `article-service/.../service/impl/ArticleModerationProducerImpl.java` | 发审核消息（失败只记日志） |
| `article-service/.../consumer/ArticleModerationConsumer.java` | MQ 入口薄壳：解析消息、转交 workflow、吞异常（不抛 → 不无限 requeue） |
| `article-service/.../service/ArticleModerationWorkflow.java` | 审核流程接口（消费端与单测的统一入口） |
| `article-service/.../service/impl/ArticleModerationWorkflowImpl.java` | **消费端核心**：TX1 读闸 → 事务外调 AI → TX2 应用（三道闸 + 三态流转都在这） |
| `article-service/.../service/impl/ModerationRetrySenderImpl.java` | 发延迟重试消息（带 x-attempt + TTL） |
| `article-service/.../scheduler/ModerationStuckScanner.java` | 兜底扫描，超时转人工 |
| `article-service/.../service/impl/ArticleBizServiceImpl.java` | 发布/编辑入口、待生效区写入、afterCommit 发消息 |
| `article-service/.../service/impl/ModerationServiceImpl.java` | 调 BlogAgent HTTP 接口、写审核日志 |
| `article-service/.../service/impl/ModerationAdminBizServiceImpl.java` | 人工审核队列 + 通过/驳回 |
| `article-service/.../service/impl/ArticleIndexMessageServiceImpl.java` | 审核通过后发 ES 索引消息 |

---

## 十、设计决策与代价

| # | 决策 | 理由 | 代价 |
|---|---|---|---|
| R1 | 真异步（MQ）而非同步等待 | 发布秒回，不用盯着转圈十几秒 | 引入中间态，需要状态机 + 兜底扫描 |
| R2 | 编辑用待生效区而非直接覆盖 | 驳回时旧版毫发无损，读者永远看不到未审内容 | 多一张表、双份内容、一篇只允许一份待审编辑 |
| R3 | **fail-closed**：一切故障转人工，绝不放行 | 审核是安全门，漏放比慢放严重得多 | 故障时用户等待变长（最坏 130 秒 + 15 分钟） |
| R4 | 重试延迟递增 10s/30s/90s | 给下游恢复留出时间，避免无效重试打爆 | 最坏 130 秒才落到人工 |
| R5 | 重试次数放消息头 `x-attempt` 而非消息体 | 消息体保持纯净（只有 articleId）；消息头能随死信一起保留 | 消费端要 `@Header` 解析，首投需容错缺省 |
| R6 | 两段短事务，用 `TransactionTemplate` 编程式边界，不用一个长事务 | 3~15 秒的 AI 调用不能搂在事务里（占连接 + 持行锁，并发一高就自堵）；注解式又踩过同类自调用不生效的坑（见 6.4） | 事务边界要自己守——好在 `process()` 里一眼看得见；测试要自己准备 `TransactionTemplate` |
| R7 | 消费端异常一律吞掉 | 避免 RabbitMQ 无限 requeue 打爆服务 | 必须有兜底扫描接住 |
| R8 | 幂等靠"查 DB 状态闸"而非去重表 | 零额外存储，天然幂等 | 要求所有状态流转都必须改状态字段，否则闸门失效 |
| R9 | 竞态复核用 `FOR UPDATE` 当前读 | REPEATABLE READ 下普通查询可能读到旧快照；行锁还把同一篇文章的并发消费者串行化了 | 持锁到 TX2 提交——锁只覆盖毫秒级的"重读 + 落结论"，已不再跨越 AI 调用 |
| R10 | 同一篇文章的并发消息不做跨实例互斥 | 不加分布式锁：少一套锁的复杂度与故障面；TX2 的 `FOR UPDATE` 已保证只有一个能落下结论 | 并发下同一篇文章的两条消息可能各调一次 AI，另一条空转返回——白花一次 token，刻意接受 |
| R11 | 并发配置用 `listener.simple.*`（服务级）而非专用 `containerFactory` | article-service 目前只有这一个 `@RabbitListener`，服务级配置就等于专用配置 | 日后新增第二个 `@RabbitListener` 时必须收窄为专用 `containerFactory`，否则会被一并改到 |

---

## 附：常见疑问速查

**Q：`ARTICLE_MODERATION_ROUTING_KEY` 是"消费管线"吗？**
不是。它是消息的**投递地址标签**（路由键），被主队列绑定、重试队列绑定、死信回流三处复用，负责把主链路和重试回路黏成闭环。代表消费管线的是 `ARTICLE_MODERATION_QUEUE`。

**Q：为什么重试要另起一个交换机？**
因为死信只能转发到"另一个"交换机。重试消息若发进主交换机，会立刻被主队列消费，延迟失效。

**Q：`maxAttempt=3` 是总共调 3 次 AI 吗？**
不是，是**重试 3 次**，加上初始那次共 **4 次**调用。

**Q：为什么待生效区的主键直接用 `article.id`？**
这是刻意的约束——保证**一篇最多只有一份待审编辑**，简化并发处理，也让守卫逻辑（`review_status==ai_reviewing` 就拒绝再编辑）成立。

**Q：审核中能删除文章吗？**
可以（撤稿是作者权利）。软删后，消费端在 TX1 读闸（撤稿发生在调 AI 之前）或 TX2 竞态闸（撤稿发生在 AI 那几秒里）任一处理到它时都会发现已删，清理待生效区收尾，不写任何状态。

**Q：审核中能编辑吗？**
不能。`publish` 里有守卫，`status==ai_reviewing` 或 `review_status==ai_reviewing` 时直接拒绝，提示"审核中，请稍候"。这是为了防频繁重审浪费 AI 额度。
