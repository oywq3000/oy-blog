# 文章 AI 审核 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给博客文章发布加 AI 审核门：AI 判 approve 直接发布、reject 直接驳回、manual 转人工审核队列，人工通过/驳回走 admin 接口。

**Architecture:** BlogAgent（Python，`G:\agentWorkplace\BlogAgent`）新增 `POST /moderate/article` 三态判定端点；article-service 在唯一的发布入口 `ArticleBizServiceImpl.publish` 里加审核门（同步调用，豁免角色可配置）；歧义的新文章进 `pending_review` 状态、已发布文章的歧义编辑进新表 `article_pending_content`；admin-service 新增待审队列 BFF，透传 Feign 到 article-service 的人工审核端点。审核日志复用 `moderation_log` 表。

**Tech Stack:** FastAPI + LangChain/DeepSeek（Python 侧）；Spring Boot 3.2 + MyBatis-Plus + RestClient + OpenFeign（Java 侧）；MySQL。

**Spec:** `docs/superpowers/specs/2026-08-29-article-ai-moderation-design.md`（本计划从 spec 论证，执行时与 spec 一起阅读）

## Global Constraints

- **两个仓库**：Task 1-2 在 `G:\agentWorkplace\BlogAgent`（Git Bash 路径 `/g/agentWorkplace/BlogAgent`）；Task 3-13 在 `G:\JavaWorkSpace\oy-blog-dev1`（本仓库，`/g/JavaWorkSpace/oy-blog-dev1`）。
- **Python 解释器**：`/d/tool1/anancoda/envs/ai-agent/python.exe`（本机 conda 环境 ai-agent）。
- **Python 测试**：`MOCK_LLM=1` 前缀运行：`cd /g/agentWorkplace/BlogAgent && MOCK_LLM=1 /d/tool1/anancoda/envs/ai-agent/python.exe -m pytest -v`。
- **Java 编译必须 JDK 21**：命令行 `mvn` 依赖 JAVA_HOME（默认是 JDK 20 会报"不支持发行版本 21"），统一前缀 `JAVA_HOME=/d/DevelopKit/jdk-21.0.8`。
- **Java 单测命令模板**（指定测试类过滤必须加 `-Dsurefire.failIfNoSpecifiedTests=false`，否则其他模块报 "No tests matching pattern"）：
  `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/article-service -am test -Dtest=ModerationServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
  （admin-service 任务把 `-pl oy-blog-service/admin-service` 换掉即可）
- **`src/test` 被 .gitignore 忽略**：Java 测试文件不提交 git，提交只含 `src/main` 与 `doc/`、`docs/` 文件。
- **Java 单测里 `Result.ok()` 依赖 I18nUtils 静态 messageSource**：无 Spring 容器时必须反射注入 `StaticMessageSource`（先例 `ArticleStatsBizServiceImplTest` 第 43-48 行），每个新测试类都要带这段 `@BeforeAll`。
- **SQL 先于代码部署**：`article_pending_content` 建表 SQL 必须先执行、再发布新代码（旧代码不受新表影响，两段式）。
- **状态字面量**（全局约定，各处代码保持一致）：
  - `article.status`：`draft` / `published` / `pending_review`（新增）/ `rejected`（新增）
  - `article.review_status`：`approved` / `rejected` / `manual` / `exempt`
  - `moderation_log.action`：`ai_approve` / `ai_reject` / `ai_manual` / `manual_approve` / `manual_reject`（评论审核的 `approve`/`reject` 不动）
- **审核协议**：`POST /moderate/article`，请求 `{"articleId","title","summary","content"}`，响应 `{"verdict":"approve|reject|manual","reason":"..."}`；Java 侧任何调用失败/未知响应一律按 `manual` 处理（fail-closed，绝不放行）。
- **豁免语义**：`oy-blog.article.moderation.exempt-roles` 里的角色（默认 `ADMIN`，匹配 `X-User-Type` 头的 `BlogRole.name()`）跳过 AI 审核直接放行，`review_status=exempt`。
- **代码风格**：中文注释、中文 Javadoc（与现有代码一致）；文件里"//"注释解释为什么，不解释什么。
- **先审后写原则**：已发布文章的编辑，AI 审核发生在写库之前；approve 才覆盖内容，reject 全部丢弃，manual 进待生效区。

---

## 文件结构总览

**BlogAgent（Python 仓库）**
- Create `app/moderation.py` — 审核提示词 + 判定函数（三态 + 解析容错 + MOCK 触发词）
- Modify `app/config.py` — 加 `moderation_content_max_chars`
- Modify `app/main.py` — 加 `POST /moderate/article`
- Create `tests/test_moderation.py`、`tests/test_moderation_endpoint.py`
- Modify `README.md` — env 表 + 端点表

**oy-blog（Java 仓库）**
- Create `doc/sql/article_moderation_migration.sql`
- Create `article-service/.../config/ModerationProperties.java`、`config/ModerationClientConfig.java`
- Create `article-service/.../service/ModerationVerdict.java`、`service/ModerationService.java`
- Create `article-service/.../service/ArticleIndexMessageService.java`（从 ArticleBizServiceImpl 抽出）
- Create `article-service/.../service/ArticleChapterService.java`（从 ArticleBizServiceImpl 抽出）
- Create `article-service/.../domain/entity/ArticlePendingContent.java`、`mapper/ArticlePendingContentMapper.java`
- Create `article-service/.../service/ModerationAdminBizService.java` + `impl/ModerationAdminBizServiceImpl.java`
- Create `article-service/.../controller/ModerationAdminController.java`
- Modify `article-service/.../service/impl/ArticleBizServiceImpl.java`（审核门）
- Modify `article-service/.../service/impl/ArticleReadBizServiceImpl.java`（可见性修补）
- Modify `article-service/.../domain/vo/ArticleInfoVo.java`（加审核字段）
- Modify `article-service/.../src/main/resources/application.yml`（配置块）
- Create `service-api/.../article/domain/dto/ArticleModerationPageDto.java`、`ArticleModerationAuditDto.java`、`vo/ArticleModerationItemVo.java`
- Create `service-api/.../article/client/AdminModerationClient.java` + `fallback/AdminModerationClientFallbackFactory.java`
- Create `admin-service/.../service/AdminModerationBizService.java` + `impl/...`、`controller/AdminModerationController.java`

---

## Task 1: BlogAgent 审核模块（app/moderation.py + 单测）

**Files:**
- Create: `/g/agentWorkplace/BlogAgent/app/moderation.py`
- Modify: `/g/agentWorkplace/BlogAgent/app/config.py`（在 `article_content_max_chars` 后加一行 `moderation_content_max_chars: int = 8000`）
- Test: `/g/agentWorkplace/BlogAgent/tests/test_moderation.py`

**Interfaces:**
- Produces: `moderate_content(article_id: str, title: str, summary: str, content: str, settings: Settings) -> tuple[str, str]`（Task 2 端点调用）
- Produces: `MODERATION_PROMPT` 常量（六大类规则清单）

- [ ] **Step 1: 写失败测试** `tests/test_moderation.py`

```python
# -*- coding: utf-8 -*-
"""moderation 模块单测：解析容错 + 截断 + MOCK 触发词三态。"""
from app.config import Settings
from app.moderation import _parse_verdict, _truncate, moderate_content


def _settings(mock: bool = True) -> Settings:
    return Settings(mock_llm=mock, moderation_content_max_chars=100)


def test_parse_normal():
    verdict, reason = _parse_verdict('{"verdict":"reject","reason":"含有政治敏感内容"}')
    assert verdict == "reject"
    assert reason == "含有政治敏感内容"


def test_parse_with_extra_text():
    # 模型有时会在 JSON 外多输出说明文字，容错提取第一个 {...}
    verdict, _ = _parse_verdict('判定结果：{"verdict":"approve","reason":"正常"}\n')
    assert verdict == "approve"


def test_parse_bad_json():
    verdict, reason = _parse_verdict("这不是JSON")
    assert verdict == "manual"
    assert "异常" in reason


def test_parse_empty():
    verdict, _ = _parse_verdict("")
    assert verdict == "manual"


def test_parse_unknown_verdict():
    verdict, _ = _parse_verdict('{"verdict":"maybe","reason":"x"}')
    assert verdict == "manual"


def test_truncate():
    assert _truncate("1234567890", 5) == "12345"
    assert _truncate("abc", 5) == "abc"


def test_mock_reject():
    verdict, reason = moderate_content("a1", "标题", "", "这是一篇违规文章", _settings())
    assert verdict == "reject"
    assert "违规" in reason


def test_mock_manual():
    verdict, _ = moderate_content("a1", "标题", "", "内容有些歧义", _settings())
    assert verdict == "manual"


def test_mock_approve():
    verdict, _ = moderate_content("a1", "标题", "", "正常的技术文章", _settings())
    assert verdict == "approve"


def test_truncation_applied_before_mock_judge():
    # 触发词在截断边界之外 → 不命中，防误判也验证截断确实生效
    long_text = "x" * 200 + "违规"
    verdict, _ = moderate_content("a1", "t", "", long_text, _settings())
    assert verdict == "approve"
```

- [ ] **Step 2: 运行确认失败**

Run: `cd /g/agentWorkplace/BlogAgent && MOCK_LLM=1 /d/tool1/anancoda/envs/ai-agent/python.exe -m pytest tests/test_moderation.py -v`
Expected: 全部 FAIL（`ModuleNotFoundError: app.moderation`）

- [ ] **Step 3: 实现 `app/moderation.py`**

```python
# -*- coding: utf-8 -*-
"""文章 AI 审核：三态判定（approve/reject/manual），供 POST /moderate/article 使用。

调用方：Java article-service 发布文章时同步调用，判定结果直接决定文章状态。
健壮性约定：模型输出解析失败一律回退 manual（转人工），绝不放行也绝不误杀。
"""
import json
import re

from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_deepseek import ChatDeepSeek

from app.config import Settings

MODERATION_PROMPT = """你是博客文章审核员。根据以下规则审核文章，判断是否违规。

违规规则（命中任意一条判 reject）：
1. 违法内容：宣扬违法犯罪、赌博、毒品、枪支等违法活动
2. 政治敏感：攻击国家制度、领导人，传播政治谣言，破坏社会稳定
3. 色情低俗：色情描写、性暗示、低俗挑逗内容
4. 广告引流：纯广告软文、推广联系方式、诱导付费或加群
5. 人身攻击：辱骂、威胁、诽谤、歧视他人
6. 垃圾内容：无意义灌水、乱码、明显测试文本

判定标准：
- 明确命中违规规则 → verdict=reject
- 完全正常、不涉及任何违规规则 → verdict=approve
- 无法确定是否违规（有歧义）→ verdict=manual

只输出一行 JSON，不要输出任何其他内容：
{"verdict":"approve|reject|manual","reason":"简短说明，不超过100字"}"""


def _build_model(settings: Settings) -> BaseChatModel:
    """审核专用模型：默认模型 + 非流式 + 60s 超时。

    不复用 llm.py 的 build_chat_model——那是聊天/思考流专用（streaming=True），
    审核只需一次完整 invoke。
    """
    return ChatDeepSeek(
        model=settings.model_default,
        api_key=settings.deepseek_api_key,
        base_url=settings.deepseek_base_url,
        streaming=False,
        timeout=60,
    )


def _parse_verdict(text: str) -> tuple[str, str]:
    """模型输出 -> (verdict, reason)。解析失败一律回退 manual。"""
    if not text:
        return "manual", "AI 未返回结果"
    match = re.search(r"\{[^{}]*\}", text, re.S)
    if not match:
        return "manual", "AI 返回格式异常"
    try:
        data = json.loads(match.group(0))
    except json.JSONDecodeError:
        return "manual", "AI 返回格式异常"
    verdict = str(data.get("verdict", "")).strip().lower()
    if verdict not in ("approve", "reject", "manual"):
        return "manual", "AI 判定结果未知"
    reason = str(data.get("reason") or "").strip()[:500]
    return verdict, reason


def _truncate(text: str, limit: int) -> str:
    return text if len(text) <= limit else text[:limit]


def moderate_content(
    article_id: str, title: str, summary: str, content: str, settings: Settings
) -> tuple[str, str]:
    """审核文章 -> (verdict, reason)。

    MOCK_LLM=1 联调模式：正文含"违规"→reject、含"歧义"→manual、否则 approve，
    无 API key 也能全链路验证三种结果。
    """
    full_text = _truncate(f"{title}\n{summary}\n{content}", settings.moderation_content_max_chars)
    if settings.mock_llm:
        if "违规" in full_text:
            return "reject", "【MOCK】命中触发词：违规"
        if "歧义" in full_text:
            return "manual", "【MOCK】命中触发词：歧义"
        return "approve", "【MOCK】联调放行"
    model = _build_model(settings)
    resp = model.invoke(
        [SystemMessage(content=MODERATION_PROMPT), HumanMessage(content=full_text)]
    )
    return _parse_verdict(str(resp.content))
```

Modify `app/config.py`，在 `article_content_max_chars: int = 4000` 之后加：

```python
    moderation_content_max_chars: int = 8000
```

- [ ] **Step 4: 运行确认通过**

Run: `cd /g/agentWorkplace/BlogAgent && MOCK_LLM=1 /d/tool1/anancoda/envs/ai-agent/python.exe -m pytest tests/test_moderation.py -v`
Expected: 11 个测试全 PASS

- [ ] **Step 5: 提交（BlogAgent 仓库）**

```bash
cd /g/agentWorkplace/BlogAgent && git add app/moderation.py app/config.py tests/test_moderation.py && git commit -m "feat: 文章审核模块（三态判定+解析容错+MOCK触发词）"
```

---

## Task 2: BlogAgent /moderate/article 端点 + README

**Files:**
- Modify: `/g/agentWorkplace/BlogAgent/app/main.py`
- Modify: `/g/agentWorkplace/BlogAgent/README.md`（env 表加 `MODERATION_CONTENT_MAX_CHARS` 行、端点表加审核端点）
- Test: `/g/agentWorkplace/BlogAgent/tests/test_moderation_endpoint.py`

**Interfaces:**
- Consumes: Task 1 的 `moderate_content`
- Produces: HTTP `POST /moderate/article`（Java 侧 Task 5 依赖此协议；请求缺 `articleId`/`title`/`content` 任一 → 422 `{"code":422,"message":"参数不完整"}`）

- [ ] **Step 1: 写失败测试** `tests/test_moderation_endpoint.py`

```python
# -*- coding: utf-8 -*-
"""审核端点处理器单测（直接调函数，不起 HTTP 服务；依赖 MOCK_LLM=1 环境）。"""
from app.main import moderate_article


