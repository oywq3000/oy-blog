# 文章 AI 审核设计（Article AI Moderation Design）

- 日期：2026-08-29
- 状态：三节设计已获用户确认（状态机 / 数据流 / 改动清单）
- 涉及仓库：oy-blog（Java，本仓库，改动主体）+ BlogAgent（Python，G:\agentWorkplace\BlogAgent，新增审核端点）

## 1. 背景与目标

博客开放了创作中心，注册用户可发布文章。当前发布即公开（`POST /article/publish` 直接 `status=published`），无任何审核环节。

目标：接入 AI 审核，三态分流——**违规直接驳回（reject）、完全正常直接放行（approve）、有歧义转人工（manual）**。AI 的判定在 approve/reject 两条路上就是最终决定，人工只兜底"AI 拿不准"的情况。

架构约束：沿用项目既定原则——**AI 能力集中在 Python Agent（BlogAgent）**，Java 只做接线。

## 2. 现状盘点（2026-08-29 代码调查结论）

- **发布唯一入口**：`POST /article/publish` → `ArticleBizServiceImpl.publish`（`ArticleController.java:44-63`）。管理端 `AdminArticleController` → Feign `AdminArticleClient.publish` → 同一接口。**审核门插此一处即可覆盖全部发布场景**。
- **文章表已预留审核字段且零使用**：`Article.is_reviewed`（新建时置 0，`ArticleBizServiceImpl.java:270`）、`review_status`、`review_reason`（`Article.java:135-148`）。
- **评论审核已有完整模板**：`CommentAdminBizServiceImpl.audit` → 改 `comment.status` → 写 `ModerationLog`（action/reason/operator_id/acted_at，操作人从请求头 `X-User-Id` 取）。`moderation_log` 表自带 `article_id` 字段，直接复用。
- **公开读路径**：列表已过滤 `status='published'`（`ArticleDaoImpl.java:62-133`），但**详情 `getBySlug`/`getById` 不校验 status**（`ArticleDaoImpl.java:28-30`）——现有漏洞，本次修补，否则 `pending_review` 白审。
- **ES 同步**：发布事务提交后发 `ArticleIndexMessage`（`ArticleBizServiceImpl.java:95-96,109-120`），search-service 消费。审核介入后**只有 approve 才应发索引消息**。
- **编辑已发布文章**：复用 publish 接口带 id（`saveArticleBase` 的 isNew=false 分支），`article_content` 被 `saveOrUpdate` 直接覆盖；`ArticleRevision` 每次保存都插快照但无回滚用途（`ArticleBizServiceImpl.java:301-310`）。
- **Java 调 Python 先例**：agent-service 的 WebClient + `PythonSseClient`（SSE 流式）；article-service **无出站 HTTP 先例**，需新增同步 JSON WebClient。
- **角色体系**：`BlogRole{READER, GUEST, ADMIN}`；admin 判定 `user_role.code='admin'`（`UserAuthBizServiceImpl.java:252-256`）。
- **配置挂点**：Nacos + `@ConfigurationProperties`（先例 `HotWeightProperties`，`oy-blog.article.*` 前缀）。
- `article.status` 为 varchar，现有取值 draft/published（"archived" 仅注释）；`scheduled_publish_at` 未实现。
- `doc/sql` 下无 article 建表 DDL（库中表早已存在）；迁移文件照 `comment_moderation_migration.sql` 模式自写。

## 3. 需求（已与用户逐条确认）

1. **触发时机**：每次发布动作都审（含已发布文章的编辑保存）；草稿不审。
2. **豁免对象**：可配置（配置项豁免角色列表，默认仅 admin 角色豁免）。
3. **歧义处理**：新文章不发布（pending_review）；已发布文章的编辑，**旧版继续展示、新内容先审后生效**。
4. **违规规则**：内置固定六大类清单（违法内容 / 政治敏感 / 色情低俗 / 广告引流 / 人身攻击 / 垃圾内容），集中在 BlogAgent prompt 文件，不做管理端配置界面。
5. **执行方式**：BlogAgent 新增审核端点 + Java 同步调用（方案一）；AI 挂了/结果不可解析一律**转人工（fail-closed）**，绝不放行。
6. **审核范围**：标题 + 摘要 + 正文文本；封面图不审（文本模型无法看图）；tags 不审（低风险，编辑时正常生效）。

