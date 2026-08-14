# ES 搜索高亮功能实现方案

## 概述

在搜索结果中展示 ES 返回的关键词高亮片段，替代原来的摘要（summary）展示命中的上下文内容（约 200 字），并对命中的关键词进行高亮标记。

## 数据流

```
用户输入 keyword
  → SearchPage.vue 发起搜索请求
    → EsSearchController.search()
      → SearchBizServiceImpl.searchArticles()
        → ElasticsearchClient.search() [带 highlight 配置]
          → ES 返回 hits + highlight fragments
            → 后端提取 highlight，封装为 ArticleSearchVO
              → 前端接收 highlightSnippet，用 v-html 渲染
```

## 改动范围

### 后端改动（search-service 模块）

#### 1. 新增 `ArticleSearchVO` — 搜索响应 VO
**文件**: `search-service/src/main/java/.../domain/vo/ArticleSearchVO.java`

```java
// 包装 ArticleDocument + highlightSnippet，不污染 ES 映射实体
@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleSearchVO extends ArticleDocument {
    private String highlightTitle;    // 高亮后的标题片段
    private String highlightSnippet;  // 命中的正文/摘要上下文（~200字）
}
```

#### 2. 修改 `SearchBizService` 接口
**文件**: `search-service/.../service/SearchBizService.java`

- 返回类型从 `Result<PageVo<List<ArticleDocument>>>` 改为 `Result<PageVo<List<ArticleSearchVO>>>`

#### 3. 修改 `SearchBizServiceImpl` 实现
**文件**: `search-service/.../service/impl/SearchBizServiceImpl.java`

- 在 ES 查询中增加 `.highlight()` 配置:
  - `title` 字段：返回高亮标题
  - `content` 字段：`fragment_size=200, number_of_fragments=1`（200 字上下文）
  - `summary` 字段：`fragment_size=200, number_of_fragments=1`
  - preTags=`<em class="highlight">`, postTags=`</em>`
- 从 `response.hits().hits()` 中提取 `highlight` map
- 组装 `ArticleSearchVO` 列表返回

#### 4. 修改 `EsSearchController`
**文件**: `search-service/.../controller/EsSearchController.java`

- 返回类型同步更新

#### 5. 修改 `ArticleDocument`
**文件**: `search-service/.../domain/entity/ArticleDocument.java`

- 在 `summary` 字段上添加 `analyzer = "ik_max_word", searchAnalyzer = "ik_smart"` 使 summary 支持分词高亮
- 增加 `coverUrl` 字段（当前前端映射了 coverUrl 但 ES 文档没有此字段）

### 前端改动

#### 6. 更新 `SearchPage.vue`
**文件**: `frontend/oy-blog-front/src/views/SearchPage.vue`

- `ArticleInfo` 接口增加 `highlightSnippet`、`highlightTitle` 字段
- `EnrichedArticle` 接口增加 `highlightSnippet`、`highlightTitle` 字段
- `enrichArticles()` 函数映射新字段
- 模板中传递 `highlightSnippet` 给卡片组件

#### 7. 更新 `ArticleCard.vue`
**文件**: `frontend/oy-blog-front/src/components/ArticleCard.vue`

- 新增 `highlightSnippet` prop（可选）
- 新增 `highlightTitle` prop（可选）
- 当 `highlightSnippet` 存在时，用 v-html 渲染（替代 summary 的纯文本），样式化 `<em class="highlight">` 标签
- 当 `highlightTitle` 存在时，用 v-html 渲染标题

#### 8. 更新 `article.ts` API 层
**文件**: `frontend/oy-blog-front/src/api/article.ts`

- `ArticleInfo` 接口增加可选字段

### ES 索引调整（无需重建）

summary 字段当前定义为 `FieldType.Text`（无分析器），需改为 `FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart"` 以支持分词高亮。由于 Spring Data Elasticsearch 默认不自动更新 mapping，**需要手动更新 mapping 或重建索引**。

```bash
# 手动更新 summary 字段 mapping（无需重建整个索引）
curl -X PUT "http://192.168.200.130:9200/articles/_mapping" -H 'Content-Type: application/json' -d '{
  "properties": {
    "summary": { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" }
  }
}'
```

## ES Highlight 配置细节

```java
Highlight highlight = Highlight.of(h -> h
    .fields("title", hf -> hf
        .preTags("<em class=\"highlight\">")
        .postTags("</em>")
        .numberOfFragments(0)  // 0 = 返回整个字段
    )
    .fields("content", hf -> hf
        .preTags("<em class=\"highlight\">")
        .postTags("</em>")
        .fragmentSize(200)
        .numberOfFragments(1)
    )
    .fields("summary", hf -> hf
        .preTags("<em class=\"highlight\">")
        .postTags("</em>")
        .fragmentSize(200)
        .numberOfFragments(1)
    )
);
```

## 高亮片段优先级

后端提取高亮逻辑：
1. 优先取 `content` highlight → 作为 `highlightSnippet`
2. 如 content 无命中，取 `summary` highlight → 作为 `highlightSnippet`
3. 如都无命中，`highlightSnippet` 为 null → 前端 fallback 到原始 summary

## 前端展示逻辑

```
ArticleCard:
  title: 如果 highlightTitle 存在 → v-html 渲染高亮标题
         否则 → 原样显示 title
  
  summary位置: 如果 highlightSnippet 存在 → v-html 渲染高亮片段（200字上下文）
              否则 → 显示原始 summary
```

## CSS 高亮样式

```css
:deep(em.highlight) {
  font-style: normal;
  background: linear-gradient(180deg, transparent 60%, rgba(255, 200, 0, 0.4) 60%);
  padding: 0 0.1em;
  font-weight: 600;
  color: var(--color-accent-primary);
}
```

## 测试计划

### 后端单元测试
- `SearchBizServiceImplTest` — Mock ElasticsearchClient，验证 highlight 配置正确、fragment 提取正确
- 测试边界：无命中、命中 title、命中 content、命中 summary、多关键词

### 前端测试（如项目有 vitest 配置）
- 验证 `enrichArticles` 正确映射 `highlightSnippet` 字段
- 验证 Card 在有/无 highlightSnippet 时渲染正确

---

## 实施步骤

| 步骤 | 内容 | 文件 |
|------|------|------|
| 1 | 创建 ArticleSearchVO | 新文件 |
| 2 | 修改 ArticleDocument.summary 添加分词器 | ArticleDocument.java |
| 3 | 修改 SearchBizServiceImpl 添加 highlight + 提取逻辑 | SearchBizServiceImpl.java |
| 4 | 修改 SearchBizService 接口返回类型 | SearchBizService.java |
| 5 | 修改 EsSearchController 返回类型 | EsSearchController.java |
| 6 | 更新前端 API 类型 | article.ts |
| 7 | 更新 ArticleCard 支持 highlight | ArticleCard.vue |
| 8 | 更新 SearchPage 传递 highlight 数据 | SearchPage.vue |
| 9 | 编写后端测试 | SearchBizServiceImplTest.java |