def test_missing_params_422():
    resp = moderate_article({"articleId": "a1", "title": "", "content": "正文"})
    assert resp.status_code == 422


def test_missing_article_id_422():
    resp = moderate_article({"articleId": "", "title": "标题", "content": "正文"})
    assert resp.status_code == 422


def test_mock_approve():
    resp = moderate_article({"articleId": "a1", "title": "标题", "summary": "", "content": "正常内容"})
    assert resp == {"verdict": "approve", "reason": "【MOCK】联调放行"}


def test_mock_reject():
    resp = moderate_article({"articleId": "a1", "title": "标题", "summary": "", "content": "违规内容"})
    assert resp == {"verdict": "reject", "reason": "【MOCK】命中触发词：违规"}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd /g/agentWorkplace/BlogAgent && MOCK_LLM=1 /d/tool1/anancoda/envs/ai-agent/python.exe -m pytest tests/test_moderation_endpoint.py -v`
Expected: FAIL（`ImportError: cannot import name 'moderate_article'`）

- [ ] **Step 3: 实现端点** `app/main.py`

在 import 区加：

```python
from fastapi.responses import JSONResponse

from app.moderation import moderate_content
```

在 `/chat/stop` 端点之后追加（注意用普通 `def` 不用 `async def`：审核是同步 LLM invoke，FastAPI 会把 `def` 路由放进线程池，不阻塞事件循环）：

```python
@app.post("/moderate/article")
def moderate_article(body: dict):
    """文章 AI 审核（Java 同步调用）。

    请求：{"articleId","title","summary","content"}（content=Markdown 纯文本）
    响应：{"verdict":"approve|reject|manual","reason":"..."}
    参数缺失 → 422 {"code":422,"message":"参数不完整"}
    """
    article_id = str(body.get("articleId") or "").strip()
    title = str(body.get("title") or "").strip()
    summary = str(body.get("summary") or "")
    content = str(body.get("content") or "")
    if not article_id or not title or not content:
        return JSONResponse(status_code=422, content={"code": 422, "message": "参数不完整"})
    verdict, reason = moderate_content(article_id, title, summary, content, settings)
    return {"verdict": verdict, "reason": reason}
```

README.md 更新：§3 环境变量表加一行 `| MODERATION_CONTENT_MAX_CHARS | 审核文本截断长度（控制审核成本），默认 8000 |`；§5 端点表加一行 `| POST /moderate/article | {articleId,title,summary,content} | {"verdict":"approve|reject|manual","reason":"..."}，参数缺失 422 |`。

- [ ] **Step 4: 运行确认通过**

Run: `cd /g/agentWorkplace/BlogAgent && MOCK_LLM=1 /d/tool1/anancoda/envs/ai-agent/python.exe -m pytest -v`
Expected: 全量通过（原 50 个 + 新增 15 个 = 65 个）

- [ ] **Step 5: 提交（BlogAgent 仓库）**

```bash
cd /g/agentWorkplace/BlogAgent && git add app/main.py tests/test_moderation_endpoint.py README.md && git commit -m "feat: 新增 POST /moderate/article 审核端点（含 422 校验）"
```

---

## Task 3: 数据库迁移 SQL（oy-blog 仓库）

**Files:**
- Create: `/g/JavaWorkSpace/oy-blog-dev1/doc/sql/article_moderation_migration.sql`

**Interfaces:**
- Produces: 表 `article_pending_content`（Task 8/10 的 `ArticlePendingContentMapper` 依赖；先执行 SQL 再发布代码）

- [ ] **Step 1: 写迁移文件**

```sql
-- 文章 AI 审核迁移：待生效编辑区
-- 部署顺序：先执行本 SQL，再发布新代码（旧代码不受新增表影响，两段式）
-- 用途：已发布文章的编辑被 AI 判"有歧义"时，新版本暂存于此等待人工审核；
--       人工通过 → 替换进 article/article_content；人工驳回 → 整行删除。
CREATE TABLE IF NOT EXISTS `article_pending_content` (
  `article_id`           VARCHAR(64)  NOT NULL COMMENT '文章ID（复用 article.id，一篇最多一份待审编辑）',
  `pending_title`        VARCHAR(255) NOT NULL COMMENT '待生效标题',
  `pending_summary`      VARCHAR(500) DEFAULT NULL COMMENT '待生效摘要',
  `pending_content_md`   LONGTEXT COMMENT '待生效 Markdown 正文',
  `pending_content_html` LONGTEXT COMMENT '待生效 HTML 正文',
  `review_reason`        VARCHAR(500) DEFAULT NULL COMMENT 'AI 转人工理由',
  `created_at`           DATETIME NOT NULL COMMENT '创建时间',
  `updated_at`           DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章待生效编辑（已发布文章编辑被判歧义时暂存）';
```

- [ ] **Step 2: 在开发库执行 SQL 并记录**

执行：通过 db_opt 技能或 Navicat 在开发库（dev）执行上述 SQL；执行后 `SHOW CREATE TABLE article_pending_content` 确认列齐全。
若 dev 库不可达（历史出现过），本步挂起并在执行记录里注明，后续任务用真实库验收。

- [ ] **Step 3: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add doc/sql/article_moderation_migration.sql && git commit -m "feat: 文章审核迁移 SQL（article_pending_content 待生效编辑区）"
```

---

## Task 4: Java 审核配置 + RestClient Bean

**Files:**
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/config/ModerationProperties.java`
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/config/ModerationClientConfig.java`
- Modify: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/resources/application.yml`（`oy-blog.article.hot-weight` 块之后加 `moderation` 块）
- Test: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/test/java/com/oyproj/config/ModerationPropertiesTest.java`（测试不入库）

**Interfaces:**
- Produces: Spring Bean `RestClient moderationRestClient`（Task 5 注入）；`ModerationProperties`（`isEnabled()/getExemptRoles()/getBaseUrl()/getTimeoutMs()`）

- [ ] **Step 1: 写失败测试** `ModerationPropertiesTest.java`

```java
package com.oyproj.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审核配置默认值测试
 */
class ModerationPropertiesTest {

    @Test
    void defaultsAreSafe() {
        ModerationProperties p = new ModerationProperties();
        assertTrue(p.isEnabled()); // 默认开启审核门
        assertEquals(List.of("ADMIN"), p.getExemptRoles()); // 默认只豁免管理员
        assertEquals("http://localhost:8001", p.getBaseUrl());
        assertEquals(30000, p.getTimeoutMs());
    }
}
```

- [ ] **Step 2: 运行确认失败（编译错误）**

Run: `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/article-service -am test -Dtest=ModerationPropertiesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（类不存在）
> 注意：若编译失败源于 src/test 下遗留的手工脚本（testForCreateArticle 等），这些文件在 .gitignore 里不属于 git，直接删除它们再跑（它们是一次性脚本）。

- [ ] **Step 3: 实现两个类**

```java
package com.oyproj.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文章 AI 审核配置（application.yml 的 oy-blog.article.moderation 节点）
 */
@Component
@Data
@ConfigurationProperties(prefix = "oy-blog.article.moderation")
public class ModerationProperties {
    /** 审核总开关，false = 全放行（等于关闭审核门） */
    private boolean enabled = true;
    /** 豁免角色列表（BlogRole.name()，如 ADMIN），命中则跳过 AI 审核 */
    private List<String> exemptRoles = new ArrayList<>(List.of("ADMIN"));
    /** BlogAgent 审核端点地址 */
    private String baseUrl = "http://localhost:8001";
    /** 审核调用超时（毫秒），连接和读取共用 */
    private int timeoutMs = 30000;
}
```

```java
package com.oyproj.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 审核 HTTP 客户端配置：article-service 首次出站 HTTP（同步 JSON 调 BlogAgent）。
 * 用 Spring 6.1 自带的 RestClient（spring-boot-starter-web 已含），无需新增依赖。
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ModerationProperties.class)
public class ModerationClientConfig {

    private final ModerationProperties moderationProperties;

