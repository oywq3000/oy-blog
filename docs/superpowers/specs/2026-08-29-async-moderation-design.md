# 文章 AI 审核异步化设计

- 日期：2026-08-29
- 状态：三节设计已获用户确认（状态机 / 数据流 / 改动清单）
- 前置：同步版已实现并终审通过（spec 见 `2026-08-29-article-ai-moderation-design.md`，代码在 dev1 未合并）；本 spec 是对同步版的**架构演进**
- 涉及仓库：oy-blog（Java，改动全部在本仓库）；BlogAgent（Python，**零改动**，协议不变）；前端（配合项，不在本仓库）

## 1. 背景与目标

同步版审核已实现：用户点发布后需等待 AI 审核 3~15 秒才返回结果。体验问题：发布是"卡住等结果"的。

目标：**真异步审核**——发布秒回"AI 审核中"，AI 在后台审核，结果出来后文章状态自动流转，创作中心列表通过前端轮询实时反映状态。

已与用户确认的需求决策：

1. **真异步**：发布秒回 + 后台审（MQ 审核队列，用户选定方案 A）。
2. **结果通知**：文章状态落在列表上 + 前端自动轮询（10~20 秒一次），不做站内通知。
3. **审核中操作**：可删除、不可编辑（防频繁重审浪费 API 额度；保留作者撤稿权）。
4. **失败兜底**：AI 调用失败自动重试 3 次（间隔递增），仍失败转人工。
5. **编辑也异步**：已发布文章的编辑同样秒回，旧版继续展示 + "编辑审核中"徽标。

## 2. 状态机（在同步版基础上演进）

`status` 新增 `ai_reviewing`（AI 审核中）：

| status | 含义 | 公众可见 |
| --- | --- | --- |
| `draft` | 草稿 | 否 |
| `ai_reviewing` | AI 审核中（新增） | 否，作者列表显示"AI 审核中" |
| `published` | 已发布 | 是 |
| `pending_review` | 待人工审核 | 否 |
| `rejected` | 已驳回 | 否 |

`review_status` 新增 `ai_reviewing` 值（原四值 approved/rejected/manual/exempt 保留）。已发布文章的编辑：`status` 保持 `published`（旧版继续展示），`review_status=ai_reviewing` + `article_pending_content` 有行 → 作者列表显示"编辑审核中"徽标。

状态转移：

```
发布（非豁免）→ ai_reviewing
  ├─ 消费端 AI approve → published（发 ES 索引）
  ├─ AI reject → rejected（review_reason=AI 理由）
  ├─ AI manual → pending_review（进人工队列）
  └─ 失败重试 3 次仍挂 → pending_review（reason=审核服务不可用）

编辑已发布文章 → 内容进 article_pending_content + review_status=ai_reviewing（旧版继续 published）
  ├─ approve → 替换生效 + 章节重建 + ES UPDATE，清待生效区，review_status=approved
  ├─ reject  → 清待生效区（本次编辑丢弃，旧版不动），review_status=rejected
  └─ manual  → 待生效区保留 + review_status=manual → 进现有人工审核队列

审核中删除 → 软删；消费端发现已删 → 清待生效区收尾
审核中不可编辑 → publish 守卫：status==ai_reviewing 或（published 且 review_status==ai_reviewing）→ 拒绝，提示"审核中，请稍候"
```

## 3. 总体架构

```
publish（非豁免）→ 落库（ai_reviewing / 待生效区行）→ 事务提交后发 MQ 审核消息 → 秒回 {"verdict":"ai_reviewing"}
                         │
                         ▼
        article.moderation.queue → ArticleModerationConsumer（article-service 内）
           1. 查 DB 最新状态（幂等闸）
           2. 调 BlogAgent POST /moderate/article（ModerationService.moderate 复用）
           3. 成功 → 三态流转 + 写审核日志 + approve 才发 ES
           4. 失败 → attempt<3 发延迟重试消息（TTL 10s/30s/90s），≥3 转人工
                         │
                         ▼
        兜底扫描 ModerationStuckScanner @Scheduled 每 5 分钟：
        ai_reviewing 超 15 分钟无结果 → 转人工（防消息丢失/消费端挂死）
```

## 4. 数据流

### 4.1 发布新文章（秒回）

