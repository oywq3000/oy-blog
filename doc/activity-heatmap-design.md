# 用户活跃度热力图（GitHub 风格贡献图）设计方案

> 状态：已实现（2026-08-16） ｜ 涉及：article-service（后端）、oy-blog-front（前端） ｜ 无新表、无 DDL、无网关改动

## 1. 背景与目标

个人主页的「活跃度」卡片原本用前端 `Math.random()` 生成模拟数据（`UserProfile.vue`），前后端均未实现。本方案打通真实数据链路：后端按天聚合用户最近 12 个月的活跃事件数，前端替换模拟数据渲染真实的 GitHub 风格贡献热力图。

## 2. 需求决策（已确认）

| 决策项 | 结论 |
|---|---|
| 统计行为 | 仅 4 类：**发文章、评论、回复、文章点赞、收藏**（不含浏览 article_log、不含评论点赞 comment_reaction） |
| 接口范围 | 仅当前登录用户 `GET /article/stats/heatmap/me`；DAO 带 userId 参数，扩展公开接口只需加 controller 方法 |
| 返回格式 | 扁平 `[{date, count}]` 列表，前端组 52×7 网格并算 intensity |
| 缓存 | 不加 Redis（个人博客规模，聚合 SQL 足够，避免缓存失效复杂度） |

## 3. 整体数据流

```
前端 UserProfile.vue (loadHeatmap)
   → GET /api/article-service/article/stats/heatmap/me  (网关注入 X-User-Id)
   → article-service ArticleController.getMyHeatmap()
   → ArticleStatsBizService（游客→空列表；否则窗口 LocalDate.now().minusMonths(12)）
   → UserActivityHeatmapDao.xml（5 表 UNION ALL 按天聚合）
   → Result<List<HeatmapDayVo>> [{date, count}]
   → 前端 buildHeatmapData() → 52×7 网格（intensity 4 档）
```

## 4. 后端设计（article-service）

### 4.1 API 契约

```
GET /article/stats/heatmap/me
游客 / 无 X-User-Id → Result.ok([])
响应示例：
{
  "errCode": 200, "errMsg": null, "isSuccess": true,
  "data": [
    { "date": "2025-08-20", "count": 3 },
    { "date": "2026-08-15", "count": 1 }
  ]
}
```

### 4.2 文件清单

| 文件 | 职责 |
|---|---|
| `domain/vo/HeatmapDayVo.java` | `{LocalDate date; Long count}`，@Data @Builder |
| `dao/UserActivityHeatmapDao.java` | `@Mapper` 接口，`listActivityDays(userId, startDate)` |
| `resources/com/oyproj/dao/UserActivityHeatmapDao.xml` | 聚合 SQL（见下） |
| `service/ArticleStatsBizService.java` + `impl/` | 游客判定 + 透传 DAO |
| `controller/ArticleController.java` | 新增端点 `/stats/heatmap/me` |

### 4.3 聚合 SQL 核心

5 张行为表 UNION ALL 后按天聚合，**窗口下界由 Java 侧传参**（`LocalDate.now().minusMonths(12)`），不用 SQL 侧 `NOW()/CURDATE()`——避免依赖 DB 服务器时区：

```sql
SELECT event_date AS date, SUM(day_cnt) AS count
FROM (
    SELECT DATE(publish_at), COUNT(*) FROM article
    WHERE author_id = #{userId} AND status = 'published'
      AND deleted_at IS NULL AND publish_at >= #{startDate}
    GROUP BY DATE(publish_at)
    UNION ALL
    SELECT DATE(comment_at), COUNT(*) FROM comment
    WHERE user_id = #{userId} AND comment_at >= #{startDate} GROUP BY DATE(comment_at)
    UNION ALL
    SELECT DATE(reply_at), COUNT(*) FROM comment_reply
    WHERE user_id = #{userId} AND reply_at >= #{startDate} GROUP BY DATE(reply_at)
    UNION ALL
    SELECT DATE(liked_at), COUNT(*) FROM article_like
    WHERE user_id = #{userId} AND liked_at >= #{startDate} GROUP BY DATE(liked_at)
    UNION ALL
    SELECT DATE(favorited_at), COUNT(*) FROM article_favorite
    WHERE user_id = #{userId} AND favorited_at >= #{startDate} GROUP BY DATE(favorited_at)
) t
GROUP BY event_date ORDER BY event_date
```

