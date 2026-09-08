# 专栏功能后端验收清单（article-columns）

> 分支: `feat-columns` | 代码: 88eced0..2b8e188（T1-T7，9 个 commit）| 日期: 2026-09-08
> 状态: **代码完成 + 本地单测全绿；全链路（起服 curl/页面）验收待执行**
> 服务器执行记录：**待部署时补**（本清单第三节供部署后逐条打勾；第四节为部署顺序提醒）

## 一、本期交付范围

| 层 | 内容 | commit |
|----|------|--------|
| DDL 迁移 | `doc/sql/article_series_migration.sql`：article_series / article_series_item 建表归档 + cover_url 列补齐 + uk_series_article 唯一索引（幂等，MySQL 8 PREPARE 单语句拆分） | a25ab70 / c9ef4d7 |
| 字段扩展 | ArticleSeries.coverUrl；ArticleSaveDto.seriesIds（发布时全量替换 ≤3）；SeriesSaveDto/SeriesAdminVo.coverUrl；i18n key（series.not_found / series.limit_exceeded） | 6b099d8 |
| 成员关系服务 | 收录（幂等去重 + uk 兜底）/ 移除 / 相邻上移下移 / 整文全量改绑 / 级联清理（TDD 驱动） | caeba96 |
| 接线钩子 | publish 链落库专栏关系（draft 不落库）；删文章/删专栏级联清理成员行 | e76dbc0 |
| 前台读 SQL | 成员分页/计数/按栏分组计数（仅 published 且未软删） | 9c0d895 |
| 前台读接口 | 专栏列表（含已发布成员数角标）/ 专栏详情分页（sort_order 升序）/ 文章详情带所属专栏链接（seriesList）；网关白名单由既有 `/article-service/article/read/**` 覆盖，本期未改网关 | 9441f91 |
| 管理接口四层链 | article-service `/article/admin/series/*` 成员端点 + Feign 客户端 + admin BFF `/admin/article/series/*`（含权限注解）；改绑路由对齐 `/admin/article/{articleId}/series` | 7209ee3 / 2b8e188 |

## 二、本地已验证（单测层）

article-service 全量 **97 用例全绿**（`mvn -pl oy-blog-service/article-service -am test`，JDK 21）；src/test 不入库。admin-service 无测试源（`mvn compile` 通过，管理端点全部转发 article-service 同一逻辑）。

专栏相关用例 24 项：

| 测试类 | 用例 | 覆盖点 |
|--------|------|--------|
| ArticleSeriesBizServiceImplTest（16） | add 追队尾 max+1 / 跳过已在栏内 | 收录幂等 |
| | add 上限拒绝 | 单文 ≥3 栏抛 series.limit_exceeded，事务整体回滚 |
| | add 专栏不存在 | NotFound（series.not_found） |
| | remove 命中删行 / 未命中返回 false | 移除 |
| | move 中段上移邻位 swap | 排序 |
| | move 队首 up / 队尾 down / 非法方向 / 非成员 → no-op false | 排序边界 |
| | replace 删旧增新（保留仍在集合中的栏位） | 整文全量改绑 |
| | replace 未知专栏拒绝 | 全量校验后操作，不产生部分改动 |
| | clearByArticle / clearBySeries | 级联清理 |
| | listMembers join 标题、滤软删文章 | 管理成员列表 |
| ArticleSeriesQueryTest（3，H2） | selectSeriesMemberPage 滤草稿+软删 / selectSeriesMemberCount 只计 published / selectPublishedCountGroupBySeries camel 映射 | 前台读 SQL |
| ArticleReadBizServiceImplTest（3/15） | getSeriesDetailShouldFilterAndOrder / listSeriesReadShouldAttachCount / getByIdShouldEnrichSeries | 前台读接口 |
| ArticleBizServiceImplTest（2/3） | saveRelationsShouldPersistSeries / deleteShouldClearSeriesItems | 发布链落库 + 删文级联 |

## 三、全链路验收清单（起服后逐条执行，T15 前置）