    @Bean
    public RestClient moderationRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(moderationProperties.getTimeoutMs());
        factory.setReadTimeout(moderationProperties.getTimeoutMs());
        return RestClient.builder()
                .baseUrl(moderationProperties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
```

application.yml：在 `oy-blog.article.hot-weight` 块之后加：

```yaml
    # ============ 文章 AI 审核配置 ============
    moderation:
      enabled: true                 # 审核总开关
      exempt-roles: [ADMIN]         # 豁免角色（BlogRole.name()）
      base-url: ${AGENT_PYTHON_URL:http://localhost:8001}  # BlogAgent 审核端点
      timeout-ms: 30000
```

- [ ] **Step 4: 运行确认通过**

Run: 同 Step 2 命令
Expected: `ModerationPropertiesTest` PASS

- [ ] **Step 5: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add oy-blog-service/article-service/src/main/java/com/oyproj/config/ModerationProperties.java oy-blog-service/article-service/src/main/java/com/oyproj/config/ModerationClientConfig.java oy-blog-service/article-service/src/main/resources/application.yml && git commit -m "feat: 文章审核配置（oy-blog.article.moderation）+ RestClient Bean"
```

---

## Task 5: ModerationService（豁免判定 + 调用 + 日志）

**Files:**
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/service/ModerationVerdict.java`
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/service/ModerationService.java`
- Test: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/test/java/com/oyproj/service/ModerationServiceTest.java`

**Interfaces:**
- Consumes: Task 4 的 `RestClient moderationRestClient`、`ModerationProperties`；`ModerationLogMapper`（已有）
- Produces（Task 8/10 依赖）:
  - `ModerationVerdict` record：`verdict()` 取值 `approved/rejected/manual`，`reason()`；静态工厂 `approved(reason)/rejected(reason)/manual(reason)`；判断方法 `isApproved()/isRejected()/isManual()`
  - `ModerationService.isEnabled() -> boolean`
  - `ModerationService.isExempt() -> boolean`（读请求头 X-User-Type 与配置比对，读不到角色 → false）
  - `ModerationService.moderate(articleId, title, summary, contentMd) -> ModerationVerdict`（HTTP 异常/未知响应 → manual）
  - `ModerationService.writeLog(articleId, action, reason, operatorId)`

- [ ] **Step 1: 写失败测试** `ModerationServiceTest.java`

```java
package com.oyproj.service;

import com.oyproj.config.ModerationProperties;
import com.oyproj.domain.entity.ModerationLog;
import com.oyproj.mapper.ModerationLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 审核服务测试：三态映射、fail-closed、豁免判定、日志写入
 */
@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    private static final String BASE = "http://localhost:8001";

    private RestClient restClient;
    private MockRestServiceServer server;
    @Mock private ModerationLogMapper logMapper;
    private ModerationService service;

    @BeforeEach
    void setUp() {
        ModerationProperties props = new ModerationProperties();
        props.setBaseUrl(BASE);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        service = new ModerationService(restClient, props, logMapper);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void mockUser(String userType) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userType != null) {
            request.addHeader("X-User-Type", userType);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void shouldMapApproveVerdict() {
        server.expect(requestTo(BASE + "/moderate/article"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"verdict\":\"approve\",\"reason\":\"内容正常\"}", MediaType.APPLICATION_JSON));

        ModerationVerdict v = service.moderate("a1", "标题", "", "正文");

        assertTrue(v.isApproved());
        assertEquals("内容正常", v.reason());
    }

    @Test
    void shouldMapRejectVerdict() {
        server.expect(requestTo(BASE + "/moderate/article"))
                .andRespond(withSuccess("{\"verdict\":\"reject\",\"reason\":\"广告引流\"}", MediaType.APPLICATION_JSON));

        ModerationVerdict v = service.moderate("a1", "标题", "", "正文");

        assertTrue(v.isRejected());
        assertEquals("广告引流", v.reason());
    }

    @Test
    void shouldMapManualVerdict() {
        server.expect(requestTo(BASE + "/moderate/article"))
                .andRespond(withSuccess("{\"verdict\":\"manual\",\"reason\":\"有歧义\"}", MediaType.APPLICATION_JSON));

        ModerationVerdict v = service.moderate("a1", "标题", "", "正文");

        assertTrue(v.isManual());
    }

    @Test
    void unknownVerdictFallsBackToManual() {
        server.expect(requestTo(BASE + "/moderate/article"))
                .andRespond(withSuccess("{\"verdict\":\"maybe\",\"reason\":\"x\"}", MediaType.APPLICATION_JSON));

        ModerationVerdict v = service.moderate("a1", "标题", "", "正文");

        assertTrue(v.isManual());
    }

    @Test
    void serverErrorFallsBackToManual() {
        server.expect(requestTo(BASE + "/moderate/article"))
                .andRespond(withServerError());

        ModerationVerdict v = service.moderate("a1", "标题", "", "正文");

        assertTrue(v.isManual());
        assertTrue(v.reason().contains("不可用"));
    }

    @Test
    void connectionRefusedFallsBackToManual() {
        // 不注册任何期望：请求发向真实 localhost:8001，无人监听 → 连接异常 → manual
        ModerationVerdict v = service.moderate("a1", "标题", "", "正文");
        assertTrue(v.isManual());
    }

    @Test
    void adminIsExempt() {
        mockUser("ADMIN");
        assertTrue(service.isExempt());
    }

    @Test
    void readerIsNotExempt() {
        mockUser("READER");
        assertFalse(service.isExempt());
    }

    @Test
    void missingUserTypeIsNotExempt() {
        mockUser(null);
        assertFalse(service.isExempt()); // 读不到角色 → 保守方向：不豁免
    }

    @Test
    void shouldWriteModerationLog() {
        service.writeLog("a1", "ai_approve", "内容正常", "ai");

        ArgumentCaptor<ModerationLog> captor = ArgumentCaptor.forClass(ModerationLog.class);
        verify(logMapper).insert(captor.capture());
        ModerationLog log = captor.getValue();
        assertEquals("a1", log.getArticleId());
        assertEquals("ai_approve", log.getAction());
        assertEquals("内容正常", log.getReason());
        assertEquals("ai", log.getOperatorId());
        assertNotNull(log.getActedAt());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/article-service -am test -Dtest=ModerationServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（类不存在）
> `connectionRefusedFallsBackToManual` 依赖本机 8001 端口无服务：若本机恰好有服务在 8001（历史上 run.py 实例常驻），该测试会得到 200 但 body 不是 JSON → `retrieve().body(Map.class)` 仍抛异常 → 还是 manual，测试依旧通过。两条路都收敛到 manual，测试稳定。

- [ ] **Step 3: 实现**

```java
package com.oyproj.service;

/**
 * 审核结论（review_status 三态 + exempt 由调用方直接处理）
 *
 * @param verdict approved=放行 / rejected=驳回 / manual=转人工
 * @param reason  结论理由（给作者和管理员看的）
 */
public record ModerationVerdict(String verdict, String reason) {

    public static ModerationVerdict approved(String reason) {
        return new ModerationVerdict("approved", reason);
    }

    public static ModerationVerdict rejected(String reason) {
        return new ModerationVerdict("rejected", reason);
    }

    public static ModerationVerdict manual(String reason) {
        return new ModerationVerdict("manual", reason);
    }

    public boolean isApproved() {
        return "approved".equals(verdict);
    }

    public boolean isRejected() {
        return "rejected".equals(verdict);
    }

    public boolean isManual() {
        return "manual".equals(verdict);
    }
}
```

```java
package com.oyproj.service;

import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.constant.HeaderConstant;
import com.oyproj.config.ModerationProperties;
import com.oyproj.domain.entity.ModerationLog;
import com.oyproj.mapper.ModerationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 文章 AI 审核服务：豁免判定、调用 BlogAgent 审核端点、写审核日志。
 * 铁律：任何调用异常一律回退 manual（转人工），绝不放行——AI 挂了不等于审核门洞开。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationService extends ArticleBaseBizService {

    private final RestClient moderationRestClient;
    private final ModerationProperties properties;
    private final ModerationLogMapper moderationLogMapper;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** 当前用户是否豁免（读网关注入的 X-User-Type，如 READER/ADMIN/GUEST） */
    public boolean isExempt() {
        String userType = getCurrentUserType();
        if (!StringUtils.hasText(userType)) {
            return false; // 读不到角色 → 保守方向：不豁免
        }
        return properties.getExemptRoles().stream()
                .anyMatch(role -> role.equalsIgnoreCase(userType));
    }

    /** 读请求头 X-User-Type（网关 AuthenticationFilter 注入；管理端经 AdminFeignConfig 透传） */
    public String getCurrentUserType() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        return request.getHeader(HeaderConstant.USER_TYPE.getValue());
    }

    /** 调用 BlogAgent 审核。HTTP 失败/超时/响应异常 → manual（fail-closed）。 */
    public ModerationVerdict moderate(String articleId, String title, String summary, String contentMd) {
        Map<String, String> body = new HashMap<>();
        body.put("articleId", articleId);
        body.put("title", title);
        body.put("summary", summary == null ? "" : summary);
        body.put("content", contentMd);
        try {
            Map<?, ?> resp = moderationRestClient.post()
                    .uri("/moderate/article")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            String verdict = resp == null ? null : String.valueOf(resp.get("verdict"));
            String reason = resp == null ? "" : String.valueOf(resp.get("reason"));
            if ("reject".equals(verdict)) {
                return ModerationVerdict.rejected(reason);
            }
            if ("approve".equals(verdict)) {
                return ModerationVerdict.approved(reason);
            }
            return ModerationVerdict.manual(reason); // manual 或未知值 → 转人工
        } catch (Exception e) {
            log.warn("文章 AI 审核调用失败, articleId: {}, 错误: {}", articleId, e.getMessage());
            return ModerationVerdict.manual("审核服务不可用，转人工审核");
        }
    }

    /** 写审核日志（operatorId："ai" 表示 AI 判定，人工审核时为管理员 ID） */
    public void writeLog(String articleId, String action, String reason, String operatorId) {
        ModerationLog logEntity = new ModerationLog();
        logEntity.setId(getId());
        logEntity.setArticleId(articleId);
        logEntity.setAction(action);
        logEntity.setReason(reason);
        logEntity.setOperatorId(operatorId);
        logEntity.setActedAt(LocalDateTime.now());
        moderationLogMapper.insert(logEntity);
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: 同 Step 2 命令
Expected: 11 个测试全 PASS

- [ ] **Step 5: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add oy-blog-service/article-service/src/main/java/com/oyproj/service/ModerationVerdict.java oy-blog-service/article-service/src/main/java/com/oyproj/service/ModerationService.java && git commit -m "feat: 审核服务（豁免判定+调BlogAgent+fail-closed+审核日志）"
```

---

## Task 6: 抽取 ArticleIndexMessageService（纯重构，行为不变）

**Files:**
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/service/ArticleIndexMessageService.java`
- Modify: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleBizServiceImpl.java`（删除 `buildArticleIndexMessage` 与 `sendMessageAfterCommit`，改为调用新组件；`delete` 方法不动）
- Test: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/test/java/com/oyproj/service/ArticleIndexMessageServiceTest.java`

**Interfaces:**
- Consumes: `ArticleContentDao`、`ArticleStatsDao`、`ArticleMessageProducer`、`UserClient`（均为已有）
- Produces（Task 8/10 依赖）: `ArticleIndexMessageService.sendIndexAfterCommit(Article article, List<String> tags, MQOperation operation)`、`buildIndexMessage(Article article, List<String> tags, MQOperation operation) -> ArticleIndexMessage`

- [ ] **Step 1: 写失败测试**（特征测试：先把现有行为钉住）

```java
package com.oyproj.service;

import com.oyproj.api.user.client.UserClient;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.dao.UserArticleStatDao;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleContent;
import com.oyproj.domain.entity.ArticleStats;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.dto.ArticleStatsDao;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 索引消息构建测试：从 ArticleBizServiceImpl 抽出的逻辑，行为必须与抽取前一致
 */
@ExtendWith(MockitoExtension.class)
class ArticleIndexMessageServiceTest {

    @Mock private ArticleContentDao contentDao;
    @Mock private ArticleStatsDao statsDao;
    @Mock private ArticleMessageProducer producer;
    @Mock private UserClient userClient;
    @Mock private UserArticleStatDao userArticleStatDao;

    @InjectMocks private ArticleIndexMessageService service;

    @BeforeAll
    static void initI18n() throws Exception {
        Field field = com.oyproj.common.utils.I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, (MessageSource) new StaticMessageSource());
    }

    private Article article() {
        Article a = new Article();
        a.setId("a1");
        a.setSlug("a1");
        a.setTitle("标题");
        a.setSummary("摘要");
        a.setAuthorId("u1");
        a.setStatus("published");
        a.setCreatedAt(LocalDateTime.now());
        a.setPublishAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        return a;
    }

    @Test
    void shouldSetAuthorNameFromUserClient() {
        when(userClient.getUserDTO("u1")).thenReturn(Result.ok(new UserDTO()));
        when(userClient.getUserDTO("u1").getData()).thenReturn(new UserDTO());
        UserDTO dto = new UserDTO();
        dto.setUsername("张三");
        dto.setAvatarUrl("http://a.jpg");
        when(userClient.getUserDTO("u1")).thenReturn(Result.ok(dto));
        when(contentDao.getById("a1")).thenReturn(null);
        when(statsDao.getById("a1")).thenReturn(null);

        ArticleIndexMessage msg = service.buildIndexMessage(article(), List.of("Java"), MQOperation.CREATE);

        assertEquals("张三", msg.getAuthorName());
        assertEquals("http://a.jpg", msg.getAuthorAvatar());
        assertEquals("Java", msg.getTags().get(0));
        assertEquals(MQOperation.CREATE, msg.getOperation());
    }

    @Test
    void authorFallbackToAuthorIdWhenUserClientFails() {
        when(userClient.getUserDTO("u1")).thenThrow(new RuntimeException("服务不可用"));
        when(contentDao.getById("a1")).thenReturn(null);
        when(statsDao.getById("a1")).thenReturn(null);

        ArticleIndexMessage msg = service.buildIndexMessage(article(), List.of(), MQOperation.UPDATE);

        assertEquals("u1", msg.getAuthorName()); // 兜底：用 authorId
        assertNull(msg.getAuthorAvatar());
    }

    @Test
    void shouldFillContentAndStats() {
        when(userClient.getUserDTO("u1")).thenReturn(Result.ok(new UserDTO()));
        ArticleContent content = new ArticleContent();
        content.setContentMd("**加粗** # 标题");
        when(contentDao.getById("a1")).thenReturn(content);
        ArticleStats stats = new ArticleStats();
        stats.setViews(10L);
        stats.setLikes(2L);
        stats.setComments(3L);
        when(statsDao.getById("a1")).thenReturn(stats);

        ArticleIndexMessage msg = service.buildIndexMessage(article(), List.of(), MQOperation.CREATE);

        assertEquals("加粗 标题", msg.getContentMd().trim()); // MarkdownSanitizer 清洗后
        assertEquals(10L, msg.getViewCount());
        assertEquals(2L, msg.getLikeCount());
        assertEquals(3L, msg.getCommentCount());
    }
}
```

注意：`shouldSetAuthorNameFromUserClient` 里对 `userClient.getUserDTO` 的 stub 有冗余行（前两行是探索性写法，Mockito 会以最后一次 stub 为准），保留最后一次 `when(userClient.getUserDTO("u1")).thenReturn(Result.ok(dto))` 并删除前两行：

```java
    @Test
    void shouldSetAuthorNameFromUserClient() {
        UserDTO dto = new UserDTO();
        dto.setUsername("张三");
        dto.setAvatarUrl("http://a.jpg");
        when(userClient.getUserDTO("u1")).thenReturn(Result.ok(dto));
        when(contentDao.getById("a1")).thenReturn(null);
        when(statsDao.getById("a1")).thenReturn(null);

        ArticleIndexMessage msg = service.buildIndexMessage(article(), List.of("Java"), MQOperation.CREATE);

        assertEquals("张三", msg.getAuthorName());
        assertEquals("http://a.jpg", msg.getAuthorAvatar());
        assertEquals("Java", msg.getTags().get(0));
        assertEquals(MQOperation.CREATE, msg.getOperation());
    }
```

> `Result.ok(dto)` 内部走 I18nUtils（已在 @BeforeAll 注入）。`MarkdownSanitizer.sanitize` 的精确输出以实际执行为准，若断言不符，先打印实际值再修正断言（特征测试允许跟随现状，但不能静默跳过）。

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/article-service -am test -Dtest=ArticleIndexMessageServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（类不存在）

- [ ] **Step 3: 抽取实现**

新文件 `ArticleIndexMessageService.java`：

```java
package com.oyproj.service;

import com.oyproj.api.user.client.UserClient;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.common.util.MarkdownSanitizer;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleContent;
import com.oyproj.domain.entity.ArticleStats;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.dto.ArticleStatsDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 文章 ES 索引消息构建与发送（原 ArticleBizServiceImpl 私有方法原样迁出）。
 * 迁出原因：人工审核通过（发布旁路）也要发索引消息，两处共用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleIndexMessageService {

    private final ArticleContentDao contentDao;
    private final ArticleStatsDao statsDao;
    private final ArticleMessageProducer articleMessageProducer;
    private final UserClient userClient;

    /** 在事务提交后发送索引消息（afterCommit：消费方读到的数据已提交） */
    public void sendIndexAfterCommit(Article article, List<String> tags, MQOperation operation) {
        ArticleIndexMessage message = buildIndexMessage(article, tags, operation);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        articleMessageProducer.sendArticleIndexMessage(message);
                    }
                }
        );
    }

    /** 构建索引消息（逻辑与抽取前完全一致，tags 改为入参） */
    public ArticleIndexMessage buildIndexMessage(Article article, List<String> tags, MQOperation operation) {
        ArticleIndexMessage message = new ArticleIndexMessage();
        message.setOperation(operation);
        message.setArticleId(article.getId());
        message.setSlug(article.getSlug());
        message.setTitle(article.getTitle());
        message.setSummary(article.getSummary());
        message.setAuthorId(article.getAuthorId());
        try {
            Result<UserDTO> userDTO = userClient.getUserDTO(article.getAuthorId());
            if (userDTO != null && userDTO.getData() != null) {
                message.setAuthorName(userDTO.getData().getUsername());
                message.setAuthorAvatar(userDTO.getData().getAvatarUrl());
            }
        } catch (Exception e) {
            log.warn("获取作者信息失败, authorId: {}", article.getAuthorId(), e);
            message.setAuthorName(article.getAuthorId()); // 兜底：用 authorId
        }
        message.setCreatedAt(article.getCreatedAt());
        message.setPublishAt(article.getPublishAt());
        message.setUpdatedAt(article.getUpdatedAt());
        message.setStatus(article.getStatus());
        message.setTags(tags);

        // 加载文章内容（清洗 Markdown 为纯文本）
        try {
            ArticleContent content = contentDao.getById(article.getId());
            if (content != null) {
                message.setContentMd(MarkdownSanitizer.sanitize(content.getContentMd()));
            }
        } catch (Exception e) {
            log.warn("加载文章内容失败, articleId: {}", article.getId(), e);
        }

        // 加载统计数据
        try {
            ArticleStats stats = statsDao.getById(article.getId());
            if (stats != null) {
                message.setViewCount(stats.getViews());
                message.setLikeCount(stats.getLikes());
                message.setCommentCount(stats.getComments());
            }
        } catch (Exception e) {
            log.warn("加载文章统计失败, articleId: {}", article.getId(), e);
        }

        return message;
    }
}
```

`ArticleBizServiceImpl.java` 改动（本任务只做重构，审核门留到 Task 8）：

1. 注入新组件：在字段区加 `@NotNull private final ArticleIndexMessageService indexMessageService;`
2. `publish` 里第 95-96 行改为：
```java
        MQOperation operation = isNew ? MQOperation.CREATE : MQOperation.UPDATE;
        indexMessageService.sendIndexAfterCommit(article, dto.getTags(), operation);
```
3. 删除私有方法 `sendMessageAfterCommit`（第 108-120 行）和 `buildArticleIndexMessage`（第 122-172 行）。
4. 删除不再使用的 import（`MarkdownSanitizer`、`TransactionSynchronization`、`TransactionSynchronizationManager` 保留一个 `TransactionSynchronizationManager`——注意 `delete` 方法里还有 `registerSynchronization` 的匿名类调用，`TransactionSynchronizationManager` 仍需保留；`MarkdownSanitizer` 可删）。

- [ ] **Step 4: 运行确认通过**

Run: 同 Step 2 命令 + 全模块回归 `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/article-service -am test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 新测试 PASS；既有测试全绿（回归确认重构无行为变化）

- [ ] **Step 5: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add oy-blog-service/article-service/src/main/java/com/oyproj/service/ArticleIndexMessageService.java oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleBizServiceImpl.java && git commit -m "refactor: 抽出 ArticleIndexMessageService（供发布与人工审核共用，行为不变）"
```

---

## Task 7: ArticlePendingContent 实体 + Mapper

**Files:**
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/domain/entity/ArticlePendingContent.java`
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/mapper/ArticlePendingContentMapper.java`

**Interfaces:**
- Produces（Task 8/10 依赖）: `ArticlePendingContentMapper`（BaseMapper 增删改查）；实体字段 `articleId/pendingTitle/pendingSummary/pendingContentMd/pendingContentHtml/reviewReason/createdAt/updatedAt`

说明：实体+Mapper 无独立测试价值（都是注解声明），测试在 Task 8/10 的服务测试里覆盖，本任务直接实现+提交。

- [ ] **Step 1: 实现实体**

```java
package com.oyproj.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章待生效编辑（已发布文章的编辑被 AI 判"有歧义"时暂存，人工通过后替换生效）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_pending_content")
public class ArticlePendingContent {

