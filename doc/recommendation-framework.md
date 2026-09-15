# 个性化文章推荐（相似内容画像）机制

## 概述

「猜你喜欢」：基于**用户/游客的标签画像**做相似内容推荐。行为事件流增量建画像，读端按画像匹配相似文章，冷启动回退热榜。登录用户与游客都做真正个性化；游客画像带 TTL 自动消散。

| 维度 | 说明 |
|------|------|
| 数据形态 | Redis ZSet：`member=标签id`，`score=累计权重`（权重复用热榜 `HotWeightProperties`：views×1 + likes×2 + favorites×3 + comments×5，取消类为负权） |
| 画像来源 | **在线**：行为事件消费者增量；**离线**：一次性 backfill 扫历史表 |
| 冷启动 | 画像空 / Redis 故障 → 回退热榜链路（Redis 榜 → MySQL 总榜），**接口永不报错** |
| 消费语义 | 装饰功能：消费者 catch-all + 限流 + 照样 ack，绝不阻塞分区、绝不影响主链路 |
| 存储选型 | Redis（画像/已读/缓存）+ MySQL（标签/行为历史表只读），**无新增表、无新中间件、零 SQL 变更** |

设计原则：**打分透明可解释**（推荐的就是"用户读过/点过最多的主题下的文章"），不引黑盒参数；画像以 tag_id 为成员，标签增删不影响已有画像语义（失效 tag 匹配 0 行 → 自然走冷启动）。

---

## 架构（三通道，共用一个 Redis 画像）

```
【在线】用户操作 → article.behavior (Redpanda)
   ▼
ProfileConsumer（新消费组 article-recommend-profile）
   查文章标签 → 逐个 ZINCRBY 画像ZSet（权重=事件weight）
   游客额外写 consumed 已读集 + 设 TTL 7天
   ▼
▓ profile:user:{id}（登录，无 TTL）/ profile:guest:{id}（游客，TTL 7天）▓
   ▲                              │
   │                              │ 游客：consumed:guest:{id}（TTL 7天）
【离线】 RecommendBackfillRunner（开关控制，一次性）
   扫 article_log/like/favorite → 全量重建 profile:user:{*}（先删后建幂等）
   │
   ▼
【读端】GET /api/article-service/article/read/recommend
   画像 top30 → 候选(published, 排除已消费) → Σ画像权重排序(同分views降序) → top100 → 缓存10min → 分页
     空画像/Redis异常/页越界 → 回退热榜
```

在线与热榜互不干扰（不同消费组、不同 key、同哲学）；读端只读，绝无写库。

---

## Redis 键契约

| key | 类型 | 说明 |
|---|---|---|
| `rec:profile:user:{userId}` | ZSet | 登录用户标签画像，member=tag_id，score=权重（无 TTL） |
| `rec:profile:guest:{guestId}` | ZSet | 游客标签画像（TTL 7 天，匿名身份自动消散） |
| `rec:consumed:guest:{guestId}` | ZSet | 游客已消费文章集合（member=article_id，TTL 7 天） |
| `rec:result:user:{userId}` / `rec:result:guest:{guestId}` | String | 排序后文章 id 列表（逗号分隔），TTL 10 分钟 |

集中定义：`com.oyproj.common.RecommendKeys`（前缀 `rec:` 与 `CachePrefix` 区分）。登录用户"已消费"不走 Redis，读端实时查 `article_log ∪ article_like ∪ article_favorite`（MySQL 是权威事实）。

---

## 三个组件

### 1. ProfileConsumer（在线画像，《装饰功能》样本）
- 新消费组 `${spring.kafka.consumer.profile-group-id:article-recommend-profile}`，与热榜组互不抢 offset。
- 每条事件：`ArticleTagDao.listTagIdsByArticleIds` 查文章标签 → 对每个标签 `ZINCRBY +event.weight`。
- 游客（`userId` 以 `CachePrefix.GUEST_ID` 前缀开头）额外 `zAdd` 进 consumed 集合并对两把 key 设 7 天 TTL。
- `userId` 为 null（无头流量）→ 直接跳过；事件带 GUEST_ID 属生产常态（网关注入），**`ArticleBehaviorEvent` 注释已纠正确认**。
- 失败处理：catch-all + `FaultLogThrottle` 限流 + `finally ack`——绝不阻塞分区。**运维预期：新组首次上线会从保留期起点重放全量事件，期间 DB 标签查询有洪峰，属正常。**
- 事件到标签的查询是逐条 `SELECT tag_id FROM article_tag WHERE article_id=?`（走既有索引）；个人博客量级无压力。

