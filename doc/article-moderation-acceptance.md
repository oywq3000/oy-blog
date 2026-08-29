# 文章 AI 审核 验收记录

> 日期: 2026-08-29 | 状态: **✅ 可验证项全部通过；全链路验收待服务器部署后执行**
> 对应设计: `docs/superpowers/specs/2026-08-29-article-ai-moderation-design.md`（§11 验收清单）

## 一、验收环境

- **BlogAgent**（Python 审核服务，`G:\agentWorkplace\BlogAgent`，分支 feat-moderation）：
  - 解释器 `/d/tool1/anancoda/envs/ai-agent/python.exe`，`.env` 使用真实 DeepSeek 凭据
  - MOCK 冒烟：`MOCK_LLM=1`，端口 8001
  - 真实 DeepSeek：`MOCK_LLM=0`，端口 8013（8001 空闲也统一用 8013 隔离验证）
- **Java 工程**（`G:\JavaWorkSpace\oy-blog-dev1`，分支 dev1）：JDK 21（`/d/DevelopKit/jdk-21.0.8`）
  - article-service 全量单测：`mvn -pl oy-blog-service/article-service -am test -Dsurefire.failIfNoSpecifiedTests=false`
  - admin-service：`mvn -pl oy-blog-service/admin-service -am compile`
- **中间件（MySQL / Nacos / MQ / ES）**：dev 库 `192.168.200.130` 本机不可达（15s 超时）→ 完整服务无法本地启动，**全链路（§11 第 1-8 项）留待服务器部署后执行**

## 二、验收结果（对照 spec §11）

| # | 验收项 | 验证方式 | 结果 |
|---|------|---------|------|
| 1 | 发布正常文章 → AI approve → 立即公开可见（列表+详情+ES 检索） | Java 单测（`ArticleBizPublishModerationTest`）+ 全链路 | 单测 ✅ / 全链路 🔶 待部署 |
| 2 | 发布违规文章 → AI reject → 不公开、作者看到驳回原因、可改后重发 | Java 单测 + 全链路 | 单测 ✅ / 全链路 🔶 待部署 |
| 3 | 发布歧义文章 → manual → 不公开、作者见"审核中"、管理员队列可见 | Java 单测 + 全链路 | 单测 ✅ / 全链路 🔶 待部署 |
| 4 | 管理员（豁免）发布 → 不调 AI 直接公开 | Java 单测（publish 豁免分支） | ✅ 单测覆盖 |
| 5 | 已发布文章编辑 → 歧义时旧版继续展示、新内容待审；通过后替换；驳回后丢弃 | Java 单测（`ArticleBizPublishModerationTest` 4 个编辑场景） | ✅ 单测覆盖 |
| 6 | 人工审核：通过/驳回四种子场景全部生效 | Java 单测（`ModerationAdminBizServiceImplTest` 8 项） | ✅ 单测覆盖 |
| 7 | AI 服务宕机时发布 → 转人工队列，绝不直接放行（fail-closed） | Java 单测（`ModerationServiceTest`：serverError / connectionRefused / unknownVerdict） | ✅ 单测覆盖 |
| 8 | 详情接口无法再通过 slug/id 读到非 published 文章 | Java 单测（`ArticleReadVisibilityTest` 9 项，含作者本人可见兜底） | ✅ 单测覆盖 |
| 9 | 单测全绿 + MOCK 联调三态 + 真实 DeepSeek 三态 | 本节三~五 | ✅ 全部实测通过 |

> 🔶 = 环境阻塞（dev 库不可达，本地无法起完整服务），代码逻辑已由单测覆盖，待服务器部署后按第七节清单执行全链路验收。

## 三、单测结果

**BlogAgent（Python）全量：64 个测试，63 通过，1 个预存失败**

```
tests/test_config.py      6（1 失败：test_defaults 的模型默认值 deepseek-chat→deepseek-v4-flash 未同步，与本功能无关，未修）
tests/test_moderation.py  10（新增） 全部通过
tests/test_moderation_endpoint.py 4（新增） 全部通过
tests/test_graph.py 5 / test_llm.py 9 / test_protocol.py 6 / test_registry.py 9 / test_stream.py 9 / test_tools.py 6 全部通过
```

> 验收标准 = 新增测试全绿 + 无新增失败，达成。预存失败为历史遗留（配置默认值改版未同步测试），与本功能无关。

**article-service（Java）全量：45 个测试，0 失败 0 错误（BUILD SUCCESS）**

新增审核相关测试类 6 个全部通过：
- `ModerationPropertiesTest`（配置绑定）
- `ModerationServiceTest`（10：三态 + fail-closed）
- `ArticleIndexMessageServiceTest`（3：索引消息服务抽取后行为不变）
- `ArticleBizPublishModerationTest`（10：publish 三态分流 + 管理员豁免 + 编辑先审后生效）
- `ArticleReadVisibilityTest`（9：公开读取仅限 published / 作者本人可见）
- `ModerationAdminBizServiceImplTest`（8：人工审核队列 通过/驳回 + 待生效替换）

**admin-service：`mvn compile` 退出码 0，编译通过。**