> 环境：按 `doc/local-dev-environment.md` 起中间件（Nacos/MySQL/Redis/RabbitMQ/ES）与 gateway(8080)/article-service/admin-service；或服务器同网段直连。
> 提示：Git Bash 内联中文 JSON 会乱码，请求体用 UTF-8 文件 `curl -d @body.json` 携带（历次验收经验）。

### A. 迁移与结构（全新环境才需执行 SQL；目标库已执行过则直接验结构）

- [ ] 全新库：先执行 `doc/sql/article_series_migration.sql`（命令见文件头，注意 `SET NAMES utf8mb4` / `--default-character-set=utf8mb4` 防注释乱码）；连跑三遍，后两遍输出全为 skip（对齐 dev 库 2026-09-08 执行记录）
- [ ] `SHOW CREATE TABLE article_series`：含 cover_url 列
- [ ] `SHOW CREATE TABLE article_series_item`：含 uk_series_article 唯一索引

### B. 前台读（公开白名单，无需 token，一律 200）

登录取 token 备用：`POST /user-service/auth/login` `{"username":"<admin>","password":"<pwd>"}` → `data.token`，下文 `$T` 代指。

- [ ] 创建专栏（带封面）：`POST /admin-service/admin/article/series`（需 `$T`）`{"name":"<栏名>","description":"<描述>","coverUrl":"https://.../cover.jpg"}` → 返回专栏 id
- [ ] 专栏列表：`GET /article-service/article/read/series` → 按创建时间升序，coverUrl 原样回显，`articleCount` = 已发布未软删成员数（空栏为 0）
- [ ] 专栏详情：`GET /article-service/article/read/series/{seriesId}?pageNum=1&pageSize=10` → 返回 id/name/description/coverUrl + 分页元数据（total/totalPages），成员按 sort_order 升序（同序按 publish_at 降序）
- [ ] 草稿/审核中不入列：对一栏收录 1 篇草稿 + 1 篇已发布 → 详情 total 只计 1，成员仅已发布文
- [ ] 专栏不存在：`GET /article-service/article/read/series/nonexistent-id` → 失败 Result，message=`专栏不存在`
- [ ] 文章详情带专栏链接：`GET /article-service/article/read/{articleId}` 或 `/article-service/article/read/by-slug/{slug}` → 挂栏文章含 `seriesList`（seriesId/name/coverUrl/sortOrder/totalCount，按 seriesId 稳定序）；未挂栏文章 `seriesList=null`

### C. 发布链绑定（管理豁免路径与创作路径同走 saveRelations）

- [ ] `POST /admin-service/admin/article/publish`（需 `$T`）body 含 `seriesIds:[s1,s2]`（≤3）→ 发布成功后 `GET .../read/series/{id}` 该文在列、`articleCount` 相应 +1
- [ ] 幂等重发：同文再 publish 相同 seriesIds → 成员行不重复、计数不变
- [ ] 改绑：编辑已发布文章 seriesIds 改为 `[s2]` 再 publish → 关系全量替换：s2 保留在列、s1 被移出（与标签同语义）
- [ ] 超限报错：publish 带 4 个 seriesIds → 失败 Result，message=`一篇文章最多加入 3 个专栏`，且整篇发布回滚（库中无该文章、各栏成员数不变）
- [ ] draft 不落库：`POST /admin-service/admin/article/draft` 带 seriesIds → 存稿成功，但前台成员与管理成员列表均无该文；改由 publish 提交时以请求中的 seriesIds 为准
- [ ] 收录不存在专栏 id（C 项接口 `POST /admin-service/admin/article/series/{badId}/articles`）→ message=`专栏不存在`

### D. 管理端成员操作（需 `$T`，权限 admin:article:write / read）