### 2. RecommendBackfillRunner（离线补齐，《一次性》）
- 开关：`oyblog.recommend.backfill-enabled=true`（@Value，注意与其余配置前缀 `oy-blog.article.recommend.*` 分开登记）。
- 数据源：`article_log(view)`、`article_like`、`article_favorite`（three mapper `selectList`）。游客行跳过。
- **幂等语义 = 全量重建**：写 `profile:user:{id}` 前先 `remove` 再逐标签 `zAdd`——重复执行不翻倍。
- `run()` 整体 try/catch：失败记 error（"backfill 中断，可重跑恢复"），**绝不因 backfill 失败让应用启动崩掉**。

### 3. 打分引擎 + 读端接口
- 引擎 `RecommendationBizService.recommendArticleIds(actorId, guest) → List<String>`：空列表 = 冷启动。
  1. 画像 top30（`reverseRangeWithScores`）；2. 候选 = 挂过这些标签的 **published**（`status=="published" && deletedAt==null`）文章；3. 排除已消费；4. `score=Σ 命中标签画像权重`，同分按 `article_stats.views` 降序，再按文章 id 升序；5. 取前 `resultCacheSize` 条。
  - **Redis 读全部包 try/catch，异常一律 `List.of()`** → 调用方落回热榜（spec §7 的"永不报错"由此兑现）。
- 读端 `ArticleReadBizServiceImpl.recommend(pageNum,pageSize)`：
  - 读结果缓存（缓存命中直接组装分页，不打引擎）；引擎空 → `listPublishedByHot(...,"7d")` 回退；引擎有结果 → 写缓存（put 同样 try/catch）再组装。
  - 组装复用热榜的 `buildRankedPage`（`long from` 防 int 溢出；页越界返回空页而非 null）。

---

## 部署顺序（重要：两条初始化路径不得对冲）

新消费组首启会**全量重放**历史行为事件（天然在线建画像），与 backfill 的"先删后建"**会互相对冲**：
- backfill 先跑完、重放后到 → 同一批历史行为被计两次（画像权重 ≈ 2×，相对排序基本不变但口径错）。
- 重放先到、backfill 后跑 → backfill 的 remove+rebuild 覆盖重放结果，口径正确（符合 spec §5"与在线通道同一口径"）。

**标准顺序：**
1. 构建部署 article-service（**不**开 backfill 开关），让 `article-recommend-profile` 组全量重放排水（可在 Kibana 观察该组日志与 `rec:profile:*` 键增长）。
2. 重放稳定后，开 `oyblog.recommend.backfill-enabled=true` **重启一次**跑 backfill（其先删后建权威覆盖），确认日志"重建 N 个用户画像"后**立即关回 false** 再重启。
3. 前端构建部署 `oy-blog-front`。

**上线验证（Kibana `svc=article-service`）**：
- `article-recommend-profile` 组日志正常，游客事件 `userId` 确为 GUEST_ID（终审裁决：javadoc 与代码相悖，以线上事实为准——若真为 null，改 `view()` 调用点传参一行，ProfileConsumer 对 null 已 skip 不炸）。
- 抽查 1-2 个老用户 `rec:profile:user:*` 的 top 标签是否符合其浏览史；游客随意访问几篇后 `rec:profile:guest:*` 出现且 TTL 生效。

---

## 接口

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/article-service/article/read/recommend?pageNum&pageSize` | GET | 猜你喜欢分页（画像推荐，冷启动回退热榜）。游客/登录通吃，网关已注入身份头。放行走既有 `/article/read/**` 白名单，零网关改动 |

前端：`HomeView.vue` 热门区块上方「✨ 猜你喜欢」卡片区（复用 `ArticleCard`，空数据整块不渲染），`api/article.ts#getRecommendations`。

---

## 配置（`RecommendProperties`，前缀 `oy-blog.article.recommend`）

| 键 | 默认 | 说明 |
|---|---|---|
| `profile-top-tags` | 30 | 画像参与打分 top N |
| `result-cache-size` | 100 | 缓存成品列表长度 |
| `result-cache-ttl-seconds` | 600 | 读端结果缓存秒数 |
| `guest-ttl-seconds` | 604800 | 游客画像/已读集 TTL（7 天） |
| `oyblog.recommend.backfill-enabled`（@Value） | false | backfill 一次性开关（注意前缀不同） |

---

## 已知取舍（有意为之）

- 游客已消费集上限 499 条读取（`reverseRangeWithScores(0,499)`）：7 天 TTL 内消费 >500 篇的游客可能复推已读——个人博客量级可接受，上限即取舍。
- backfill 全量 `selectList` + 全库文章 id 单条巨型 IN：博客量级无压力；文章上万后再分批。
- 引擎 top30 里被 unlike 拉负的标签也会进 `listByTagIds` 查询（不贡献分数只增 IN 体积）——性能级，非正确性问题。
- Redis 写中途失败会留下部分写入的用户 key：backfill 全量重建 + 可重跑，自然收敛。
- 推荐做深分页（第 11 页起）返回空页：首页模块只取第 1 页，翻页扩展点留给前端。