# 文章浏览历史机制

## 概述

浏览历史记录**登录用户最近读过哪些文章**，用于个人中心「浏览历史」tab 展示。核心定位：

| 维度 | 说明 |
|------|------|
| 数据形态 | **关系数据而非事件日志**：`(user_id, article_id)` → 最近一次浏览时间，upsert 一行，行数上界 = 用户数 × 文章数 |
| 计数与历史分离 | 浏览量计数走 Redis 去重 + `article_stats`；`article_log` 只承担"谁最近读了什么"，职责不重叠 |
| 存储选型 | **MySQL**（非 MongoDB）。历史列表展示必须 JOIN 文章表拿标题/封面/作者/统计，跨库拼接或文档快照都会引入脏数据；个人博客量级下 MongoDB 的事件日志优势无意义 |
| 游客语义 | 游客匿名浏览**不写历史**（历史接口仅登录用户消费；表 `user_id` 可空语义为"匿名为 NULL"，不存临时 GUEST_ID） |

设计原则：**article_log 是辅助数据** —— 写入失败不影响浏览计数接口的成功返回；历史展示按需实时 JOIN 文章表，不存快照。

---

## 架构图

```
┌─────────────────────────── 前端 (Vue 3) ───────────────────────────┐
│                                                                    │
│  ArticleDetail.vue                     UserProfile.vue (history tab)│
│  ┌─────────────────────────┐          ┌─────────────────────────┐  │
│  │ 加载详情成功            │          │ loadHistory()           │  │
│  │  sessionStorage 去重    │          │  GET /article/read/history│ │
│  │   (viewed_{articleId})  │          │  ↓                      │  │
│  │  POST .../view          │          │ 渲染 viewedAt/标题/摘要   │  │
│  └──────────┬──────────────┘          └───────────▲─────────────┘  │
└─────────────┼──────────────────────────────────────┼───────────────┘
              │ X-User-Id（网关注入）                  │
┌─────────────▼──────────────────────────────────────┴───────────────┐
│                     article-service (8091)                          │
│                                                                    │
│  写入链路 view():                           读取链路 listHistory():  │
│  ┌─────────────────────────────────┐   ┌─────────────────────────┐ │
│  │ 1. 文章存在性检查                │   │ 1. listHistoryLogs      │ │
│  │ 2. Redis 去重                   │   │    user_id + action='view'│ │
│  │    IP 5min / 登录用户 30min      │   │    ORDER BY view_at DESC │ │
│  │ 3. 写 article_log（仅登录用户）   │   │ 2. 内存按 articleId 去重 │ │
│  │    upsert: 同 user+article 更新   │   │    (保留最新 view_at)    │ │
│  │ 4. article_stats 计数递增        │   │ 3. 批量查文章 listByIds  │ │
│  │    (无记录则新建，冲突降级 incViews)│  │ 4. 注入 viewedAt         │ │
│  └─────────────────────────────────┘   │ 5. 补统计 + 作者信息      │ │
│                                        └─────────────────────────┘ │
└───────┬──────────────────────────────────┬─────────────────────────┘
        │                                  │
        ▼                                  ▼
  ┌───────────┐  ┌──────────┐      ┌──────────────────┐
  │   Redis   │  │  MySQL   │      │ article_log       │
  │ 去重 key  │  │ article  │      │ idx_user_action_  │
  │ 5min/30min│  │ article_ │      │ time(user_id,     │
  └───────────┘  │ stats    │      │ action, view_at)  │
                 └──────────┘      └──────────────────┘
```

---

## 数据模型

表 `article_log`（InnoDB, utf8mb4，注释"文章操作日志（可分区）"）：

| 列 | 类型 | 说明 |
|----|------|------|
| id | varchar(64) PK | 32 位 UUID（`BaseBiz.getId()`），`IdType.INPUT` 由调用方赋值 |
| _class | varchar(255) NULL | Spring Data MongoDB 遗留列，无消费方，不写入 |
| article_id | varchar(64) NOT NULL | 文章 UUID |
| user_id | varchar(64) NULL | 用户 ID，**匿名为 NULL**（游客不写） |
| action | varchar(64) NULL | 操作类型，浏览固定为 `"view"`（无枚举，全仓库裸字符串一致） |
| ip / ua / referer | varchar(64) / (512) / (512) | 请求上下文，取不到为 NULL |
| view_at | datetime NOT NULL | 浏览时间，DEFAULT CURRENT_TIMESTAMP |