1. 豁免用户 / `moderation.enabled=false` → 原同步放行逻辑不变（published + review_status=exempt + ES），秒过不异步。
2. 审核中守卫：article 已是 `ai_reviewing`（或编辑审核中）→ 拒绝。
3. 非豁免 → 落库：`status=ai_reviewing`、`review_status=ai_reviewing`；内容/章节/标签照常保存。事务提交后发 MQ 审核消息 `{articleId}`。返回 `{articleId, verdict:"ai_reviewing"}`。
4. 前端：创作中心列表每 10~20 秒轮询一次，按 status 显示"AI 审核中"。

### 4.2 编辑已发布文章（旧版继续展示）

1. 编辑审核中守卫：`review_status==ai_reviewing` → 拒绝（一次只允许一份待审编辑）。
2. 新内容进 `article_pending_content`（覆盖或新建）+ `review_status=ai_reviewing`；封面/允许评论/标签等未审字段立即生效（与同步版决策一致）。
3. 事务提交后发 MQ → 秒回 `{articleId, verdict:"ai_reviewing"}`。旧版继续 published 展示。

### 4.3 消费端处理（幂等闸）

`@RabbitListener(article.moderation.queue)` 收到 `{articleId}`：

| DB 状态 | 处理 |
| --- | --- |
| 文章不存在或已软删 | 清待生效区行 → 确认收尾 |
| `status==ai_reviewing` | 按新文章审（标题/摘要/正文） |
| `status==published` 且有待生效区行且 `review_status==ai_reviewing` | 按编辑审（待生效区内容） |
| 其他（draft/rejected/pending_review/published 无待生效行） | 过期任务，直接确认不动作 |

审核三态流转与同步版完全一致：approve→published/替换生效+章节重建+ES；reject→rejected/清待生效区；manual→pending_review/待生效区保留。写 ModerationLog（ai_approve/ai_reject/ai_manual，operator_id="ai"）。

### 4.4 失败路径（两层防护）

| 故障 | 处理 |
| --- | --- |
| BlogAgent 调用失败（限流/网络/超时） | attempt（消息头 x-attempt）<3 → 发延迟重试（expiration 10s/30s/90s，重试队列死信回路自动回主队列）；≥3 → 转人工 |
| 发布时 MQ 发送失败 | 文章已落库 ai_reviewing → 记日志，兜底扫描接管 |
| MQ 消息丢失 / 消费端挂死 | 兜底扫描：超 15 分钟无结果 → 转人工 |
| 重复消息（重试回路与兜底扫描并存） | 幂等闸：状态已非 ai_reviewing → 直接确认 |

任何故障最终收敛到"转人工"，文章永不卡死在"AI 审核中"。

## 5. 改动清单

### 5.1 MQ 基建

- `ArticleMQConstant`（oy-blog-common）新增：
  - `ARTICLE_MODERATION_EXCHANGE = "article.moderation.exchange"`
  - `ARTICLE_MODERATION_QUEUE = "article.moderation.queue"`
  - `ARTICLE_MODERATION_ROUTING_KEY = "article.moderation"`
  - `ARTICLE_MODERATION_RETRY_QUEUE = "article.moderation.retry.queue"`
- 新消息体 `ArticleModerationMessage`：仅 `articleId`；attempt 计数放消息头 `x-attempt`。
- 队列声明（新增 RabbitConfig，Spring AMQP `@Bean Declarable`）：
  - exchange：direct
  - 主队列：绑定 exchange，routing key=article.moderation
  - 重试队列：无消费者，`x-dead-letter-exchange=article.moderation.exchange`；每条消息逐条设 expiration（TTL 递增），到期自动死信回主队列（死信保留原始 header 与 routing key）

### 5.2 article-service

| 文件 | 改动 |
| --- | --- |
| `ArticleBizServiceImpl.publish` | 豁免路径不动；非豁免改落库 `ai_reviewing`（编辑→待生效区）+ afterCommit 发审核消息 + 返回 `ai_reviewing`；加审核中守卫 |
| 新增 `ArticleModerationProducer` | 发审核消息，失败记日志（照 `ArticleMessageProducerImpl` 模式） |
| 新增 `ArticleModerationConsumer` | `@RabbitListener` + 幂等闸 + 调 `ModerationService.moderate` + 三态流转 |
| 新增 `ModerationRetrySender` | 失败按 attempt 发延迟重试消息 |
| 新增 `ModerationStuckScanner` | `@Scheduled` 每 5 分钟，超 15 分钟转人工（照 `RetryMqScheduler` 模式） |
| `ModerationProperties` | 新增：`retryTtlMs=[10000,30000,90000]`、`maxAttempt=3`、`stuckTimeoutMinutes=15`、`scanIntervalMs=300000` |