    /**
     * 文章ID（复用 article.id，一篇最多一份待审编辑）
     */
    @TableId(value = "article_id")
    private String articleId;

    /**
     * 待生效标题
     */
    @TableField("pending_title")
    private String pendingTitle;

    /**
     * 待生效摘要
     */
    @TableField("pending_summary")
    private String pendingSummary;

    /**
     * 待生效 Markdown 正文
     */
    @TableField("pending_content_md")
    private String pendingContentMd;

    /**
     * 待生效 HTML 正文
     */
    @TableField("pending_content_html")
    private String pendingContentHtml;

    /**
     * AI 转人工理由
     */
    @TableField("review_reason")
    private String reviewReason;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 实现 Mapper**

```java
package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.ArticlePendingContent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章待生效编辑映射器
 */
@Mapper
public interface ArticlePendingContentMapper extends BaseMapper<ArticlePendingContent> {}
```

- [ ] **Step 3: 编译验证**

Run: `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/article-service -am compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add oy-blog-service/article-service/src/main/java/com/oyproj/domain/entity/ArticlePendingContent.java oy-blog-service/article-service/src/main/java/com/oyproj/mapper/ArticlePendingContentMapper.java && git commit -m "feat: 文章待生效编辑实体与Mapper（article_pending_content）"
```

---

## Task 8: publish 审核门（核心改造）

**Files:**
- Modify: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleBizServiceImpl.java`（`publish` 方法重写）
- Test: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/test/java/com/oyproj/service/impl/ArticleBizPublishModerationTest.java`

**Interfaces:**
- Consumes: Task 5 `ModerationService`、Task 6 `ArticleIndexMessageService`、Task 7 `ArticlePendingContentMapper`、已有 `articleDao/revisionDao/contentDao/chapterDao/statsDao/tagDao/articleTagMapper/articleMessageProducer/userClient/userArticleStatDao`
- Produces: `POST /article/publish` 返回 map 恒含 `articleId`、`verdict`（exempt/approved/rejected/manual）、`reason` 三个键

- [ ] **Step 1: 写失败测试**

```java
package com.oyproj.service.impl;

import com.oyproj.api.user.client.UserClient;
import com.oyproj.common.base.Result;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.dao.UserArticleStatDao;
import com.oyproj.domain.dto.ArticleSaveDto;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticlePendingContent;
import com.oyproj.dto.*;
import com.oyproj.mapper.ArticlePendingContentMapper;
import com.oyproj.mapper.ArticleTagMapper;
import com.oyproj.service.ArticleIndexMessageService;
import com.oyproj.service.ArticleMessageProducer;
import com.oyproj.service.ModerationService;
import com.oyproj.service.ModerationVerdict;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * publish 审核门测试：三态分流、豁免直放、先审后生效
 */
@ExtendWith(MockitoExtension.class)
class ArticleBizPublishModerationTest {

    @Mock private ArticleDao articleDao;
    @Mock private ArticleRevisionDao revisionDao;
    @Mock private ArticleContentDao contentDao;
    @Mock private ArticleChapterDao chapterDao;
    @Mock private ArticleStatsDao statsDao;
    @Mock private TagDao tagDao;
    @Mock private ArticleTagMapper articleTagMapper;
    @Mock private ArticleMessageProducer articleMessageProducer;
    @Mock private UserClient userClient;
    @Mock private UserArticleStatDao userArticleStatDao;
    @Mock private ModerationService moderationService;
    @Mock private ArticleIndexMessageService indexMessageService;
    @Mock private ArticlePendingContentMapper pendingContentMapper;

    @InjectMocks private ArticleBizServiceImpl biz;

    @BeforeAll
    static void initI18n() throws Exception {
        Field field = I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, (MessageSource) new StaticMessageSource());
    }

    @BeforeEach
    void setUp() {
        // 默认：审核开启 + 普通用户（不豁免），每个测试按需覆盖。
        // lenient：个别测试（如开关关闭、豁免）不会同时走两个 stub，严格模式会误报
        lenient().when(moderationService.isEnabled()).thenReturn(true);
        lenient().when(moderationService.isExempt()).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "u1");
        request.addHeader("X-User-Type", "READER");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private ArticleSaveDto dto() {
        ArticleSaveDto dto = new ArticleSaveDto();
        dto.setTitle("新标题");
        dto.setSummary("摘要");
        dto.setContentMd("# 正文");
        return dto;
    }

    private Article publishedArticle() {
        Article a = new Article();
        a.setId("a1");
        a.setTitle("旧标题");
        a.setAuthorId("u1");
        a.setStatus("published");
        return a;
    }

    @Test
    void approveNewArticlePublishes() {
        when(articleDao.getById(anyString())).thenReturn(null); // 新文章：无 id，不查
        when(moderationService.moderate(anyString(), anyString(), any(), anyString()))
                .thenReturn(ModerationVerdict.approved("内容正常"));

        Result<Map<String, String>> result = biz.publish(dto());

        assertEquals(true, result.getIsSuccess());
        assertEquals("approved", result.getData().get("verdict"));

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao, atLeastOnce()).save(captor.capture());
        Article saved = captor.getValue();
        // 注意：captor 按引用捕获，save 之后同一实例还会被 setReviewStatus/setIsReviewed 变异，
        // 所以这里只断言 save 之后不再变动的字段
        assertEquals("published", saved.getStatus());
        assertEquals("u1", saved.getAuthorId());

        verify(contentDao).saveOrUpdate(any());
        verify(indexMessageService).sendIndexAfterCommit(any(), anyList(), eq(com.oyproj.common.mq.constants.MQOperation.CREATE));
        verify(moderationService).writeLog(anyString(), eq("ai_approve"), eq("内容正常"), eq("ai"));
    }

    @Test
    void rejectNewArticleSavesAsRejectedWithoutIndex() {
        when(moderationService.moderate(anyString(), anyString(), any(), anyString()))
                .thenReturn(ModerationVerdict.rejected("广告引流"));

        Result<Map<String, String>> result = biz.publish(dto());

        assertEquals("rejected", result.getData().get("verdict"));

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao, atLeastOnce()).save(captor.capture());
        Article saved = captor.getValue();
        assertEquals("rejected", saved.getStatus());
        assertEquals("rejected", saved.getReviewStatus());
        assertEquals("广告引流", saved.getReviewReason());

        verify(contentDao).saveOrUpdate(any()); // 内容照常保存，作者可改后重发
        verify(indexMessageService, never()).sendIndexAfterCommit(any(), anyList(), any());
        verify(moderationService).writeLog(anyString(), eq("ai_reject"), eq("广告引流"), eq("ai"));
    }

    @Test
    void manualNewArticleSavesAsPendingReview() {
        when(moderationService.moderate(anyString(), anyString(), any(), anyString()))
                .thenReturn(ModerationVerdict.manual("内容有歧义"));

        Result<Map<String, String>> result = biz.publish(dto());

        assertEquals("manual", result.getData().get("verdict"));

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao, atLeastOnce()).save(captor.capture());
        Article saved = captor.getValue();
        assertEquals("pending_review", saved.getStatus());
        assertEquals("manual", saved.getReviewStatus());

        verify(indexMessageService, never()).sendIndexAfterCommit(any(), anyList(), any());
        verify(moderationService).writeLog(anyString(), eq("ai_manual"), eq("内容有歧义"), eq("ai"));
    }

    @Test
    void approveEditOfPublishedOverwrites() {
        when(articleDao.getById("a1")).thenReturn(publishedArticle());
        when(moderationService.moderate(anyString(), anyString(), any(), anyString()))
                .thenReturn(ModerationVerdict.approved("内容正常"));

        ArticleSaveDto dto = dto();
        dto.setId("a1");

        Result<Map<String, String>> result = biz.publish(dto);

        assertEquals("approved", result.getData().get("verdict"));
        verify(contentDao).saveOrUpdate(any());
        verify(indexMessageService).sendIndexAfterCommit(any(), anyList(), eq(com.oyproj.common.mq.constants.MQOperation.UPDATE));
        verify(pendingContentMapper, never()).insert(any());
    }

    @Test
    void rejectEditOfPublishedKeepsOldContent() {
        when(articleDao.getById("a1")).thenReturn(publishedArticle());
        when(moderationService.moderate(anyString(), anyString(), any(), anyString()))
                .thenReturn(ModerationVerdict.rejected("广告引流"));

        ArticleSaveDto dto = dto();
        dto.setId("a1");

        Result<Map<String, String>> result = biz.publish(dto);

        assertEquals("rejected", result.getData().get("verdict"));

        verify(contentDao, never()).saveOrUpdate(any()); // 先审后生效：旧内容不动
        verify(indexMessageService, never()).sendIndexAfterCommit(any(), anyList(), any());

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao, atLeastOnce()).updateById(captor.capture());
        Article updated = captor.getValue();
        assertEquals("published", updated.getStatus()); // 旧版继续发布中
        assertEquals("rejected", updated.getReviewStatus());
        assertEquals("广告引流", updated.getReviewReason());
    }

    @Test
    void manualEditOfPublishedStoresPendingContent() {
        when(articleDao.getById("a1")).thenReturn(publishedArticle());
        when(moderationService.moderate(anyString(), anyString(), any(), anyString()))
                .thenReturn(ModerationVerdict.manual("内容有歧义"));
        when(pendingContentMapper.selectById("a1")).thenReturn(null);

        ArticleSaveDto dto = dto();
        dto.setId("a1");

        Result<Map<String, String>> result = biz.publish(dto);

        assertEquals("manual", result.getData().get("verdict"));

        verify(contentDao, never()).saveOrUpdate(any()); // 旧内容不动
        verify(indexMessageService, never()).sendIndexAfterCommit(any(), anyList(), any());

        ArgumentCaptor<ArticlePendingContent> captor = ArgumentCaptor.forClass(ArticlePendingContent.class);
        verify(pendingContentMapper).insert(captor.capture());
        ArticlePendingContent pending = captor.getValue();
        assertEquals("a1", pending.getArticleId());
        assertEquals("新标题", pending.getPendingTitle());
        assertEquals("# 正文", pending.getPendingContentMd());
        assertEquals("内容有歧义", pending.getReviewReason());

        // 文章本体仅审核字段更新，status 保持 published（旧版继续展示）
        ArgumentCaptor<Article> articleCaptor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao, atLeastOnce()).updateById(articleCaptor.capture());
        Article updated = articleCaptor.getValue();
        assertEquals("published", updated.getStatus());
        assertEquals("manual", updated.getReviewStatus());
    }

    @Test
    void manualEditOverwritesExistingPending() {
        ArticlePendingContent existing = ArticlePendingContent.builder()
                .articleId("a1").pendingTitle("上一版").createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now()).build();
        when(articleDao.getById("a1")).thenReturn(publishedArticle());
        when(moderationService.moderate(anyString(), anyString(), any(), anyString()))
                .thenReturn(ModerationVerdict.manual("再次歧义"));
        when(pendingContentMapper.selectById("a1")).thenReturn(existing);

        ArticleSaveDto dto = dto();
        dto.setId("a1");

        biz.publish(dto);

        verify(pendingContentMapper).updateById(any(ArticlePendingContent.class));
        verify(pendingContentMapper, never()).insert(any());
    }

    @Test
    void exemptUserSkipsAiAndPublishesDirectly() {
        when(moderationService.isExempt()).thenReturn(true);

        Result<Map<String, String>> result = biz.publish(dto());

        assertEquals("exempt", result.getData().get("verdict"));
        verify(moderationService, never()).moderate(anyString(), anyString(), any(), anyString());

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleDao, atLeastOnce()).save(captor.capture());
        assertEquals("published", captor.getValue().getStatus());

        verify(indexMessageService).sendIndexAfterCommit(any(), anyList(), eq(com.oyproj.common.mq.constants.MQOperation.CREATE));
    }

    @Test
    void disabledSwitchPublishesDirectly() {
        when(moderationService.isEnabled()).thenReturn(false);

        Result<Map<String, String>> result = biz.publish(dto());

        assertEquals("exempt", result.getData().get("verdict"));
        verify(moderationService, never()).moderate(anyString(), anyString(), any(), anyString());
    }

    @Test
    void resubmitRejectedArticleApprovePublishes() {
        Article rejected = publishedArticle();
        rejected.setStatus("rejected");
        when(articleDao.getById("a1")).thenReturn(rejected);
        when(moderationService.moderate(anyString(), anyString(), any(), anyString()))
                .thenReturn(ModerationVerdict.approved("内容正常"));

        ArticleSaveDto dto = dto();
        dto.setId("a1");

        Result<Map<String, String>> result = biz.publish(dto);

        assertEquals("approved", result.getData().get("verdict"));
        verify(indexMessageService).sendIndexAfterCommit(any(), anyList(), eq(com.oyproj.common.mq.constants.MQOperation.CREATE));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/article-service -am test -Dtest=ArticleBizPublishModerationTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（现 publish 无审核门，行为与断言不符；个别断言如 verdict 键缺失）

- [ ] **Step 3: 重写 `publish` 方法**

`ArticleBizServiceImpl.java`：

1. 注入新依赖（字段区追加）：
```java
    @NotNull private final ModerationService moderationService;
    @NotNull private final ArticlePendingContentMapper pendingContentMapper;
```
2. 把 `publish` 方法整体替换为：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, String>> publish(ArticleSaveDto dto) {
        // contentHtml 为空时，自动从 contentMd 渲染
        if (!StringUtils.hasText(dto.getContentHtml())) {
            dto.setContentHtml(MarkdownRenderer.toHtml(dto.getContentMd()));
        }
        boolean isNew = !StringUtils.hasText(dto.getId());
        Article existing = isNew ? null : articleDao.getById(dto.getId());
        if (!isNew && existing == null) {
            throw new NotFoundException(I18nUtils.t("article.not_found"));
        }
        boolean editingPublished = existing != null && "published".equals(existing.getStatus());

        // 审核门：开关关闭或豁免用户 → 直接放行
        if (!moderationService.isEnabled() || moderationService.isExempt()) {
            Article article = saveArticleBase(dto, "published");
            String articleId = article.getId();
            saveRevision(articleId, dto.getContentMd());
            saveContent(articleId, dto.getContentMd(), dto.getContentHtml());
            parseAndSaveChapters(articleId, dto.getContentMd());
            saveRelations(articleId, dto);
            article.setReviewStatus("exempt");
            article.setIsReviewed(1);
            articleDao.updateById(article);
            MQOperation operation = isNew ? MQOperation.CREATE : MQOperation.UPDATE;
            indexMessageService.sendIndexAfterCommit(article, dto.getTags(), operation);
            return publishResult(articleId, "exempt", "审核豁免");
        }

        // AI 审核（先审后写：已发布文章的编辑在此阶段尚未覆盖内容）
        ModerationVerdict verdict = moderationService.moderate(
                isNew ? "" : dto.getId(), dto.getTitle(), dto.getSummary(), dto.getContentMd());

        if (verdict.isApproved()) {
            Article article = saveArticleBase(dto, "published");
            String articleId = article.getId();
            saveRevision(articleId, dto.getContentMd());
            saveContent(articleId, dto.getContentMd(), dto.getContentHtml());
            parseAndSaveChapters(articleId, dto.getContentMd());
            saveRelations(articleId, dto);
            article.setReviewStatus("approved");
            article.setReviewReason(verdict.reason());
            article.setIsReviewed(1);
            articleDao.updateById(article);
            MQOperation operation = isNew ? MQOperation.CREATE : MQOperation.UPDATE;
            indexMessageService.sendIndexAfterCommit(article, dto.getTags(), operation);
            moderationService.writeLog(articleId, "ai_approve", verdict.reason(), "ai");
            return publishResult(articleId, "approved", verdict.reason());
        }

        if (verdict.isRejected()) {
            if (editingPublished) {
                // 先审后生效：本次编辑全部丢弃，旧版继续对外展示
                existing.setReviewStatus("rejected");
                existing.setReviewReason(verdict.reason());
                articleDao.updateById(existing);
                moderationService.writeLog(existing.getId(), "ai_reject", verdict.reason(), "ai");
                return publishResult(existing.getId(), "rejected", verdict.reason());
            }
            // 新文章/重发：内容照常保存为已驳回状态，作者可改后重新发布
            Article article = saveArticleBase(dto, "rejected");
            String articleId = article.getId();
            saveRevision(articleId, dto.getContentMd());
            saveContent(articleId, dto.getContentMd(), dto.getContentHtml());
            parseAndSaveChapters(articleId, dto.getContentMd());
            saveRelations(articleId, dto);
            article.setReviewStatus("rejected");
            article.setReviewReason(verdict.reason());
            articleDao.updateById(article);
            moderationService.writeLog(articleId, "ai_reject", verdict.reason(), "ai");
            return publishResult(articleId, "rejected", verdict.reason());
        }

        // manual：转人工
        if (editingPublished) {
            // 新版本进待生效区，旧版继续 published；封面/允许评论/标签属未审字段，本次正常生效
            existing.setCoverUrl(dto.getCoverUrl());
            existing.setAllowComment(dto.getAllowComment() != null ? dto.getAllowComment() : 1);
            existing.setUpdateAt(LocalDateTime.now());
            existing.setReviewStatus("manual");
            existing.setReviewReason(verdict.reason());
            articleDao.updateById(existing);
            saveRelations(existing.getId(), dto);
            savePendingContent(existing.getId(), dto, verdict.reason());
            moderationService.writeLog(existing.getId(), "ai_manual", verdict.reason(), "ai");
            return publishResult(existing.getId(), "manual", verdict.reason());
        }
        Article article = saveArticleBase(dto, "pending_review");
        String articleId = article.getId();
        saveRevision(articleId, dto.getContentMd());
        saveContent(articleId, dto.getContentMd(), dto.getContentHtml());
        parseAndSaveChapters(articleId, dto.getContentMd());
        saveRelations(articleId, dto);
        article.setReviewStatus("manual");
        article.setReviewReason(verdict.reason());
        articleDao.updateById(article);
        moderationService.writeLog(articleId, "ai_manual", verdict.reason(), "ai");
        return publishResult(articleId, "manual", verdict.reason());
    }

    /** 组装 publish 返回：恒含 articleId/verdict/reason，前端据此提示"已驳回+原因/审核中" */
    private Result<Map<String, String>> publishResult(String articleId, String verdict, String reason) {
        Map<String, String> result = new HashMap<>();
        result.put("articleId", articleId);
        result.put("verdict", verdict);
        result.put("reason", reason == null ? "" : reason);
        return Result.ok(result);
    }

    /** 待生效编辑写入（一篇最多一份：已存在则覆盖） */
    private void savePendingContent(String articleId, ArticleSaveDto dto, String reason) {
        LocalDateTime now = LocalDateTime.now();
        ArticlePendingContent pending = pendingContentMapper.selectById(articleId);
        if (pending == null) {
            pending = ArticlePendingContent.builder()
                    .articleId(articleId)
                    .createdAt(now)
                    .build();
        }
        pending.setPendingTitle(dto.getTitle());
        pending.setPendingSummary(dto.getSummary());
        pending.setPendingContentMd(dto.getContentMd());
        pending.setPendingContentHtml(dto.getContentHtml());
        pending.setReviewReason(reason);
        pending.setUpdatedAt(now);
        if (pendingContentMapper.selectById(articleId) == null) {
            pendingContentMapper.insert(pending);
        } else {
            pendingContentMapper.updateById(pending);
        }
    }
```

3. `import com.oyproj.service.ModerationService;`、`import com.oyproj.service.ModerationVerdict;`、`import com.oyproj.service.ArticleIndexMessageService;` 已在 Task 6 部分存在，核对 import 区（`ArticlePendingContent` 在 `com.oyproj.domain.entity.*` 通配里已覆盖，无需新 import）。

- [ ] **Step 4: 运行确认通过**

Run: 同 Step 2 命令
Expected: 10 个测试全 PASS

- [ ] **Step 5: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleBizServiceImpl.java && git commit -m "feat: publish 审核门（AI三态分流+豁免直放+已发布编辑先审后生效）"
```

---

## Task 9: 公开读取可见性修补 + ArticleInfoVo 审核字段

**Files:**
- Modify: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleReadBizServiceImpl.java`
- Modify: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/domain/vo/ArticleInfoVo.java`
- Test: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/test/java/com/oyproj/service/impl/ArticleReadVisibilityTest.java`

**Interfaces:**
- Consumes: 已有 `articleDao/contentDao/chapterDao`；`ArticleBaseBizService.getUserId()`（读 X-User-Id）
- Produces: 公开读取规则 = `published` 人人可见；非 published 仅作者本人可见；违规访问抛 `NotFoundException("article.not_found")`（不泄露存在性）

- [ ] **Step 1: 写失败测试**

```java
package com.oyproj.service.impl;

import com.oyproj.api.user.client.UserClient;
import com.oyproj.common.exception.NotFoundException;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleContent;
import com.oyproj.dto.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 公开读取可见性测试：published 人人可见，其余仅作者本人
 */
@ExtendWith(MockitoExtension.class)
class ArticleReadVisibilityTest {

    @Mock private ArticleDao articleDao;
    @Mock private ArticleContentDao contentDao;
    @Mock private ArticleChapterDao chapterDao;
    @Mock private ArticleStatsDao articleStatsDao;
    @Mock private TagDao tagDao;
    @Mock private ArticleTagDao articleTagDao;
    @Mock private UserClient userClient;

    @InjectMocks private ArticleReadBizServiceImpl readBiz;

    @BeforeAll
    static void initI18n() throws Exception {
        Field field = I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, (MessageSource) new StaticMessageSource());
    }

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        // enrich 链路防 NPE：mock 默认返回 null 会让 enrichWithStats/enrichWithTags 空指针，
        // 统一给空集合（enrichWithAuthorInfo 内部有 try/catch + null 判断，默认 null 即安全）
        when(articleStatsDao.listByArticleIds(anyList())).thenReturn(Collections.emptyList());
        when(articleTagDao.listTagNamesByArticleIds(anyList())).thenReturn(Collections.emptyMap());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private Article article(String status) {
        Article a = new Article();
        a.setId("a1");
        a.setSlug("a1");
        a.setAuthorId("u1");
        a.setStatus(status);
        a.setTitle("标题");
        return a;
    }

    @Test
    void publishedVisibleToAnyone() {
        when(articleDao.getBySlug("a1")).thenReturn(article("published"));

        readBiz.getBySlug("a1"); // 不抛异常即通过
    }

    @Test
    void draftVisibleToOwner() {
        when(articleDao.getBySlug("a1")).thenReturn(article("draft"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "u1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        readBiz.getBySlug("a1"); // 作者可读自己的草稿（创作中心编辑用）
    }

    @Test
    void draftHiddenFromOthers() {
        when(articleDao.getBySlug("a1")).thenReturn(article("draft"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "u2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThrows(NotFoundException.class, () -> readBiz.getBySlug("a1"));
    }

    @Test
    void pendingReviewHiddenFromOthers() {
        when(articleDao.getById("a1")).thenReturn(article("pending_review"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "u2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThrows(NotFoundException.class, () -> readBiz.getById("a1"));
    }

    @Test
    void rejectedVisibleToOwner() {
        when(articleDao.getById("a1")).thenReturn(article("rejected"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "u1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        readBiz.getById("a1"); // 作者可见自己的被驳回文章
    }

    @Test
    void missingArticleThrowsNotFound() {
        when(articleDao.getBySlug("ghost")).thenReturn(null);

        assertThrows(NotFoundException.class, () -> readBiz.getBySlug("ghost"));
    }

    @Test
    void deletedArticleHiddenEvenForOwner() {
        Article a = article("published");
        a.setDeletedAt(java.time.LocalDateTime.now());
        when(articleDao.getBySlug("a1")).thenReturn(a);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "u1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThrows(NotFoundException.class, () -> readBiz.getBySlug("a1"));
    }

    @Test
    void draftContentHiddenFromOthers() {
        when(articleDao.getById("a1")).thenReturn(article("draft"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "u2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThrows(NotFoundException.class, () -> readBiz.getContent("a1"));
    }

    @Test
    void publishedContentVisibleToAnyone() {
        when(articleDao.getById("a1")).thenReturn(article("published"));
        ArticleContent content = new ArticleContent();
        content.setContentMd("# 正文");
        when(contentDao.getById("a1")).thenReturn(content);

        readBiz.getContent("a1"); // 不抛异常即通过
    }
}
```

> 注意：`ArticleReadBizServiceImpl` 的构造依赖以实际字段为准（上方 @Mock 列表按其现有 @NotNull final 字段整理：articleDao/contentDao/chapterDao/articleStatsDao/tagDao/articleTagDao/userClient/hotWeightProperties；`hotWeightProperties` 若缺编译即报错，补 `@Mock private HotWeightProperties hotWeightProperties;` 即可）。

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/article-service -am test -Dtest=ArticleReadVisibilityTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（现实现无可见性校验，`draftHiddenFromOthers` 等断言不成立）

- [ ] **Step 3: 实现可见性修补**

`ArticleReadBizServiceImpl.java`：

1. `getBySlug` 改为（第 58-65 行）：
```java
    @Override
    public Result<ArticleInfoVo> getBySlug(String slug) {
        Article article = articleDao.getBySlug(slug);
        if (!canView(article)) {
            throw new NotFoundException(I18nUtils.t("article.not_found"));
        }
        ArticleInfoVo vo = copyProperties(article, ArticleInfoVo.class);
        enrichWithAuthorInfo(Collections.singletonList(vo));
        enrichWithStats(Collections.singletonList(vo));
        enrichWithTags(Collections.singletonList(vo));
        return Result.ok(vo);
    }
```

2. `getContent` 改为（第 73-76 行）：
```java
    @Override
    public Result<ArticleContentVo> getContent(String articleId) {
        if (!canView(articleDao.getById(articleId))) {
            throw new NotFoundException(I18nUtils.t("article.not_found"));
        }
        return Result.ok(copyProperties(contentDao.getById(articleId), ArticleContentVo.class));
    }
```

3. `getById` 改为（第 281-288 行）：
```java
    @Override
    public Result<ArticleInfoVo> getById(String articleId) {
        Article article = articleDao.getById(articleId);
        if (!canView(article)) {
            throw new NotFoundException(I18nUtils.t("article.not_found"));
        }
        ArticleInfoVo vo = copyProperties(article, ArticleInfoVo.class);
        enrichWithAuthorInfo(Collections.singletonList(vo));
        enrichWithStats(Collections.singletonList(vo));
        enrichWithTags(Collections.singletonList(vo));
        return Result.ok(vo);
    }
```

4. `listChapters` 同样加校验（第 84-87 行）：
```java
    @Override
    public Result<List<ArticleChapterVo>> listChapters(String articleId) {
        if (!canView(articleDao.getById(articleId))) {
            throw new NotFoundException(I18nUtils.t("article.not_found"));
        }
        return Result.ok(copyList(chapterDao.listByArticle(articleId), ArticleChapterVo.class));
    }
```

5. 新增私有方法（类末尾）：
```java
    /**
     * 公开可见性：已发布人人可见；非已发布仅作者本人可见（供创作中心读取草稿/待审/驳回）。
     * 不可见一律 NotFound，不泄露文章存在性。
     */
    private boolean canView(Article article) {
        if (article == null || article.getDeletedAt() != null) {
            return false;
        }
        if ("published".equals(article.getStatus())) {
            return true;
        }
        String userId = getUserId();
        return userId != null && userId.equals(article.getAuthorId());
    }
```

6. import 补 `com.oyproj.common.exception.NotFoundException` 与 `com.oyproj.common.utils.I18nUtils`（若未导入）。

`ArticleInfoVo.java` 加字段（第 24 行 `allowComment` 之后）：
```java
    /**
     * 审核结论（approved/rejected/manual/exempt，仅创作中心列表需要）
     */
    private String reviewStatus;

    /**
     * 审核理由（驳回/转人工时给作者看）
     */
    private String reviewReason;
```

- [ ] **Step 4: 运行确认通过**

Run: 同 Step 2 命令 + 回归 `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/article-service -am test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 新测试 PASS；既有测试全绿（公开列表/详情等既有测试若有断言 draft 可见的行为需按新规则修正——公开读路径补丁是有意行为变更，若旧测试断言旧行为，改为断言新行为）

- [ ] **Step 5: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleReadBizServiceImpl.java oy-blog-service/article-service/src/main/java/com/oyproj/domain/vo/ArticleInfoVo.java && git commit -m "fix: 公开读取仅限 published/作者本人（堵住 draft 直读漏洞）+ VO 审核字段"
```

---

## Task 10: article-service 人工审核队列（DTO + 业务 + 控制器 + 章节重建）

**Files:**
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/dto/ArticleModerationPageDto.java`
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/dto/ArticleModerationAuditDto.java`
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/vo/ArticleModerationItemVo.java`
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/service/ArticleChapterService.java`（从 ArticleBizServiceImpl 原样迁出章节解析，纯重构）
- Modify: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleBizServiceImpl.java`（删除 7 个章节私有方法，改调组件）
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/service/ModerationAdminBizService.java`
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ModerationAdminBizServiceImpl.java`
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/main/java/com/oyproj/controller/ModerationAdminController.java`
- Test: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/article-service/src/test/java/com/oyproj/service/impl/ModerationAdminBizServiceImplTest.java`

**Interfaces:**
- Consumes: Task 6 `ArticleIndexMessageService`、Task 7 `ArticlePendingContentMapper`、已有 `articleDao/articleTagDao/contentDao`；Task 5 `ModerationService.writeLog`
- Produces（Task 11 Feign 依赖）: `POST /article/moderation/admin/page` → `Result<PageVo<List<ArticleModerationItemVo>>>`；`POST /article/moderation/admin/audit` → `Result<Boolean>`
- Produces: `ArticleChapterService.rebuild(String articleId, String contentMd)`（发布与人工通过编辑共用；行为与原 `parseAndSaveChapters` 完全一致）

- [ ] **Step 1: 写失败测试**

```java
package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleContent;
import com.oyproj.domain.entity.ArticlePendingContent;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.dto.ArticleDao;
import com.oyproj.dto.ArticleTagDao;
import com.oyproj.mapper.ArticlePendingContentMapper;
import com.oyproj.service.ArticleChapterService;
import com.oyproj.service.ArticleIndexMessageService;
import com.oyproj.service.ModerationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 人工审核队列测试：两类待审合并、通过/驳回四场景
 */
@ExtendWith(MockitoExtension.class)
class ModerationAdminBizServiceImplTest {