| 动作 | 请求 | 预期 |
|------|------|------|
| 批量收录 | `POST /admin-service/admin/article/series/{seriesId}/articles` `{"articleIds":[id1,id2]}` | 返回成功条数；新成员追到队尾（max+1） |
| 重复收录 | 同上再发一次 | 已在栏内跳过，成功条数为 0（幂等） |
| 收录超限文 | 收录已占 3 栏的文章 | 失败 `一篇文章最多加入 3 个专栏`，整批回滚（无部分收录） |
| 成员列表 | `GET /admin-service/admin/article/series/{seriesId}/members` | 按 sort_order 返回 articleId/title/status/coverUrl/sortOrder；软删文章不出现；草稿/审核中文章可出现（status 原样透出） |
| 移出 | `DELETE /admin-service/admin/article/series/{seriesId}/articles/{articleId}` | 成功 data=true；对不存在成员再删 data=false |
| 上移/下移 | `PUT /admin-service/admin/article/series/{seriesId}/articles/{articleId}/move?direction=up\|down` | 与邻位 swap（可对调两篇观察成员列表 sort_order 互换）；队首 up / 队尾 down / 非法 direction → data=false 且顺序不变 |
| 整文改绑 | `PUT /admin-service/admin/article/{articleId}/series` `{"seriesIds":[...]}` | 全量替换；>3 或含不存在专栏 → 报错整体回滚；seriesIds 传空数组 = 清空该文全部专栏关系 |
| 级联-删专栏 | `DELETE /admin-service/admin/article/series/{seriesId}`（既有端点，本期加级联） | 该栏成员行清空；前台列表不再出现该栏 |
| 级联-删文章 | `DELETE /admin-service/admin/article/{articleId}`（软删，既有端点） | 成员行清理；members 列表与前台分页不再出现该文 |

### E. 待管理前端（阶段 B）落地后补验的页面项

- [ ] 专栏管理页：建栏（含封面上传回显）、收录/移出/排序、文章编辑页专栏多选（≤3 防抖与报错文案展示）
- [ ] 博客前台：专栏列表角标、专栏详情分页、文章详情页"所属专栏"链接跳转

## 四、部署顺序提醒（服务器侧生效前必看）

1. **迁移状态**：`doc/sql/article_series_migration.sql` 已于 **2026-09-08 提前执行于 dev 库（100.110.148.14，oyblog）**——跑三遍幂等验证 + 修复轮（PREPARE 单语句拆分）后重跑两遍全 skip。
   - 服务器若仍以此库为目标库：**部署新代码无需重跑 SQL**（脚本幂等，确需重跑全 skip，无副作用）。
   - **全新环境**（新库/新服务器）：必须**先执行 SQL 再发新代码**（两段式；旧代码不受影响，可先 SQL 后择机发码）。
2. 发码顺序与既有部署一致：先 SQL（如需要）→ 构建部署 jar（注意 jar 内烘焙配置重打包）→ 重启服务。
3. 部署后按第三节清单执行全链路验收，并将结果回填本文件（服务器执行记录）。

## 五、已知取舍 / 行为约定

1. **收录不校验文章存在性/状态**：收录接口只查专栏与"占用栏数"，不校验文章是否存在或已发布；不存在/已软删的文章行永不出现于成员列表，前台 SQL 层再做 published + 未软删过滤。管理端从文章列表选真实 id 调用即可。
2. **上限 3 校验两处、策略不同**：publish/整文改绑（全量替换语义）对新集合 >3 直接整批拒绝；收录按"逐篇已占栏数 ≥3"拒绝，且单篇超限会令**整批收录回滚**（无部分成功），幂等重试友好。
3. **draft 不落库专栏关系**（与标签一致）：草稿保存携带 seriesIds 被忽略，仅 publish 链落库/替换；编辑已发布文章时关系在提交编辑（待审）时即替换，不等内容审核通过——审核驳回只丢弃待生效内容，不还原专栏关系（与标签行为一致，前端需知悉）。
4. **文章详情 seriesList 按 seriesId 稳定排序展示**，非栏内 sort_order（栏内顺序以专栏详情页为准）；无专栏关联时 seriesList=null，前端判空隐藏。
5. **删专栏/删文章走软删或物理删各自级联清成员行**（删专栏=物理删栏+清关系；删文章=软删+清关系），不依赖外键，由服务内事务保证。
6. **i18n**：新文案（`专栏不存在` / `一篇文章最多加入 3 个专栏`）中英双语齐备；前端需按 Result.message 展示而非硬编码。
7. 专栏列表/详情对同一专栏的计数口径均为"已发布且未软删"，管理端 members 列表口径为"全部状态但非软删"，两口径不同属设计预期。