完全复用零改动：`ModerationService`、`ArticlePendingContent`、`ArticleIndexMessageService`、`ArticleChapterService`、Task 10 人工审核队列、admin-service 全部。

### 5.3 零改动方

- Python（BlogAgent）：协议不变。
- 数据库：无新表无 ALTER（article 审核字段与 article_pending_content 均已存在）。
- admin-service：豁免用户与人审队列不受影响。

### 5.4 前端配合项（不在本仓库，记录待办）

- publish 返回 `verdict=ai_reviewing` → 关闭 loading、提示"已提交审核"
- 创作中心列表 10~20 秒自动轮询，按 `status`/`reviewStatus` 显示"AI 审核中 / 编辑审核中 / 待人工审核 / 已驳回+原因"

## 6. 测试方案

**publish 异步化单测（改造现有 `ArticleBizPublishModerationTest`）**
- 新文章发布 → `status=ai_reviewing`、producer 收到消息、返回 `ai_reviewing`、不再同步调 `moderate`
- 审核中守卫：ai_reviewing 再发布 → 拒绝；编辑审核中再编辑 → 拒绝
- 编辑已发布 → 待生效区有行 + `review_status=ai_reviewing` + 旧内容不动
- 豁免 / 开关关闭路径不变（回归）

**消费端单测（新增 `ArticleModerationConsumerTest`，Mock `ModerationService`）**
- 新文章三态：approve→published+ES；reject→rejected；manual→pending_review
- 编辑三态：approve→替换+章节重建+清待生效区+ES；reject→清待生效区（旧版不动）；manual→保留进人工队列
- 幂等闸：已删→清理收尾；状态已变→无动作
- 失败：attempt<3→发延迟重试；≥3→转人工

**兜底扫描单测**：超 15 分钟→转人工；未超时→不动作

**回归**：现有 47 个测试中 publish 相关用例重构后全绿，其余零影响

**联调验收（部署后）**：MOCK 三态全链路（秒回→状态流转）；断 BlogAgent→3 次重试→转人工；审核中删除→消费端收尾；编辑歧义→旧版展示+人工队列

## 7. 验收清单

- [ ] 普通用户发布 → 秒回"AI 审核中"→ 1 分钟内列表自动变为已发布/已驳回/待人工
- [ ] 已发布文章编辑 → 秒回"编辑审核中"→ 旧版持续展示 → 通过后自动替换生效
- [ ] 审核中文章不可编辑（接口拒绝）、可删除（删除后消费端收尾）
- [ ] BlogAgent 宕机 → 重试 3 次（10s/30s/90s）→ 转人工
- [ ] 杀消费端/MQ 消息丢失 → 15 分钟兜底扫描转人工
- [ ] 豁免用户发布仍秒过、人工审核队列流程不变
- [ ] 单测全绿 + 既有 47 测试回归
- [ ] Python 侧零改动（协议未变）

## 8. 风险与备注

- **幂等是正确性核心**：消费端一切动作前先查 DB 状态闸；重复消息、过期任务、兜底扫描与重试回路并发都必须无害。实现计划中幂等闸单独成任务。
- **重试回路的 RabbitMQ 细节**：逐消息 TTL（MessageProperties.expiration）+ 死信保留原始 header/routing key——需要在联调时实测确认（本机无 RabbitMQ，部署后验证）。
- **MQ 发送失败不阻塞发布**：文章已落库 ai_reviewing，发送失败只记日志，兜底扫描保证不卡死——语义上"发布成功但审核稍慢"，可接受。
- **同步版代码演进关系**：本 spec 的改动在 dev1 未合并的同步版代码之上进行（publish 已含审核门）；同步版曾确认的决策（豁免、先审后生效语义、fail-closed 方向）全部保留。
- **前端轮询**：纯前端定时器拉现有 listMine 接口，无后端配合；频率 10~20 秒一次，个人博客量级无压力。