## 4. 状态机

文章对外状态沿用 `status` 字段（varchar，新增两个取值）：

| status | 含义 | 公众可见 |
| --- | --- | --- |
| `draft` | 草稿（现有） | 否 |
| `published` | 已发布（现有） | 是 |
| `pending_review` | 待人工审核（新增） | 否，作者可见"审核中" |
| `rejected` | 已驳回（新增） | 否，作者可见驳回原因，可改后重发 |

审核结论用现有 `review_status` 字段：

| review_status | 含义 |
| --- | --- |
| `approved` | AI 或人工放行 |
| `rejected` | AI 或人工驳回 |
| `manual` | AI 判歧义，转人工 |
| `exempt` | 豁免用户，未审直接放行 |

`review_reason` 存驳回/转人工的理由；`is_reviewed` 维持现有语义（0=未审 1=已审）。

状态转移：

```
草稿 draft ──发布──▶ 审核门（调 BlogAgent）
                     ├─ AI approve ─▶ published（发 ES 索引）
                     ├─ AI reject  ─▶ rejected（作者可改后重发）
                     └─ AI manual  ─▶ pending_review ─┬─ 人工通过 ─▶ published
                                                      └─ 人工驳回 ─▶ rejected

已发布文章编辑：AI 判 manual → 新内容进"待生效区"（article_pending_content），旧版继续 published；
              人工通过 → 替换生效；人工驳回 → 丢弃本次编辑。
```

## 5. 总体架构

```
创作中心前端 ──▶ POST /article/publish（唯一入口，用户端+管理端都经过）
                      │
                      ▼
            article-service 审核门（publish 方法内）
              ├─ 查豁免配置（oy-blog.article.moderation.exempt-roles）
              ├─ 豁免 → 直接放行（review_status=exempt）
              └─ 否则 → 新增 WebClient 同步调 BlogAgent POST /moderate/article
                            │（title + summary + 正文 Markdown）
                            ▼
                   BlogAgent（Python，复用现成 llm/config）
                            │ DeepSeek 结构化输出
                            ▼
                   {"verdict": approve|reject|manual, "reason": "..."}
                      │
            ┌─approve─┬──manual──┬─reject─┐
            ▼         ▼          ▼        ▼
       published  待人工/待生效  rejected （approve 才发 ES 索引）
```

管理端人工审核走 admin-service 新增的"待审队列"端点（列表 / 通过 / 驳回），照抄评论审核的 `audit → ModerationLog` 模式。

## 6. 数据流

### 6.1 发布新文章（含草稿转发布）

1. 查豁免配置：当前用户角色 ∈ `exempt-roles`？
   - 是 → 直接走原发布逻辑，`review_status=exempt`、`is_reviewed=1`，不调 AI（全程无感）。
2. 否 → 组装 `{title, summary, contentMd}` 同步调 BlogAgent `/moderate/article`。
   - **approve** → 原发布逻辑：`status=published`、`publishAt=now`、发 ES 索引消息；`review_status=approved`、`is_reviewed=1`、写 ModerationLog（action=`ai_approve`）。
   - **reject** → `status=rejected`、`review_status=rejected`、`review_reason=AI 理由`；内容照常保存（作者可改后重发）；不发 ES 索引；写 ModerationLog（`ai_reject`）；publish 返回体带"已驳回+原因"，前端提示。
   - **manual** → `status=pending_review`、`review_status=manual`；内容照常保存；不发 ES 索引；写 ModerationLog（`ai_manual`）；作者看到"审核中"，管理员在待审队列看到。

### 6.2 编辑已发布文章（先审后生效）

1. 豁免用户 → 直接覆盖生效（同现状）。
2. 否则**先调 AI 审新内容（此时旧内容还没被覆盖）**：
   - **approve** → 新内容覆盖生效，`status` 保持 published，`review_status=approved`。
   - **reject** → 本次编辑丢弃（旧版继续展示，作者收到驳回+理由），`review_status=rejected`。
   - **manual** → 新内容（title/summary/contentMd/contentHtml）写入 `article_pending_content`，旧版继续 published 展示，`review_status=manual`；作者再编辑时覆盖 pending 内容并重新 AI 审。