    @Mock private ArticleDao articleDao;
    @Mock private ArticleContentDao contentDao;
    @Mock private ArticleTagDao articleTagDao;
    @Mock private ArticlePendingContentMapper pendingContentMapper;
    @Mock private ModerationService moderationService;
    @Mock private ArticleIndexMessageService indexMessageService;
    @Mock private ArticleChapterService chapterService;

    @InjectMocks private ModerationAdminBizServiceImpl biz;

    @BeforeAll
    static void initI18n() throws Exception {
        Field field = I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, (MessageSource) new StaticMessageSource());
    }

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "admin1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private Article pendingArticle() {
        Article a = new Article();
        a.setId("a1");
        a.setTitle("待审文章");
        a.setAuthorId("u1");
        a.setStatus("pending_review");
        a.setReviewReason("AI 觉得有歧义");
        a.setCreatedAt(LocalDateTime.now());
        return a;
    }

    @Test
    void pageMergesNewAndEditItems() {
        when(articleDao.listByAuthorAndStatus(anyString(), anyString(), any())).thenReturn(Collections.emptyList());
        when(articleDao.list(any(Wrapper.class))).thenReturn(List.of(pendingArticle()));
        when(pendingContentMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        Result<PageVo<List<ArticleModerationItemVo>>> result = biz.adminPage(new ArticleModerationPageDto());

        assertTrue(result.getIsSuccess());
        assertEquals(1, result.getData().getTotal());
        ArticleModerationItemVo item = result.getData().getList().get(0);
        assertEquals("NEW", item.getKind());
        assertEquals("待审文章", item.getTitle());
        assertEquals("AI 觉得有歧义", item.getReviewReason());
    }

    @Test
    void auditApproveNewArticlePublishes() {
        when(articleDao.getById("a1")).thenReturn(pendingArticle());
        when(articleTagDao.listTagNamesByArticleIds(anyList())).thenReturn(Collections.emptyMap());

        ArticleModerationAuditDto dto = new ArticleModerationAuditDto();
        dto.setArticleId("a1");
        dto.setApprove(true);
        dto.setReason("人工确认放行");

        Result<Boolean> result = biz.audit(dto);

        assertTrue(result.getIsSuccess());
        verify(articleDao).updateById(any(Article.class));
        verify(indexMessageService).sendIndexAfterCommit(any(), anyList(), eq(com.oyproj.common.mq.constants.MQOperation.CREATE));
        verify(moderationService).writeLog("a1", "manual_approve", "人工确认放行", "admin1");
    }

    @Test
    void auditRejectNewArticleRejects() {
        when(articleDao.getById("a1")).thenReturn(pendingArticle());

        ArticleModerationAuditDto dto = new ArticleModerationAuditDto();
        dto.setArticleId("a1");
        dto.setApprove(false);
        dto.setReason("违规内容");

        biz.audit(dto);

        verify(articleDao).updateById(any(Article.class));
        verify(indexMessageService, never()).sendIndexAfterCommit(any(), anyList(), any());
        verify(moderationService).writeLog("a1", "manual_reject", "违规内容", "admin1");
    }

    @Test
    void auditApproveEditAppliesPending() {
        Article published = pendingArticle();
        published.setStatus("published");
        published.setTitle("旧标题");
        when(articleDao.getById("a1")).thenReturn(published);
        ArticlePendingContent pending = ArticlePendingContent.builder()
                .articleId("a1")
                .pendingTitle("新标题")
                .pendingSummary("新摘要")
                .pendingContentMd("# 新正文")
                .pendingContentHtml("<h1>新正文</h1>")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(pendingContentMapper.selectById("a1")).thenReturn(pending);
        when(contentDao.getById("a1")).thenReturn(null);
        when(articleTagDao.listTagNamesByArticleIds(anyList())).thenReturn(Collections.emptyMap());

        ArticleModerationAuditDto dto = new ArticleModerationAuditDto();
        dto.setArticleId("a1");
        dto.setApprove(true);

        biz.audit(dto);

        verify(articleDao).updateById(any(Article.class));
        verify(contentDao).saveOrUpdate(any(ArticleContent.class));
        verify(pendingContentMapper).deleteById("a1"); // 待审内容生效后清空
        verify(chapterService).rebuild("a1", "# 新正文"); // 章节目录随新内容重建
        verify(indexMessageService).sendIndexAfterCommit(any(), anyList(), eq(com.oyproj.common.mq.constants.MQOperation.UPDATE));
        verify(moderationService).writeLog("a1", "manual_approve", any(), eq("admin1"));
    }

    @Test
    void auditRejectEditDiscardsPending() {
        Article published = pendingArticle();
        published.setStatus("published");
        when(articleDao.getById("a1")).thenReturn(published);
        ArticlePendingContent pending = ArticlePendingContent.builder()
                .articleId("a1").pendingTitle("新标题").createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now()).build();
        when(pendingContentMapper.selectById("a1")).thenReturn(pending);

        ArticleModerationAuditDto dto = new ArticleModerationAuditDto();
        dto.setArticleId("a1");
        dto.setApprove(false);
        dto.setReason("编辑内容不合规");

        biz.audit(dto);

        verify(pendingContentMapper).deleteById("a1");
        verify(contentDao, never()).saveOrUpdate(any());
        verify(indexMessageService, never()).sendIndexAfterCommit(any(), anyList(), any());
        verify(moderationService).writeLog("a1", "manual_reject", "编辑内容不合规", "admin1");
    }