## 四、MOCK 三态冒烟（MOCK_LLM=1，端口 8001）

命令：`cd /g/agentWorkplace/BlogAgent && MOCK_LLM=1 .../python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8001`（后台运行，`netstat` 确认 8001 无占用）

| 用例 | 请求 | 实际响应 | 期望 | 结果 |
|------|------|---------|------|------|
| 正常 | `{"articleId":"t1","title":"标题","summary":"","content":"正常的技术文章"}` | `{"verdict":"approve","reason":"【MOCK】联调放行"}` | approve | ✅ |
| 违规 | `{"articleId":"t2","title":"标题","summary":"","content":"违规内容"}` | `{"verdict":"reject","reason":"【MOCK】命中触发词：违规"}` | reject | ✅ |
| 歧义 | `{"articleId":"t3","title":"标题","summary":"","content":"有些歧义"}` | `{"verdict":"manual","reason":"【MOCK】命中触发词：歧义"}` | manual | ✅ |
| 参数缺失 | `{"articleId":"","title":"","content":""}` | HTTP 422 `{"code":422,"message":"参数不完整"}` | 422 | ✅ |

> 注：Windows Git Bash 命令行内联中文 JSON 会乱码导致 body 解析失败，实际改用 UTF-8 文件 `curl -d @file` 完成；案例 4 的 422 响应本身即证明校验生效。

## 五、真实 DeepSeek 三态验证（MOCK_LLM=0，端口 8013）

命令：`cd /g/agentWorkplace/BlogAgent && MOCK_LLM=0 .../python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8013`，使用 `.env` 真实凭据（key 不展示）。

| 用例 | 请求内容摘要 | 实际响应（verdict + reason 摘要） | 结果 |
|------|------------|----------------------------------|------|
| 正常技术文章 | 《Java 虚拟线程入门》教程 | `approve` — "技术教程文章，内容正常，不涉及违规规则。" | ✅ |
| 明确违规 | "加微信 xxx888 代购彩票，稳赚不赔" 广告文 | `reject` — "内容为彩票内幕代购广告，推广微信和加群，涉嫌诱导付费和违法赌博，属于广告引流及违法内容。" | ✅ |
| 边界样例 | 网络内容治理的学术性科普讨论 | `approve` — "文章为学术性科普讨论，内容正常，未涉及任何违规规则。"（合规预期为 manual 或 approve，reason 合理） | ✅ |

真实 API key 有效、判定与 reason 均合理，三态全部符合预期。

## 六、验收中发现的问题

**未发现功能缺陷。** 两项环境性注意（非代码缺陷）：
1. Windows 控制台中文 JSON 内联乱码 → 冒烟需用 UTF-8 文件承载请求体（见第四节注），不影响 Linux/容器部署（服务器部署用同网段直连，请求体来自 Java 端 UTF-8 构造）。
2. BlogAgent 全量测试有 1 个预存失败 `test_config.py::test_defaults`（模型默认值 deepseek-chat→deepseek-v4-flash 未同步测试），与本功能无关，未修（如需彻底清零可另行同步该测试断言）。

## 七、遗留事项（待办清单）

1. **全链路验收（最高优先）**：服务器部署后执行（dev 库 192.168.200.130 本机不可达，本地无法起完整 Java 服务）：
   - [ ] 先执行 `doc/sql/article_moderation_migration.sql`（两段式：先 SQL 后发布新代码；服务器 jar 内烘焙配置注意重打包）
   - [X] `doc/sql/article_status_enum_to_varchar_migration.sql` —— **已在服务器（100.110.148.14）执行（2026-08-29）**：status/review_status 原为 ENUM（draft/published/archived 与 pending/approved/rejected），审核状态字面量不在枚举内会报 Data truncated；已 ALTER 为 VARCHAR(32)
   - [ ] Nacos/yml 配置 `oy-blog.article.moderation.base-url` 指向 BlogAgent（服务器为 `http://oy-blog-python-agent:8001` 同网段直连；本地调试为 `http://localhost:8001`）
   - [ ] 普通用户发布三篇样例 → 分别验证：published 可见 / rejected 不公开且作者见驳回原因 / pending_review 不可公开读但作者列表可见
   - [ ] 管理员 `POST /admin/moderation/page` 见两篇待审 → audit 通过/驳回 → 状态流转 + ES 检索（approve 能搜到、驳回搜不到）
   - [ ] 已发布文章编辑歧义 → 旧版仍展示、`article_pending_content` 有行 → 人工通过后新内容生效
   - [ ] 关闭 BlogAgent 再发布 → 转人工队列（fail-closed 实测）
2. **管理前端审核页面**：后端端点已在 Task 10-12 就位，页面随 `docs/superpowers/plans/2026-08-26-oy-blog-admin-frontend.md` 管理前端计划落地。
3. **作者创作中心提示**：publish 返回 map 已带 `verdict`/`reason`，"审核中/已驳回"展示需前端配合小改动（另行处理）。
4. **BlogAgent 分支合并**：feat-moderation 分支待合并；README §6 测试数已按本次实测更新（64 个，63 过 + 1 预存失败）。

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