关键点：**审核发生在写库之前**（现在代码是"先覆盖内容再标 published"，改成"先审后写"），驳回/歧义时旧内容天然无损，无需回滚。

### 6.3 人工审核队列（admin-service 新增端点）

待审队列 = `status=pending_review` 的新文章 + 有 `article_pending_content` 的已发布文章（两类合成一张列表，各带 AI 的理由）。

管理员操作：

| 操作 | 效果 |
| --- | --- |
| 通过 pending_review 文章 | `status=published`、`publishAt=now`、发 ES 索引、ModerationLog（`manual_approve`） |
| 驳回 pending_review 文章 | `status=rejected`、`review_reason=人工理由`、ModerationLog（`manual_reject`） |
| 通过"编辑待审" | pending 内容替换进 article + article_content，清空 pending、ModerationLog（`manual_approve`） |
| 驳回"编辑待审" | 清空 pending（文章保持旧版不动）、ModerationLog（`manual_reject`） |

## 7. 出错兜底（一律 fail-closed）

| 故障 | 处理 |
| --- | --- |
| BlogAgent 连不上 / 超时（30s） | 按 manual 处理 → 转人工。**绝不放行** |
| AI 返回内容不是合法 JSON | 按 manual 处理 → 转人工 |
| 豁免配置读不到 | 按"不豁免"处理（保守方向） |
| `moderation.enabled=false` 总开关 | 全放行（等于关闭审核门，默认开启） |

## 8. BlogAgent 端点协议

```
POST /moderate/article
请求：{"articleId": "...", "title": "...", "summary": "...", "content": "..."}   // content = Markdown 纯文本
响应 200：{"verdict": "approve|reject|manual", "reason": "..."}
异常：422 参数缺失/超长；504 上游超时；5xx 上游失败
```

- 正文超过 `MODERATION_CONTENT_MAX_CHARS`（新 env，默认 8000）截断。
- 模型：默认 `deepseek-v4-flash`（便宜快），复用 `build_chat_model`。
- 判定实现：`MODERATION_PROMPT`（六大类清单 + "只输出一行 JSON `{"verdict":...,"reason":...}`，拿不准就 manual"）→ 非流式 invoke → JSON 解析带容错（提取第一个 `{...}`，失败 → manual）。
- **`MOCK_LLM=1` 联调设计**：正文含"违规"→ reject、含"歧义"→ manual、否则 approve——无 API key 也能全链路验证三种结果。

## 9. 组件改动清单

### 9.1 BlogAgent（Python 仓库）

- 新增 `app/moderation.py`：`MODERATION_PROMPT` + `moderate_article(...)` 判定函数（复用 `app/llm.py` 的 `build_chat_model`）。
- `app/main.py` 新增 `POST /moderate/article` 端点。
- `app/config.py` 新增 `MODERATION_CONTENT_MAX_CHARS`（默认 8000）。

### 9.2 article-service（核心）

- 新增 `ModerationService`：豁免判定、调 BlogAgent（**新增同步 JSON WebClient**，先例参考 agent-service 的 WebClient 配置）、应用 verdict、写 ModerationLog。
- `ArticleBizServiceImpl.publish` 插入审核门；publish 返回 DTO 增加审核结果字段，前端据此提示"已驳回+原因 / 审核中"。
- 新增 `ArticlePendingContent` 实体 + Mapper + DAO（待生效编辑区）。
- **修补**：`getBySlug`/`getById` 补 status 校验（非 published 不可公开读）。注意确认作者编辑草稿时的读取路径不受影响（见 §12 风险）。

### 9.3 admin-service（人工审核）

- 新增 `ModerationAdminController`（或并入现有 Admin 模块）：待审队列列表（两类合成）+ 通过/驳回端点，照抄评论审核模式（操作人从请求头取）。

### 9.4 配置（Nacos / yml，`@ConfigurationProperties("oy-blog.article.moderation")`）