    @Test
    void auditMissingItemReturnsError() {
        when(articleDao.getById("ghost")).thenReturn(null);
        when(pendingContentMapper.selectById("ghost")).thenReturn(null);

        ArticleModerationAuditDto dto = new ArticleModerationAuditDto();
        dto.setArticleId("ghost");
        dto.setApprove(true);

        Result<Boolean> result = biz.audit(dto);

        assertEquals(false, result.getIsSuccess());
        verify(articleDao, never()).updateById(any(Article.class));
    }
}
```

> 注意：`pageMergesNewAndEditItems` 里对 `articleDao.list(...)` 的 stub 形式取决于实现里用什么方法查 pending_review 列表——实现用 `articleDao.list(Wrapper)` 或新增 DAO 方法；若 `ArticleDao` 无 `list(Wrapper)`（IService 自带），直接用 IService 的 `list`。执行时以编译提示为准微调 stub。

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/article-service -am test -Dtest=ModerationAdminBizServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（类不存在）

- [ ] **Step 3: 实现 DTO/VO**

`ArticleModerationPageDto.java`：
```java
package com.oyproj.api.article.domain.dto;

import lombok.Data;

@Data
public class ArticleModerationPageDto {
    private Integer page = 1;
    private Integer size = 10;
}
```

`ArticleModerationAuditDto.java`：
```java
package com.oyproj.api.article.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ArticleModerationAuditDto {
    @NotBlank(message = "文章ID不能为空")
    private String articleId;
    /** true=通过 false=驳回 */
    @NotNull(message = "审核结果不能为空")
    private Boolean approve;
    private String reason;
}
```

`ArticleModerationItemVo.java`：
```java
package com.oyproj.api.article.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章待审队列项（NEW=待审新文章；EDIT=已发布文章的待审编辑）
 */
@Data
public class ArticleModerationItemVo {
    private String articleId;
    /** NEW / EDIT */
    private String kind;
    /** NEW=待审文章标题；EDIT=当前对外展示的旧标题 */
    private String title;
    private String authorId;
    private String summary;
    /** AI 转人工理由 */
    private String reviewReason;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    /** 仅 EDIT：待生效标题 */
    private String pendingTitle;
    /** 仅 EDIT：待生效摘要 */
    private String pendingSummary;
}
```

- [ ] **Step 4: 抽取 ArticleChapterService（纯重构，行为不变）**

动机：人工通过"待审编辑"后正文已变，章节目录必须跟着新内容重建；`parseAndSaveChapters` 原本是 `ArticleBizServiceImpl` 的私有方法，抽到组件后发布与审核两处共用。

新文件 `article-service/src/main/java/com/oyproj/service/ArticleChapterService.java`：

```java
package com.oyproj.service;

import com.oyproj.domain.entity.ArticleChapter;
import com.oyproj.dto.ArticleChapterDao;
import com.oyproj.common.utils.UUIDUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文章章节目录解析与保存（原 ArticleBizServiceImpl 的 7 个私有方法原样迁出）。
 * 迁出原因：人工审核通过待审编辑后正文变化，需要与发布路径共用同一套章节重建逻辑。
 */
@Service
@RequiredArgsConstructor
public class ArticleChapterService {

    private final ArticleChapterDao chapterDao;

    /** 解析正文标题并重建章节目录（原 parseAndSaveChapters 原样迁移，仅改私有→公开） */
    public void rebuild(String articleId, String content) {
        // 1. 清理旧章节
        List<ArticleChapter> oldChapters = chapterDao.listByArticle(articleId);
        for (ArticleChapter ch : oldChapters) {
            chapterDao.removeById(ch.getId());
        }

        if (!StringUtils.hasText(content)) {
            return;
        }

        // 2. 预处理内容：保护代码块
        String processedContent = protectCodeBlocks(content);

        // 3. 解析新章节
        Pattern pattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(processedContent);

        ArrayDeque<ArticleChapter> stack = new ArrayDeque<>();
        HashMap<String, Integer> anchorCount = new HashMap<>();
        int order = 1;
        List<ArticleChapter> chapters = new ArrayList<>();

        while (matcher.find()) {
            String hashes = matcher.group(1);
            String rawTitle = matcher.group(2).trim();
            int level = hashes.length();
            int start = matcher.start();

            // 检查是否在代码块内（如果是占位符，则跳过）
            if (isInsideCodeBlock(matcher.group(0), processedContent, start)) {
                continue;
            }

            // 提取纯文本标题，去除HTML标签
            String title = extractPlainTextFromTitle(rawTitle);

            // 清理标题
            title = cleanTitleText(title);

            if (title.isEmpty()) {
                continue;
            }

            String base = slugify(title);
            Integer cnt = anchorCount.getOrDefault(base, 0);
            String anchor = cnt == 0 ? base : base + "-" + cnt;
            anchorCount.put(base, cnt + 1);

            // 处理层级栈
            while (!stack.isEmpty() && stack.peek().getLevel() >= level) {
                stack.pop();
            }

            String parentId = stack.isEmpty() ? null : stack.peek().getId();

            ArticleChapter chapter = ArticleChapter.builder()
                    .id(UUIDUtils.getId())
                    .articleId(articleId)
                    .chapterOrder(order++)
                    .level(level)
                    .title(title)
                    .anchor(anchor)
                    .parentId(parentId)
                    .startOffset(start)
                    .endOffset(matcher.end())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            chapters.add(chapter);
            stack.push(chapter);
        }

        // 批量保存章节并建立路径关系
        saveChaptersWithPaths(chapters);
    }