设计要点：
- 仅 `article` 有软删（`deleted_at`）与状态过滤；comment/comment_reply/article_like/article_favorite 无软删列（已核实实体）
- `LocalDate` 经 MyBatis 内置 `LocalDateTypeHandler` 绑定，无需配置；XML 按 `@Mapper` 接口 FQN 同路径自动加载（仿 `UserArticleStatDao` 先例）
- 游客判定：`userId == null || userId.startsWith(CachePrefix.GUEST_ID.getPrefix())`，与 ArticleInteractionBizServiceImpl 同款

## 5. 前端设计（oy-blog-front）

### 5.1 文件清单

| 文件 | 职责 |
|---|---|
| `src/utils/heatmap.ts`（新增） | 纯函数：`toIntensity` / `formatLocalDateKey` / `buildHeatmapData` |
| `src/api/article.ts` | 新增 `getMyHeatmap()` → `/article/stats/heatmap/me` |
| `src/views/UserProfile.vue` | 删除 mock，新增 `loadHeatmap()` + `isHeatmapLoading`，onMounted 调用 |
| `src/locales/en.ts` / `zh.ts` | 新增 `profile.heatmapTooltip`（`{count} contributions on {date}` / `{date} 贡献 {count} 次`） |

### 5.2 核心算法

```ts
/** count → rgba alpha，匹配图例 4 档 0.1/0.4/0.7/1 */
toIntensity(count): 0 → 0；1 → 0.1；2-3 → 0.4；4-6 → 0.7；≥7 → 1

/** 本地时区 YYYY-MM-DD（旧 mock 用 toISOString 是 UTC，UTC+8 凌晨 0-8 点会串前一天，已修复） */
formatLocalDateKey(d)

/** API 扁平列表 → 52×7 网格，日期公式沿用原 mock：覆盖 今天-364 … 昨天（今天不入网格） */
buildHeatmapData(entries): Map<date,count> 反查 → HeatmapDay[][]，初始全零网格布局稳定
```

组件侧：`loadHeatmap()` 带 `currentUser` 守卫（与 loadUserStats/loadFavorites 一致），失败时拦截器已 toast、网格保持全零；tooltip 改走 i18n。

## 6. 测试设计

| 层 | 用例 |
|---|---|
| 后端 `ArticleStatsBizServiceImplTest`（4） | 游客空列表不触库 / 无 userId 空列表 / 正常透传 DAO / DAO 空透传；spy 匿名子类覆盖 `getUserId()`（父类 protected） |
| 前端 `heatmap.test.ts`（4） | 空数据 52×7 全零、最后一格=昨天；种子数据回填 intensity；toIntensity 全边界；formatLocalDateKey 时区 |
| 前端 `profile-tabs.test.ts`（3） | 挂载调接口 1 次 + 364 格；有数据时末格 title 正确 + vm 层 intensity=0.7（happy-dom 会丢弃含 var() 的内联样式，不能断言 DOM 颜色）；空数据 tooltip 为 0 次 |
| 前端 `profile-api.test.ts`（1） | 契约：URL = `/api/article-service/article/stats/heatmap/me`，method get |

## 7. 验证方式

1. 后端：`JAVA_HOME=<JDK21> mvn test -pl oy-blog-service/article-service`；手工 SQL：取真实 userId 跑 XML 聚合查询核对每日计数
2. 前端：`npm test`（勿与后端测试并行）；`vue-tsc -b` 零错误
3. 端到端：起网关 8080 + article-service 8091 + `npm run dev`，发文章/评论/点赞/收藏后用 SQL 回拨事件时间（如 `UPDATE article SET publish_at = NOW() - INTERVAL 3 DAY`）制造历史分布，刷新个人页核对颜色梯度、中英文 tooltip、主题切换、游客全零网格

## 8. 边界与已知取舍

1. **今天不入网格**：网格覆盖「今天-364 … 昨天」，今天的活跃明天才显示（沿用原 mock 布局，GitHub 式"只显示过去"）
2. **统计口径**：浏览（article_log 为 upsert 非事件流）与评论点赞（comment_reaction）明确排除
3. **intensity 固定阈值**：1→0.1、2-3→0.4、4-6→0.7、≥7→1，确定性强可单测；若实际分布极偏可换相对分位数
4. **时区**：`DATE()` 按 DB 服务器本地时间分组，窗口下界由 Java 侧 LocalDate 传入规避边界歧义；偏差最多影响窗口首日 ±1
5. **既有问题未动**：月份标签硬编码 Jan-Dec 与网格起点不对齐、图例 Less/More 硬编码英文
6. **扩展点**：DAO 已参数化 userId，将来做他人公开主页热力图只需新增 `GET /stats/heatmap/{userId}` controller 方法（可复用 stats/{userId} 先例）