索引设计：

| 索引 | 列 | 用途 |
|------|----|----|
| PRIMARY | id | — |
| idx_article_log_article_time | (article_id, view_at) | 文章维度时间线 |
| idx_article_log_user_article | (user_id, article_id) | appendView upsert 查询 |
| idx_article_log_user_action_time | (user_id, action, view_at) | **历史列表查询**：等值过滤 + view_at 排序，无 filesort |

实体：`com.oyproj.domain.entity.ArticleLog`；Mapper：`ArticleLogMapper`（纯 MyBatis-Plus BaseMapper，无 XML）。DDL 变更脚本见 `doc/sql/article_log.sql`（已执行到库）。

---

## 写入链路

### 前端触发

- 位置：前端仓库 `oy-blog-front`（与后端同级目录）`src/views/ArticleDetail.vue`（loadArticle 成功后）
- API：`src/api/article.ts` 的 `recordArticleView(articleId)` → `POST /api/article-service/article/interaction/{articleId}/view`
- 去重：`sessionStorage` key `viewed_{articleId}` —— 每个浏览器 tab 会话对同一文章只发一次请求；请求成功用返回值刷新页面 viewCount

### 后端处理

`ArticleInteractionController.view()` → `ArticleInteractionBizServiceImpl.view()`（`@Transactional`），步骤：

1. **存在性检查**：`articleDao.getById`，不存在返回 error，不触碰统计与日志
2. **Redis 去重**（命中则直接返回当前计数，不写日志不重复计数）：
   - IP 维度：`view:article:{articleId}:ip:{ip}`，TTL 5 分钟（clientIp 从 X-Forwarded-For → X-Real-IP → remoteAddr 提取）
   - 用户维度：`view:article:{articleId}:user:{userId}`，TTL 30 分钟（仅登录用户）
3. **写浏览历史（仅登录用户，游客跳过）**：
   ```java
   ArticleLog viewLog = ArticleLog.builder()
           .id(getId())          // IdType.INPUT，必须自备主键
           .articleId(articleId).userId(userId).action("view")
           .ip(clientIp).ua(getRequestHeader("User-Agent"))
           .referer(getRequestHeader("Referer")).viewAt(LocalDateTime.now())
           .build();
   viewDao.appendView(viewLog);  // 包 try-catch：失败只 warn，不影响计数接口
   ```
   **调用点必须在统计递增之前** —— stats 新建分支有 `return Result.ok(1L)` 的 early return，放后面会漏记。
4. **统计递增**：`article_stats` 无记录则新建（views=1），并发冲突（DuplicateKeyException）降级 `incViews`；有记录直接 `incViews`

### appendView upsert 语义

`ArticleLogDaoImpl.appendView`：按 `user_id + article_id + action='view'` 查一条（LIMIT 1）→ 命中则 update `ip/ua/referer/view_at`（同一文章重复浏览只保留一行并刷新时间，实现"历史置顶"），未命中则 insert。非 view 的 action 直接 insert。

> 已知权衡：select-then-insert 无唯一约束，极小窗口的并发首看可能产生重复行 —— 展示层有内存去重故**功能无害**，暂不引入唯一键（计划 R5）。

---

## 读取链路

`GET /api/article-service/article/read/history`（网关白名单，游客可调但无数据）：

1. **DAO**：`ArticleLogDaoImpl.listHistoryLogs` ——
   ```sql
   SELECT article_id, view_at FROM article_log
   WHERE user_id = ? AND action = 'view'
   ORDER BY view_at DESC
   ```
   走 `idx_article_log_user_action_time` 索引，无 filesort
2. **Service**：`ArticleReadBizServiceImpl.listHistory()`
   - 内存按 `article_id` 去重，保留每篇最近一次 `view_at`
   - `articleDao.listByIds` 批量查文章（**不存快照，实时拿标题/摘要**，已删除文章自动消失）
   - 按历史顺序重排 → `viewedAt` 注入 VO
   - `enrichWithStats`（浏览/点赞/评论/收藏数）+ `enrichWithAuthorInfo`（Feign 批量查作者名/头像）