    /** 保护代码块，将代码块替换为占位符，避免被误解析（原样迁移） */
    private String protectCodeBlocks(String content) {
        if (content == null) {
            return "";
        }

        // 匹配代码块（三个反引号包裹的）
        Pattern codeBlockPattern = Pattern.compile("(?s)```[\\s\\S]*?```");
        Matcher codeBlockMatcher = codeBlockPattern.matcher(content);

        // 匹配内联代码（单个反引号包裹的）
        Pattern inlineCodePattern = Pattern.compile("`[^`]+`");

        // 先处理代码块
        StringBuilder result = new StringBuilder();
        List<String> codeBlocks = new ArrayList<>();

        while (codeBlockMatcher.find()) {
            String codeBlock = codeBlockMatcher.group(0);
            String placeholder = "###CODE_BLOCK_" + codeBlocks.size() + "###";
            codeBlocks.add(codeBlock);
            codeBlockMatcher.appendReplacement(result, placeholder);
        }
        codeBlockMatcher.appendTail(result);

        String processed = result.toString();

        // 再处理内联代码
        List<String> inlineCodes = new ArrayList<>();
        Matcher inlineMatcher = inlineCodePattern.matcher(processed);
        result = new StringBuilder();

        while (inlineMatcher.find()) {
            String inlineCode = inlineMatcher.group(0);
            String placeholder = "###INLINE_CODE_" + inlineCodes.size() + "###";
            inlineCodes.add(inlineCode);
            inlineMatcher.appendReplacement(result, placeholder);
        }
        inlineMatcher.appendTail(result);

        processed = result.toString();

        // 保存到临时存储，供恢复使用（占位符列表，与迁移前行为一致）
        ThreadLocal<List<String>> codeBlocksStore = new ThreadLocal<>();
        ThreadLocal<List<String>> inlineCodesStore = new ThreadLocal<>();
        codeBlocksStore.set(codeBlocks);
        inlineCodesStore.set(inlineCodes);

        return processed;
    }

    /** 检查是否在代码块内（原样迁移） */
    private boolean isInsideCodeBlock(String match, String processedContent, int start) {
        // 检查是否为代码块占位符
        if (match.contains("###CODE_BLOCK_") || match.contains("###INLINE_CODE_")) {
            return true;
        }

        // 检查前一行是否为空行（标题应该前面有空行或文档开头）
        if (start > 0) {
            // 查找前一个换行符
            int prevNewline = processedContent.lastIndexOf('\n', start - 1);
            if (prevNewline >= 0) {
                // 检查前一行内容
                String prevLine = processedContent.substring(prevNewline + 1, start).trim();
                // 如果前一行不是空行，且不是标题（以#开头），则可能是列表项或其他内容
                if (!prevLine.isEmpty() && !prevLine.startsWith("#")) {
                    // 检查是否是列表项（如1. xxx）
                    return prevLine.matches("^\\d+\\.\\s+.+") ||
                            prevLine.matches("^[-*+]\\s+.+") ||
                            prevLine.matches("^>\\s+.+");
                }
            }
        }
        return false;
    }

    /** 简化标题文本提取（原样迁移） */
    private String extractPlainTextFromTitle(String titleWithHtml) {
        if (titleWithHtml == null || titleWithHtml.isEmpty()) {
            return "";
        }

        // 移除所有HTML标签
        String plainText = titleWithHtml.replaceAll("<[^>]*>", "");

        // 移除代码占位符
        plainText = plainText.replaceAll("###CODE_BLOCK_\\d+###", "");
        plainText = plainText.replaceAll("###INLINE_CODE_\\d+###", "");

        // 清理多余空格
        plainText = plainText.replaceAll("\\s+", " ").trim();

        if (plainText.isEmpty()) {
            // 尝试从属性中提取文本
            Pattern altPattern = Pattern.compile("alt\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
            Matcher altMatcher = altPattern.matcher(titleWithHtml);
            if (altMatcher.find()) {
                plainText = altMatcher.group(1);
            }
        }

        return plainText;
    }

    /** 清理标题文本（原样迁移） */
    private String cleanTitleText(String title) {
        if (title == null) {
            return "";
        }

        // 移除Markdown格式标记
        String cleaned = title
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")   // 加粗
                .replaceAll("\\*([^*]+)\\*", "$1")         // 斜体
                .replaceAll("__([^_]+)__", "$1")          // 加粗（下划线）
                .replaceAll("_([^_]+)_", "$1")            // 斜体（下划线）
                .replaceAll("~~([^~]+)~~", "$1")          // 删除线
                .replaceAll("`([^`]+)`", "$1")            // 内联代码
                .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")  // 链接
                .replaceAll("!\\[[^]]+]\\([^)]+\\)", "");    // 图片

        // 清理空格
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        cleaned = cleaned.replaceAll("^[\\s,.;:!?]+|[\\s,.;:!?]+$", "");

        return cleaned;
    }

    /** 批量保存章节并建立路径关系（原样迁移） */
    private void saveChaptersWithPaths(List<ArticleChapter> chapters) {
        // 先保存所有章节
        for (ArticleChapter chapter : chapters) {
            chapterDao.save(chapter);
        }

        // 建立路径关系
        Map<String, ArticleChapter> chapterMap = new HashMap<>();
        for (ArticleChapter chapter : chapters) {
            chapterMap.put(chapter.getId(), chapter);
        }

        // 为每个章节计算路径
        for (ArticleChapter chapter : chapters) {
            StringBuilder path = new StringBuilder();

            if (chapter.getParentId() != null) {
                ArticleChapter parent = chapterMap.get(chapter.getParentId());
                if (parent != null && parent.getPath() != null) {
                    path.append(parent.getPath()).append("/");
                }
            }

            path.append(chapter.getId());
            chapter.setPath(path.toString());
            chapterDao.updateById(chapter);
        }
    }

    /** slugify函数（原样迁移） */
    private String slugify(String s) {
        if (s == null || s.isEmpty()) {
            return "section";
        }

        String t = s.toLowerCase();
        t = t.replaceAll("[^\\p{L}\\p{N}\\s-]", ""); // 只保留字母、数字、空格、连字符
        t = t.replaceAll("\\s+", "-");
        t = t.replaceAll("-+", "-");
        t = t.replaceAll("^-|-$", "");

        if (t.isEmpty()) {
            return "section";
        }

        if (t.length() > 1000) {
            t = t.substring(0, 1000);
        }

        return t;
    }
}
```

`ArticleBizServiceImpl.java` 同步修改：

1. 注入组件：字段区加 `@NotNull private final ArticleChapterService chapterService;`
2. `saveDraft`/`publish`（Task 8 已重写）里所有 `parseAndSaveChapters(articleId, dto.getContentMd())` 调用改为 `chapterService.rebuild(articleId, dto.getContentMd())`（共 4 处：saveDraft 1 处 + publish 豁免/approve/reject/manual 各 1 处，以实际为准）
3. 删除 7 个私有方法：`parseAndSaveChapters`、`protectCodeBlocks`、`restoreCodeBlocks`（原就未被调用）、`isInsideCodeBlock`、`extractPlainTextFromTitle`、`cleanTitleText`、`saveChaptersWithPaths`、`slugify`
4. 删除不再使用的 import（`ArrayDeque`、`Matcher`、`Pattern`、`UUIDUtils` 若无其他使用一并删）

- [ ] **Step 5: 实现业务与控制器**

`ModerationAdminBizService.java`：
```java
package com.oyproj.service;

import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;

/**
 * 文章人工审核后台业务
 */
public interface ModerationAdminBizService {
    /** 待审队列：pending_review 新文章 + 已发布文章的待审编辑，两类合并分页 */
    Result<PageVo<List<ArticleModerationItemVo>>> adminPage(ArticleModerationPageDto dto);

    /** 人工审核：通过/驳回 */
    Result<Boolean> audit(ArticleModerationAuditDto dto);
}
```

`ModerationAdminBizServiceImpl.java`：
```java
package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleContent;
import com.oyproj.domain.entity.ArticlePendingContent;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.dto.ArticleDao;
import com.oyproj.dto.ArticleTagDao;
import com.oyproj.mapper.ArticlePendingContentMapper;
import com.oyproj.service.ArticleChapterService;
import com.oyproj.service.ArticleIndexMessageService;
import com.oyproj.service.ModerationAdminBizService;
import com.oyproj.service.ModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文章人工审核后台业务实现（照抄评论审核的 audit → ModerationLog 模式）
 */
@Service
@RequiredArgsConstructor
public class ModerationAdminBizServiceImpl extends ArticleBaseBizService implements ModerationAdminBizService {

    private final ArticleDao articleDao;
    private final ArticleContentDao contentDao;
    private final ArticleTagDao articleTagDao;
    private final ArticlePendingContentMapper pendingContentMapper;
    private final ModerationService moderationService; // 复用 writeLog
    private final ArticleIndexMessageService indexMessageService;
    private final ArticleChapterService chapterService; // 待审编辑生效后重建章节目录

    @Override
    public Result<PageVo<List<ArticleModerationItemVo>>> adminPage(ArticleModerationPageDto dto) {
        int page = dto.getPage() == null ? 1 : dto.getPage();
        int size = dto.getSize() == null ? 10 : dto.getSize();
        List<ArticleModerationItemVo> items = new ArrayList<>();

        // 类型一：待审新文章（status=pending_review）
        List<Article> newArticles = articleDao.list(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "pending_review")
                .isNull(Article::getDeletedAt)
                .orderByDesc(Article::getCreatedAt));
        for (Article a : newArticles) {
            ArticleModerationItemVo vo = new ArticleModerationItemVo();
            vo.setArticleId(a.getId());
            vo.setKind("NEW");
            vo.setTitle(a.getTitle());
            vo.setAuthorId(a.getAuthorId());
            vo.setSummary(a.getSummary());
            vo.setReviewReason(a.getReviewReason());
            vo.setCreatedAt(a.getCreatedAt());
            items.add(vo);
        }

        // 类型二：已发布文章的待审编辑
        List<ArticlePendingContent> pendings = pendingContentMapper.selectList(null);
        for (ArticlePendingContent p : pendings) {
            Article a = articleDao.getById(p.getArticleId());
            if (a == null || a.getDeletedAt() != null) {
                continue;
            }
            ArticleModerationItemVo vo = new ArticleModerationItemVo();
            vo.setArticleId(p.getArticleId());
            vo.setKind("EDIT");
            vo.setTitle(a.getTitle());     // 当前对外展示的旧标题
            vo.setAuthorId(a.getAuthorId());
            vo.setSummary(a.getSummary()); // 当前对外摘要
            vo.setReviewReason(p.getReviewReason());
            vo.setCreatedAt(p.getUpdatedAt());
            vo.setPendingTitle(p.getPendingTitle());
            vo.setPendingSummary(p.getPendingSummary());
            items.add(vo);
        }

        items.sort((x, y) -> y.getCreatedAt().compareTo(x.getCreatedAt()));
        int total = items.size();
        int from = (page - 1) * size;
        int to = Math.min(from + size, total);
        List<ArticleModerationItemVo> pageItems =
                from >= total ? Collections.emptyList() : items.subList(from, to);
        int pages = total == 0 ? 0 : (total + size - 1) / size;
        return Result.ok(new PageVo<>(page, size, total, pages, pageItems));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> audit(ArticleModerationAuditDto dto) {
        String operatorId = getUserId();
        String reason = dto.getReason();
        Article article = articleDao.getById(dto.getArticleId());
        ArticlePendingContent pending = pendingContentMapper.selectById(dto.getArticleId());

        // 类型一：待审新文章
        if (article != null && "pending_review".equals(article.getStatus())) {
            if (Boolean.TRUE.equals(dto.getApprove())) {
                article.setStatus("published");
                article.setPublishAt(LocalDateTime.now());
                article.setReviewStatus("approved");
                article.setReviewReason(reason);
                article.setIsReviewed(1);
                article.setUpdateAt(LocalDateTime.now());
                articleDao.updateById(article);
                indexMessageService.sendIndexAfterCommit(article, listTagNames(article.getId()), MQOperation.CREATE);
                moderationService.writeLog(article.getId(), "manual_approve", reason, operatorId);
            } else {
                article.setStatus("rejected");
                article.setReviewStatus("rejected");
                article.setReviewReason(reason);
                article.setUpdateAt(LocalDateTime.now());
                articleDao.updateById(article);
                moderationService.writeLog(article.getId(), "manual_reject", reason, operatorId);
            }
            return Result.ok(true);
        }

        // 类型二：已发布文章的待审编辑
        if (pending != null) {
            if (Boolean.TRUE.equals(dto.getApprove())) {
                // 待审内容替换生效
                article.setTitle(pending.getPendingTitle());
                article.setSummary(pending.getPendingSummary());
                article.setReviewStatus("approved");
                article.setReviewReason(reason);
                article.setUpdateAt(LocalDateTime.now());
                articleDao.updateById(article);

                ArticleContent content = contentDao.getById(article.getId());
                if (content == null) {
                    content = ArticleContent.builder().articleId(article.getId()).build();
                }
                content.setContentMd(pending.getPendingContentMd());
                content.setContentHtml(pending.getPendingContentHtml());
                content.setWordsCount(pending.getPendingContentMd() != null ? pending.getPendingContentMd().length() : 0);
                content.setUpdatedAt(LocalDateTime.now());
                contentDao.saveOrUpdate(content);

                chapterService.rebuild(article.getId(), pending.getPendingContentMd()); // 章节目录随新内容重建
                pendingContentMapper.deleteById(pending.getArticleId());
                indexMessageService.sendIndexAfterCommit(article, listTagNames(article.getId()), MQOperation.UPDATE);
                moderationService.writeLog(article.getId(), "manual_approve", reason, operatorId);
            } else {
                // 驳回本次编辑：文章保持旧版不动
                pendingContentMapper.deleteById(pending.getArticleId());
                article.setReviewStatus("rejected");
                article.setReviewReason(reason);
                article.setUpdateAt(LocalDateTime.now());
                articleDao.updateById(article);
                moderationService.writeLog(article.getId(), "manual_reject", reason, operatorId);
            }
            return Result.ok(true);
        }

        return Result.error("审核项不存在");
    }