```yaml
oy-blog:
  article:
    moderation:
      enabled: true            # 总开关
      exempt-roles: [admin]    # 豁免角色列表
      base-url: ${AGENT_PYTHON_URL:http://localhost:8001}
      timeout-ms: 30000
```

### 9.5 数据库迁移

`doc/sql/article_moderation_migration.sql`（照 comment_moderation_migration 模式，**先执行 SQL 再发布代码**，两段式部署）：

```sql
CREATE TABLE article_pending_content (
  article_id           VARCHAR(64) PRIMARY KEY COMMENT '文章ID（复用 article.id）',
  pending_title        VARCHAR(255) NOT NULL COMMENT '待生效标题',
  pending_summary      VARCHAR(500) COMMENT '待生效摘要',
  pending_content_md   LONGTEXT COMMENT '待生效 Markdown 正文',
  pending_content_html LONGTEXT COMMENT '待生效 HTML 正文',
  review_reason        VARCHAR(500) COMMENT 'AI 转人工理由',
  created_at           DATETIME NOT NULL,
  updated_at           DATETIME NOT NULL
);
```

- `article` 表**无需 ALTER**：`review_status/review_reason/is_reviewed` 列早已存在；`status` 为 varchar，新取值纯约定。
- `moderation_log` 表直接复用，action 新增取值 `ai_approve`/`ai_reject`/`ai_manual`/`manual_approve`/`manual_reject`（评论审核的 `approve`/`reject` 保持不变）。

## 10. 测试方案

**Python 侧（BlogAgent）**：
- moderation 模块单测：JSON 解析容错（正常 / 坏 JSON / 空响应 → manual）。
- 端点协议测试：三态判定、参数缺失 422、MOCK 模式三态触发词。

**Java 侧（article-service / admin-service）**：
- 发布流三态单测（伪 HTTP client）：approve→published+ES 消息、reject→rejected、manual→pending_review。
- 豁免逻辑：配置角色豁免时不调 AI。
- 编辑先审后生效：reject 不覆盖内容、manual 入 pending 表、approve 覆盖生效。
- 人工审核：§6.3 四种子场景。
- 详情接口 status 校验补丁的回归测试。

**联调验收**：`MOCK_LLM=1` 起 BlogAgent → Java 全链路三态走通 → 换真实 DeepSeek 验证三篇样例文章各得正确判定。

## 11. 验收清单

- [ ] 普通用户发布正常文章 → AI approve → 立即公开可见（列表 + 详情 + ES 检索）
- [ ] 普通用户发布违规文章 → AI reject → 不公开、作者看到驳回原因、可改后重发
- [ ] 普通用户发布歧义文章 → manual → 不公开、作者见"审核中"、管理员队列可见
- [ ] 管理员（豁免）发布 → 不调 AI 直接公开
- [ ] 已发布文章编辑 → 歧义时旧版继续展示、新内容待审；通过后替换；驳回后本次编辑丢弃
- [ ] 人工审核：通过/驳回四种子场景全部生效
- [ ] AI 服务宕机时发布 → 转人工队列，绝不直接放行
- [ ] 详情接口无法再通过 slug/id 读到非 published 文章
- [ ] 单测全绿 + MOCK 联调三态 + 真实 DeepSeek 三态

## 12. 风险与备注

- **详情接口修补的连带影响**：现在 draft 可通过 slug/id 公开读。修补后需确认创作中心作者读自己草稿的路径——若走公开详情接口，需提供作者视角读取（如校验作者本人可见）或复用已有 `listMine` 数据。实现计划中必须先排查这一点。
- **管理端审核 UI 归属**：管理前端尚未建设（计划见 `docs/superpowers/plans/2026-08-26-oy-blog-admin-frontend.md`）。本次后端端点先行，审核页面随管理前端计划落地；期间可先用接口/联调脚本验证。
- **ES 索引一致性**：pending_review 文章不上索引；rejected 文章若此前已发布过（编辑被驳回不影响旧版索引）索引保持旧版内容，与展示一致。
- **ModerationLog 兼容**：评论审核已有 `approve`/`reject` action，文章审核用 `ai_*`/`manual_*` 前缀区分来源，不动评论侧语义。