### 前端展示

- 位置：`src/views/UserProfile.vue` history tab（切 tab 触发 `loadHistory()`）
- API：`src/api/article.ts` 的 `getReadingHistory()`
- 渲染：自定义 `.history-item` 列表（不复用 ArticleCard）——浏览时间（`viewedAt || publishAt`）、标题、摘要、"再读一次"按钮跳详情

### 详情查询（关联统一）

文章详情页与列表跳转统一走 **真实文章 UUID**：`/article/:id` → `getArticleById` → `GET /article/read/{articleId}`（后端 `getById` 已与 `getBySlug` 对齐：统计 + 作者双 enrichment）。此前首页曾把 slug 塞进 `id` 字段、详情页按 slug 查询，已统一为 id；`/by-slug/{slug}` 端点保留为 legacy API。

---

## 关键设计决策

| 决策 | 结论 | 理由 |
|------|------|------|
| MySQL vs MongoDB | MySQL | 关系型数据 + 展示需 JOIN 文章表；行数有上界；项目无 Mongo 依赖（`_class` 列是早前尝试 Mongo 的遗留） |
| 游客是否写历史 | 不写 | 历史仅登录用户消费；表语义"匿名为 NULL"；游客高频刷新会放大写入且无人读取 |
| 日志失败影响计数？ | 不影响 | try-catch + warn。`view()` 是 `@Transactional`，抛出会导致 stats 一起回滚，反而扩大故障 |
| 写入时机 | 去重通过后、stats 之前 | 去重命中语义是"不算一次有效浏览"；stats 之前是为了绕开 early return |
| 每次浏览一行还是 upsert | upsert | 历史语义是"最近浏览"，不是事件流；upsert 天然支撑置顶排序且行数有界 |
| 重复行并发竞态 | 不修 | 展示层去重兜底，数据量极小，唯一键收益低于复杂度 |

---

## 接口清单

| 接口 | 方法 | 说明 |
|------|------|------|
| `/article/interaction/{articleId}/view` | POST | 记录浏览（去重 + 写历史 + 计数），返回最新观看次数 |
| `/article/read/history` | GET | 登录用户浏览历史（viewedAt 注入） |
| `/article/read/{articleId}` | GET | 按真实 ID 查详情（统计 + 作者 enrichment） |
| `/article/read/by-slug/{slug}` | GET | legacy，按 slug 查详情 |

---

## 测试覆盖

article-service 单测（JUnit 5 + Mockito，纯 mock 无 Spring 上下文），40 用例全绿：

| 测试类 | 覆盖 |
|--------|------|
| `ArticleInteractionBizServiceImplTest` (12) | view() 全分支：登录写日志、游客跳过、IP/用户去重命中、首次浏览新建 stats 仍写日志、并发冲突降级 incViews、日志失败不影响计数、文章不存在 |
| `ArticleLogDaoImplTest` (4) | appendView upsert 两分支、null 防御、非 view action 直插 |
| `ArticleReadBizServiceImplTest` (5) | listHistory 空/去重排序/viewedAt 回填/脏数据容错；getById、getBySlug 的统计与作者 enrichment |

运行：`JAVA_HOME="D:\DevelopKit\jdk-21.0.8" mvn test -pl oy-blog-service/article-service`

---

## 已知风险与后续工作

1. **端到端未验证**：article-service 尚未重启上线新代码；真实 SQL（upsert、历史查询）无 H2 集成测试，仅靠单测 + EXPLAIN 兜底
2. **并发重复行**（R5）：见 appendView 一节，有意不修
3. **极长 User-Agent**：`ua` 列 512 可容纳绝大多数；超长则该条历史静默丢失（try-catch 兜底，计数不受影响）
4. **返回计数并发滞后**：DuplicatedKey 降级路径返回值为竞态近似值，展示用途可接受
5. 若未来面向多租户平台（日浏览事件百万级），再评估把原始事件流转存 Mongo/ClickHouse，article_log 保留为汇总层
