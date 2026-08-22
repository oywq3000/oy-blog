# listPublished 首页文章"消失"bug 分析及修复方案

> 创建时间：2026-08-22
> 状态：**待处理**（方案已定，尚未实施）

## 一、问题现象

- 已发布文章较多时，点赞某篇文章或给某篇文章添加标签后，回首页（home）发现这篇文章看不见了
- `listPublished` 接口查询结果与预期"不一致"

## 二、根因分析

**核心结论：点赞/加标签本身没有改坏任何数据，它们只是"发现时机"，不是原因。真正的坑是查询截断 + 无排序。**

### 2.1 点赞/标签不修改 article 表（排除数据损坏）

| 操作 | 写哪张表 |
|---|---|
| 点赞 | `article_like`（INSERT 一行）+ `article_stats`（`UPDATE ... SET likes = GREATEST(likes+delta, 0)`） |
| 加标签 | `tag`（不存在则创建）+ `article_tag`（关联表 INSERT） |

全项目对 `article` 表的 UPDATE 仅 3 处，全部在 `ArticleBizServiceImpl`（发布/编辑/删除文章），与点赞、标签无关。

### 2.2 真正的 bug：隐式 LIMIT 10 + 无 ORDER BY

调用链与证据：

1. **后端偷偷只查 10 条**
   - `ArticleReadBizServiceImpl.listPublished()`（`oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleReadBizServiceImpl.java:92-98`）走 `getPage(...)`
   - `getPage` → `PageUtils.startPage()`（`.../utils/PageUtils.java:15-22`）→ `TableSupport.getPageDomain()`（`.../domain/vo/TableSupport.java:39-40`）
   - **PageHelper 默认 `pageNum=1, pageSize=10`** → 给查询隐式加 `LIMIT 10`

2. **查询没有任何 ORDER BY**
   - `ArticleDaoImpl.listPublished()`（`.../dto/impl/ArticleDaoImpl.java:62-66`）只有：
     ```java
     return baseMapper.selectList(new LambdaQueryWrapper<Article>()
             .eq(Article::getStatus, "published")
             .isNull(Article::getDeletedAt));   // 无排序
     ```
   - 文章主键是 snowflake 数字 ID（≈创建时间升序，见 `oy-blog-common/.../utils/UUIDUtils.java`）
   - MySQL 无排序查询按聚簇索引顺序扫描 → **返回的是"最老的 10 篇已发布文章"**，与"最新/置顶优先"意图无关

3. **前端拿到这 10 篇后再排序渲染**
   - `frontend/oy-blog-front/src/views/HomeView.vue:51-79`（前端是独立仓库 `G:\JavaWorkSpace\frontend\oy-blog-front`）
   - 在**前端内存**按 `publishAt || createdAt` 降序（置顶优先）排序后渲染
   - 看起来顺序是对的，但**内容永远是最老的 10 篇**；前端无分页/加载更多 UI

### 2.3 为什么"有时候"才出现

- 已发布文章数 ≤ 10 时：全部返回，一切正常
- 文章数 > 10 时：第 11 篇及之后的新文章**永远不会出现在首页**
- 用户对某篇不在返回集合里的文章点赞/加标签后回首页，发现它不在 → 误以为是操作导致的
- 所以"有时候"= 只有文章数超过 10 且互动对象恰好在被截掉的那批里时才暴露

## 三、修复方案（后端 2 处小改，前端无需改动）

### 3.1 `ArticleDaoImpl.listPublished()` 增加显式排序

文件：`oy-blog-service/article-service/src/main/java/com/oyproj/dto/impl/ArticleDaoImpl.java:62-66`

```java
public List<Article> listPublished() {
    return baseMapper.selectList(new LambdaQueryWrapper<Article>()
            .eq(Article::getStatus, "published")
            .isNull(Article::getDeletedAt)
            .orderByDesc(Article::getIsTop)
            .orderByDesc(Article::getPublishAt)
            .orderByDesc(Article::getCreatedAt)
            .orderByDesc(Article::getId));
}
```

### 3.2 `ArticleReadBizServiceImpl.listPublished()` 去掉 PageHelper 截断

文件：`oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleReadBizServiceImpl.java:92-98`

不再走 `getPage`（避免隐式 `LIMIT 10`），直接查全量，复用基类已有的 `copyList`（`oy-blog-common/.../service/base/BaseBiz.java:110`，public 方法）：

```java
@Override
@Transactional
public Result<List<ArticleInfoVo>> listPublished() {
    List<ArticleInfoVo> voList = copyList(articleDao.listPublished(), ArticleInfoVo.class);
    enrichWithStats(voList);
    enrichWithAuthorInfo(voList);
    enrichWithTags(voList);
    return Result.ok(voList);
}
```

enrich 三件套（`ArticleReadBizServiceImpl.java:142-211`）本来就是按 articleIds 批量注入，全量列表直接可用，无需改动。接口签名 `ArticleDao.listPublished()` 不变。

## 四、设计说明

- **为什么返回全部而不是"最新 10 条"**：前端首页没有分页/加载更多 UI，`HomeView.vue` 拿到列表后全量渲染。返回全部 + 显式排序与现有前端意图一致；只返回最新 10 条会把老文章永久挤出首页（仍是 bug）。个人博客量级下首页全量渲染可接受。
- **排序一致性**：后端排序规则（置顶 → 发布时间降序）与 `HomeView.vue:72-79` 前端排序一致，前端排序变成幂等兜底。
- **is_top 为 NULL 的注意点**：MySQL DESC 排序 NULL 排最前，若存在 `is_top IS NULL` 的行会排在置顶之前；前端 sort 已自行处理置顶（`a.isTop && !b.isTop`），最终展示顺序不受影响。若想后端也严格处理，可将 `orderByDesc(Article::getIsTop)` 换成 `.last("ISNULL(is_top) ASC, is_top DESC")`。
- **后续扩展**：若首页列表会无限变长，可另做"前端 load more + 后端分页"（现有 `listPublished(int page, int size)` 已存在，`ArticleDaoImpl.java:71-79`），本次不做。

## 五、涉及文件

| 文件 | 改动 |
|---|---|
| `oy-blog-service/article-service/src/main/java/com/oyproj/dto/impl/ArticleDaoImpl.java` | `listPublished()` 加 4 个 `orderByDesc` |
| `oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleReadBizServiceImpl.java` | `listPublished()` 改为 `copyList(articleDao.listPublished(), ...)`，不再走 `getPage` |
| `oy-blog-service/article-service/src/test/java/com/oyproj/service/impl/ArticleReadBizServiceImplTest.java` | 新增回归测试：stub `articleDao.listPublished()` 返回 10+ 篇乱序文章，断言 `listPublished()` 返回全部（不截断）且 enrich 正常；沿用现有 Mockito + spy 模式（构造器 8 个 mock 依赖按序注入） |

## 六、验证步骤

1. 编译：`mvn -pl oy-blog-service/article-service -am compile`（命令行编译注意 JDK 版本，需指定 jdk-21.0.8）
2. 单测：`mvn -pl oy-blog-service/article-service test -Dtest=ArticleReadBizServiceImplTest`
3. 端到端（dev/生产环境）：造 >10 篇已发布文章，请求 `GET /api/article-service/article/read/published`：
   - 返回**全部**已发布文章（>10 条），不再固定 10 条
   - 顺序：置顶在前，其余按 `publish_at` 降序
   - 点赞/加标签一篇文章后再请求，结果集不变（回归验证）
4. 前端：`cd G:\JavaWorkSpace\frontend\oy-blog-front && npm run dev`，首页可见全部文章、最新在前