    /** 文章标签名列表（索引消息用） */
    private List<String> listTagNames(String articleId) {
        return articleTagDao.listTagNamesByArticleIds(Collections.singletonList(articleId))
                .getOrDefault(articleId, Collections.emptyList());
    }
}
```

`ModerationAdminController.java`：
```java
package com.oyproj.controller;

import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.ModerationAdminBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文章审核后台控制器（仅供 admin-service 通过 Feign 调用，直接 HTTP 访问需 ADMIN 角色）
 */
@Tag(name = "文章审核后台控制器", description = "文章待审队列与人工审核")
@RestController
@RequestMapping("/article/moderation/admin")
@RequiredArgsConstructor
public class ModerationAdminController {

    private final ModerationAdminBizService biz;

    @PostMapping("/page")
    @RequirePermission("admin:moderation:read")
    @Operation(summary = "文章待审队列（新文章+待审编辑合并）")
    public Result<PageVo<List<ArticleModerationItemVo>>> adminPage(@RequestBody ArticleModerationPageDto dto) {
        return biz.adminPage(dto);
    }

    @PostMapping("/audit")
    @RequirePermission("admin:moderation:write")
    @Operation(summary = "人工审核文章（通过/驳回）")
    public Result<Boolean> audit(@RequestBody ArticleModerationAuditDto dto) {
        return biz.audit(dto);
    }
}
```

- [ ] **Step 6: 运行确认通过**

Run: 同 Step 2 命令
Expected: 6 个测试全 PASS

- [ ] **Step 7: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/dto/ArticleModerationPageDto.java oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/dto/ArticleModerationAuditDto.java oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/vo/ArticleModerationItemVo.java oy-blog-service/article-service/src/main/java/com/oyproj/service/ArticleChapterService.java oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleBizServiceImpl.java oy-blog-service/article-service/src/main/java/com/oyproj/service/ModerationAdminBizService.java oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ModerationAdminBizServiceImpl.java oy-blog-service/article-service/src/main/java/com/oyproj/controller/ModerationAdminController.java && git commit -m "feat: 文章人工审核队列（待审列表+通过/驳回+待生效替换）+ 抽出 ArticleChapterService"
```

---

## Task 11: Feign 客户端 + 降级工厂（service-api）

**Files:**
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/service-api/src/main/java/com/oyproj/api/article/client/AdminModerationClient.java`
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/service-api/src/main/java/com/oyproj/api/article/client/fallback/AdminModerationClientFallbackFactory.java`

**Interfaces:**
- Consumes: Task 10 的两个端点；`AdminFeignConfig`（已有）
- Produces（Task 12 依赖）: `AdminModerationClient.adminModerationPage(dto)` / `auditArticleModeration(dto)`

说明：Feign 接口无独立测试（照抄 AdminArticleClient 模式），实现+编译验证+提交。

- [ ] **Step 1: 实现客户端**

```java
package com.oyproj.api.article.client;

import com.oyproj.api.article.client.fallback.AdminModerationClientFallbackFactory;
import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.api.config.AdminFeignConfig;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 文章审核管理接口 Feign 客户端（admin-service 使用）
 */
@FeignClient(value = "article-service", contextId = "admin-moderation-client",
        configuration = AdminFeignConfig.class,
        fallbackFactory = AdminModerationClientFallbackFactory.class)
public interface AdminModerationClient {

    @PostMapping("/article/moderation/admin/page")
    Result<PageVo<List<ArticleModerationItemVo>>> adminModerationPage(@RequestBody ArticleModerationPageDto dto);

    @PostMapping("/article/moderation/admin/audit")
    Result<Boolean> auditArticleModeration(@RequestBody ArticleModerationAuditDto dto);
}
```

- [ ] **Step 2: 实现降级工厂**（照抄 AdminArticleClientFallbackFactory 模式）

```java
package com.oyproj.api.article.client.fallback;

import com.oyproj.api.article.client.AdminModerationClient;
import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.utils.I18nUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class AdminModerationClientFallbackFactory implements FallbackFactory<AdminModerationClient> {

    @Override
    public AdminModerationClient create(Throwable cause) {
        return new AdminModerationClient() {
            @Override
            public Result<PageVo<List<ArticleModerationItemVo>>> adminModerationPage(ArticleModerationPageDto dto) {
                log.warn("文章服务审核接口调用失败(待审队列)，错误: {}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<Boolean> auditArticleModeration(ArticleModerationAuditDto dto) {
                log.warn("文章服务审核接口调用失败(审核操作)，错误: {}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }
        };
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/service-api -am compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add oy-blog-service/service-api/src/main/java/com/oyproj/api/article/client/AdminModerationClient.java oy-blog-service/service-api/src/main/java/com/oyproj/api/article/client/fallback/AdminModerationClientFallbackFactory.java && git commit -m "feat: 文章审核管理 Feign 客户端+降级工厂"
```

---

## Task 12: admin-service 审核 BFF

**Files:**
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/admin-service/src/main/java/com/oyproj/service/AdminModerationBizService.java`
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/admin-service/src/main/java/com/oyproj/service/impl/AdminModerationBizServiceImpl.java`
- Create: `/g/JavaWorkSpace/oy-blog-dev1/oy-blog-service/admin-service/src/main/java/com/oyproj/controller/AdminModerationController.java`

**Interfaces:**
- Consumes: Task 11 `AdminModerationClient`
- Produces: 管理前端接口 `POST /admin/moderation/page`、`POST /admin/moderation/audit`（带 `@RequirePermission("admin:moderation:read"/"admin:moderation:write")`）

说明：BFF 纯透传（照抄 AdminArticleBizServiceImpl），实现+编译验证+提交。

- [ ] **Step 1: 实现接口与透传**

```java
package com.oyproj.service;

import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;

/**
 * 管理端文章审核 BFF 业务
 */
public interface AdminModerationBizService {
    Result<PageVo<List<ArticleModerationItemVo>>> page(ArticleModerationPageDto dto);
    Result<Boolean> audit(ArticleModerationAuditDto dto);
}
```

```java
package com.oyproj.service.impl;

import com.oyproj.api.article.client.AdminModerationClient;
import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.base.AdminBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.service.AdminModerationBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端文章审核 BFF 实现：全部直接透传 Feign 结果
 */
@Service
@RequiredArgsConstructor
public class AdminModerationBizServiceImpl extends AdminBizBase implements AdminModerationBizService {

    private final AdminModerationClient client;

    @Override
    public Result<PageVo<List<ArticleModerationItemVo>>> page(ArticleModerationPageDto dto) {
        return client.adminModerationPage(dto);
    }

    @Override
    public Result<Boolean> audit(ArticleModerationAuditDto dto) {
        return client.auditArticleModeration(dto);
    }
}
```

```java
package com.oyproj.controller;

import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.AdminModerationBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端文章审核控制器（BFF 入口，管理前端只调这里）
 */
@Tag(name = "管理端文章审核控制器", description = "文章待审队列与人工审核")
@RestController
@RequestMapping("/admin/moderation")
@RequiredArgsConstructor
public class AdminModerationController {

    private final AdminModerationBizService biz;

    @PostMapping("/page")
    @RequirePermission("admin:moderation:read")
    @Operation(summary = "文章待审队列")
    public Result<PageVo<List<ArticleModerationItemVo>>> page(@RequestBody ArticleModerationPageDto dto) {
        return biz.page(dto);
    }

    @PostMapping("/audit")
    @RequirePermission("admin:moderation:write")
    @Operation(summary = "人工审核文章（通过/驳回）")
    public Result<Boolean> audit(@RequestBody ArticleModerationAuditDto dto) {
        return biz.audit(dto);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/admin-service -am compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add oy-blog-service/admin-service/src/main/java/com/oyproj/service/AdminModerationBizService.java oy-blog-service/admin-service/src/main/java/com/oyproj/service/impl/AdminModerationBizServiceImpl.java oy-blog-service/admin-service/src/main/java/com/oyproj/controller/AdminModerationController.java && git commit -m "feat: 管理端文章审核 BFF（待审队列+审核操作）"
```

---

## Task 13: 联调验收 + 文档收尾

**Files:**
- Modify: `/g/JavaWorkSpace/oy-blog-dev1/doc/`（新增 `doc/article-moderation-acceptance.md` 验收记录）

- [ ] **Step 1: BlogAgent 全量测试**

Run: `cd /g/agentWorkplace/BlogAgent && MOCK_LLM=1 /d/tool1/anancoda/envs/ai-agent/python.exe -m pytest -v`
Expected: 全量通过（50 + 15 = 65）

- [ ] **Step 2: BlogAgent MOCK 三态冒烟**

```bash
cd /g/agentWorkplace/BlogAgent && MOCK_LLM=1 /d/tool1/anancoda/envs/ai-agent/python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8001
# 另开终端：
curl -s -X POST http://localhost:8001/moderate/article -H "Content-Type: application/json" -d '{"articleId":"t1","title":"标题","summary":"","content":"正常的技术文章"}'
# 期望 {"verdict":"approve","reason":"【MOCK】联调放行"}
curl -s -X POST http://localhost:8001/moderate/article -H "Content-Type: application/json" -d '{"articleId":"t2","title":"标题","summary":"","content":"违规内容"}'
# 期望 {"verdict":"reject",...}
curl -s -X POST http://localhost:8001/moderate/article -H "Content-Type: application/json" -d '{"articleId":"t3","title":"标题","summary":"","content":"有些歧义"}'
# 期望 {"verdict":"manual",...}
curl -s -X POST http://localhost:8001/moderate/article -H "Content-Type: application/json" -d '{"articleId":"","title":"","content":""}'
# 期望 422
```

- [ ] **Step 3: Java 全模块测试**

Run: `JAVA_HOME=/d/DevelopKit/jdk-21.0.8 mvn -f /g/JavaWorkSpace/oy-blog-dev1/pom.xml -pl oy-blog-service/article-service -am test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 全部既有 + 新增测试通过（新测试类共 4 个：ModerationPropertiesTest / ModerationServiceTest / ArticleIndexMessageServiceTest / ArticleBizPublishModerationTest / ArticleReadVisibilityTest / ModerationAdminBizServiceImplTest）

- [ ] **Step 4: 真实 DeepSeek 三态验证**（需真实 DEEPSEEK_API_KEY）

把 `.env` 去掉 `MOCK_LLM`（或 `MOCK_LLM=0`）重启 BlogAgent，用三篇样例文章 curl：
- 正常技术文章 → 期望 `approve`
- 明确广告/违法样例（如"加微信 xxx 购买彩票"）→ 期望 `reject`
- 边界样例（如讨论敏感话题的科普文章）→ 期望 `manual` 或 `approve`，观察 reason 合理性
记录实际结果进验收文档。

- [ ] **Step 5: 全链路验收（需本地起 Java 服务 + 中间件，若环境不具备则留待服务器部署后执行）**

1. 确认 dev 库已执行 `doc/sql/article_moderation_migration.sql`
2. 起 article-service（本地或服务器），Nacos/yml 配置 `oy-blog.article.moderation.base-url` 指向 BlogAgent（服务器上为 `http://oy-blog-python-agent:8001`，同网段直连；Nacos 配置变更需注意 jar 内烘焙配置坑，见部署文档）
3. 普通用户发布三篇样例 → 分别验证：published 可见 / rejected 且列表有驳回原因 / pending_review 不可公开读但作者列表可见
4. 管理员调 `POST /admin/moderation/page` 见两篇待审 → audit 通过/驳回 → 验证状态流转 + ES 检索（approve 后能搜到，驳回的搜不到）
5. 已发布文章编辑歧义 → 旧版仍展示、`article_pending_content` 有行 → 人工通过后新内容生效
6. 关闭 BlogAgent 再发布 → 转人工队列（fail-closed 验证）

- [ ] **Step 6: 验收文档 + 提交**

写 `doc/article-moderation-acceptance.md`（参照 `doc/admin-service-phase1-acceptance.md` 格式）：验收项清单（对照 spec §11）、每项实测结果、真实 DeepSeek 三态样例、遗留事项（管理前端审核页面待管理前端计划落地；作者创作中心"审核中/已驳回"展示需前端配合 map 里的 verdict/reason）。

```bash
cd /g/JavaWorkSpace/oy-blog-dev1 && git add doc/article-moderation-acceptance.md && git commit -m "docs: 文章 AI 审核验收记录"
```

---

## 自审记录（plan 写完后的核对）

**Spec 覆盖检查：**
- §4 状态机 → Task 8（publish 分流）+ Task 10（人工流转）
- §6.1 发布新文章 → Task 8 三个分支
- §6.2 编辑先审后生效 → Task 8 四个测试
- §6.3 人工审核队列 → Task 10
- §7 fail-closed → Task 5 测试（serverError/connectionRefused/unknownVerdict）
- §8 端点协议 → Task 1/2
- §9.1 Python → Task 1/2；§9.2 article-service → Task 4-9；§9.3 admin-service → Task 10-12；§9.4 配置 → Task 4；§9.5 迁移 → Task 3
- §10 测试方案 → 各任务测试步骤
- §12 风险"作者读草稿路径" → Task 9 的 canView（作者本人可见），验收时验证创作中心链路

**遗留事项（有意不在本计划内，记录备查）：**
- 管理前端审核页面属于 `docs/superpowers/plans/2026-08-26-oy-blog-admin-frontend.md` 计划范畴（后端端点已在 Task 10-12 就位，前端可直接对接）
- 作者侧前端"审核中/已驳回"提示依赖 publish 返回 map 的新键（`verdict`/`reason`，前端小改动，另行处理）
- `ArticleChapterService` 迁移保留了原 `protectCodeBlocks` 里 ThreadLocal 占位符的旧写法（原就无 restore 调用，属历史死代码）——迁移保持行为一致，不在本计划内清理
