<p align="center">
  <img src="https://api.iconify.design/mdi:server-network.svg?color=%236DB33F" alt="OY Blog Server Logo" width="120" height="120">
  <h1 align="center">OY Blog Server</h1>
  <p align="center">基于 Spring Cloud 微服务架构的现代化个人博客后端系统</p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Spring%20Cloud-2024.0-6DB33F?style=flat-square&logo=spring&logoColor=white" alt="Spring Cloud">
  <img src="https://img.shields.io/badge/MyBatis%20Plus-3.5.7-DC382D?style=flat-square&logo=mybatis&logoColor=white" alt="MyBatis Plus">
  <img src="https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql&logoColor=white" alt="MySQL">
  <img src="https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white" alt="Redis">
  <img src="https://img.shields.io/badge/RabbitMQ-FF6600?style=flat-square&logo=rabbitmq&logoColor=white" alt="RabbitMQ">
  <img src="https://img.shields.io/badge/Elasticsearch-8.17-005571?style=flat-square&logo=elasticsearch&logoColor=white" alt="Elasticsearch">
</p>

<br>

## 项目介绍

**OY Blog Server** 是 OY Blog 的后端部分，基于 **Spring Boot 3.4 + Spring Cloud 微服务架构**开发，采用 Maven 多模块组织。系统以 **API 网关**为统一入口，按业务拆分为 用户、文章、AI 对话、文件、搜索、管理 六个微服务，配合 MySQL、Redis、RabbitMQ、Elasticsearch、MinIO 等中间件，为前端提供认证、内容、互动、搜索、管理的一站式能力。

除常规的博客功能（文章、评论、搜索、标签、系列）外，后端还实现了 **AI 对话代理**（SSE 流式输出、深度思考、会话管理）、**内容审核流程**（文章 / 评论双线审核 + 管理端聚合）、**异步索引同步**（RabbitMQ 消息 + 定时对账）等进阶能力，并通过 Nacos 注册发现、Sentinel 熔断、统一日志与异常体系保障服务稳定性。

---

## 功能特性

<table align="center">
    <tr>
        <td width="50%">
            <h3>
                <img src="https://api.iconify.design/mdi:shield-lock-outline.svg?color=%23000000" width="20" height="20" valign="middle">
                网关与安全
            </h3>
            <ul>
                <li><b>统一入口</b>: Spring Cloud Gateway 前缀路由 + Nacos 负载均衡</li>
                <li><b>全局认证</b>: 网关过滤器校验 JWT，注入用户身份请求头</li>
                <li><b>双 Token</b>: Access Token + Refresh Token 自动续期</li>
                <li><b>白名单</b>: 登录 / 注册 / 公开文章 / 搜索等路径免认证放行</li>
                <li><b>游客模式</b>: 未登录自动签发 GUEST_ID Cookie，保持匿名会话</li>
            </ul>
        </td>
        <td width="50%">
            <h3>
                <img src="https://api.iconify.design/mdi:account-multiple-outline.svg?color=%23000000" width="20" height="20" valign="middle">
                用户中心
            </h3>
            <ul>
                <li><b>注册登录</b>: 邮箱验证码 + 图形验证码、密码找回</li>
                <li><b>角色权限</b>: READER / GUEST / ADMIN 三级角色（RBAC）</li>
                <li><b>用户资料</b>: 昵称、头像、简介维护</li>
                <li><b>公开主页</b>: 用户公开信息页 + 阅读历史</li>
                <li><b>行为策略</b>: 策略模式区分普通用户与游客行为</li>
            </ul>
        </td>
    </tr>
    <tr>
        <td>
            <h3>
                <img src="https://api.iconify.design/mdi:file-document-edit-outline.svg?color=%23000000" width="20" height="20" valign="middle">
                内容创作
            </h3>
            <ul>
                <li><b>文章管理</b>: 草稿 / 发布 / 下架全生命周期 + 修订历史</li>
                <li><b>Markdown 渲染</b>: flexmark 服务端渲染（代码高亮 / 图表）</li>
                <li><b>内容组织</b>: 标签 / 系列 / 章节三级组织体系</li>
                <li><b>封面图片</b>: 文章封面与正文图片上传</li>
                <li><b>审核流程</b>: 提交审核 → 通过 / 拒绝（原因回显）</li>
            </ul>
        </td>
        <td>
            <h3>
                <img src="https://api.iconify.design/mdi:forum-outline.svg?color=%23000000" width="20" height="20" valign="middle">
                互动评论
            </h3>
            <ul>
                <li><b>多级评论</b>: 评论 + 回复无限级嵌套</li>
                <li><b>互动表态</b>: 文章 / 评论点赞、文章收藏</li>
                <li><b>阅读统计</b>: 浏览计数、GitHub 风格贡献热力图数据</li>
                <li><b>评论审核</b>: 待审评论过滤，审核通过后方可见</li>
                <li><b>数据沉淀</b>: 点赞 / 收藏 / 历史独立表 + Redis 缓存</li>
            </ul>
        </td>
    </tr>
    <tr>
        <td>
            <h3>
                <img src="https://api.iconify.design/mdi:robot-outline.svg?color=%23000000" width="20" height="20" valign="middle">
                AI 对话
            </h3>
            <ul>
                <li><b>流式输出</b>: SSE 逐字输出，可随时中断（网关放宽超时、关闭缓冲）</li>
                <li><b>深度思考</b>: Deep Thinking 推理过程透传</li>
                <li><b>会话管理</b>: 历史会话分组，支持重命名 / 删除</li>
                <li><b>反馈闭环</b>: 消息点赞 / 点踩反馈</li>
                <li><b>游客可聊</b>: 未登录也可体验，写操作按 x-user-id 做 owner 校验</li>
                <li><b>侧车架构</b>: 对接 Python Agent 服务（内置联调桩 agent_stub.py）</li>
            </ul>
        </td>
        <td>
            <h3>
                <img src="https://api.iconify.design/mdi:file-search-outline.svg?color=%23000000" width="20" height="20" valign="middle">
                搜索与文件
            </h3>
            <ul>
                <li><b>全文搜索</b>: Elasticsearch 文章检索 + 高亮</li>
                <li><b>异步索引</b>: RabbitMQ 消息驱动索引增删改</li>
                <li><b>数据对账</b>: 定时任务全量对账 + MQ 失败重试补偿</li>
                <li><b>对象存储</b>: MinIO 统一文件上传（模板方法 + 策略模式）</li>
            </ul>
        </td>
    </tr>
    <tr>
        <td>
            <h3>
                <img src="https://api.iconify.design/mdi:shield-check-outline.svg?color=%23000000" width="20" height="20" valign="middle">
                审核与管理
            </h3>
            <ul>
                <li><b>审核聚合</b>: 文章 / 评论审核状态流转 + 操作日志</li>
                <li><b>用户管理</b>: 列表查询、封禁（会话即时失效）</li>
                <li><b>统计仪表盘</b>: 总览指标、近 30 天趋势、热门 TOP10</li>
                <li><b>权限隔离</b>: 网关白名单 + 服务内 @RequirePermission 双保险</li>
            </ul>
        </td>
        <td>
            <h3>
                <img src="https://api.iconify.design/mdi:code-tags-check.svg?color=%23000000" width="20" height="20" valign="middle">
                工程特性
            </h3>
            <ul>
                <li><b>统一响应</b>: Result 统一结构 + 全局异常处理 + 国际化</li>
                <li><b>日志体系</b>: @Log AOP 操作日志 + Logback 分级归档</li>
                <li><b>服务治理</b>: Nacos 注册 / 配置、Sentinel 熔断、Feign 调用</li>
                <li><b>数据访问</b>: MyBatis Plus 分页拦截器 + XML 自定义 SQL</li>
                <li><b>文档沉淀</b>: SpringDoc OpenAPI、doc/ 技术文档、SQL 迁移脚本</li>
            </ul>
        </td>
    </tr>
</table>

---

## 技术栈

<table align="center">
    <tr>
        <td align="center" width="250">
            <img src="https://api.iconify.design/mdi:monitor-dashboard.svg?color=%23000000" width="20" height="20" valign="middle">
            <b>核心框架</b>
        </td>
        <td align="center" width="250">
            <img src="https://api.iconify.design/mdi:database-cog-outline.svg?color=%23000000" width="20" height="20" valign="middle">
            <b>数据与中间件</b>
        </td>
        <td align="center" width="250">
            <img src="https://api.iconify.design/mdi:hammer-wrench.svg?color=%23000000" width="20" height="20" valign="middle">
            <b>工程与工具</b>
        </td>
    </tr>
    <tr>
        <td valign="top">
            <ul>
                <li><b>Language</b>: Java 21</li>
                <li><b>Framework</b>: Spring Boot 3.4.11</li>
                <li><b>Gateway</b>: Spring Cloud Gateway 2024.0.0</li>
                <li><b>Governance</b>: Spring Cloud Alibaba (Nacos + Sentinel)</li>
                <li><b>Security</b>: Spring Security + JWT (jjwt)</li>
            </ul>
        </td>
        <td valign="top">
            <ul>
                <li><b>ORM</b>: MyBatis Plus 3.5.7</li>
                <li><b>Database</b>: MySQL 8.0</li>
                <li><b>Cache</b>: Redis (Lettuce)</li>
                <li><b>MQ</b>: RabbitMQ (Spring AMQP)</li>
                <li><b>Search</b>: Elasticsearch 8.17.10</li>
                <li><b>Storage</b>: MinIO 8.5.7</li>
            </ul>
        </td>
        <td valign="top">
            <ul>
                <li><b>Build</b>: Maven 多模块</li>
                <li><b>RPC</b>: OpenFeign + LoadBalancer</li>
                <li><b>Render</b>: Flexmark（服务端 Markdown 渲染）</li>
                <li><b>Doc</b>: SpringDoc OpenAPI (Swagger UI)</li>
                <li><b>Log</b>: Logback + 自定义日志框架</li>
                <li><b>Deploy</b>: Docker Compose + 一键部署脚本</li>
            </ul>
        </td>
    </tr>
</table>

---

## 系统架构

客户端请求统一经过 API 网关（默认 `localhost:8080`），网关校验 JWT、按 `/api/{service}/**` 前缀经 Nacos 服务发现路由至各业务服务：

```
客户端 (Vue 3 SPA)
  │  /api/**（Nginx / Vite 同源代理）
  ▼
oy-blog-gateway (8080) ── JWT 统一认证 · 白名单放行 · 前缀路由 · 游客 Cookie
  │  lb://{service}（Nacos 服务发现）
  ├─ user-service    (8093)  登录注册 / 用户资料 / 公开主页
  ├─ article-service (8091)  文章 / 评论 / 点赞收藏 / 统计 ──► RabbitMQ 索引消息
  ├─ agent-service   (8095)  AI 对话（SSE）────────► Python Agent (8001, 内网直连)
  ├─ file-service    (8097)  图片上传 ──────────────► MinIO
  ├─ search-service  (8099)  ES 全文搜索 ◄── RabbitMQ 索引消息
  └─ admin-service   (8096)  管理端 BFF（审核 / 用户 / 统计）

同步调用: OpenFeign + Sentinel 熔断        异步链路: RabbitMQ（失败重试 + 定时对账）
注册与配置: Nacos                           基础设施: MySQL / Redis / RabbitMQ / Elasticsearch / MinIO
```

* 身份认证采用 **JWT**（Access Token + Refresh Token 自动续期），网关统一校验后注入 `x-user-id` 等身份头；未登录请求自动降级为**游客**（GUEST_ID Cookie）
* 语言切换通过请求头 `lang: zh | en` 透传后端（响应信息国际化）
* AI 对话走 **SSE**（`/api/agent-service/chat/stream`），网关侧已按路由放宽响应超时、Nginx 侧关闭缓冲以确保流式输出
* 服务间同步调用走 **OpenFeign**（Sentinel 熔断兜底）；文章发布 / 修改通过 **RabbitMQ** 异步通知 search-service 维护 ES 索引

---

## 目录结构

项目采用 Maven 多模块组织，公共能力下沉、业务服务按领域拆分：

| 目录名称                       | 职责说明                                           | 关键内容                                                                                                    |
| :----------------------------- | :------------------------------------------------- | :---------------------------------------------------------------------------------------------------------- |
| **oy-blog-common**       | **公共模块**统一响应、异常、认证、缓存、工具 | `base/`(Result/ResultCode), `exception/`(GlobalExceptionHandler), `security/`(SecurityConfig/AuthFilter), `mq/`(RabbitMQ 配置), `service/`(CommonCache Redis), `utils/`(JwtUtil/I18nUtils) |
| **oy-blog-gateway**      | **API 网关**统一入口，端口 8080               | `filter/AuthenticationFilter`(全局认证), `utils/GuestUtil`(游客 Cookie), `properties/AuthProperties`(白名单) |
| **oy-blog-service/service-api** | **服务契约**Feign 接口集中定义                | `api/user` / `api/article` / `api/file` 客户端, `config/FeignConfig`                                        |
| **user-service**         | **用户服务** (8093)                             | 登录注册 / 邮箱验证 / 资料维护, `starategy/`(用户行为策略), `dao/`(XML 统计查询)                             |
| **article-service**      | **文章服务** (8091)                             | 文章 / 评论 / 点赞 / 收藏 / 标签 / 系列, `MarkdownRenderer`(服务端渲染), MQ 生产者, `scheduler/`(卡审扫描/消息重试) |
| **agent-service**        | **AI 对话服务** (8095)                          | SSE 流式对话, 会话管理, 点赞点踩反馈, 对接 Python Agent                                                        |
| **file-service**         | **文件服务** (8097)                             | 上传抽象基类 + `service/impl/strategy/`(MinIO 存储策略)                                                      |
| **search-service**       | **搜索服务** (8099)                             | ES 搜索 + 高亮, RabbitMQ 消费者, `IndexReconciler`(索引对账)                                                 |
| **admin-service**        | **管理端 BFF** (8096)                           | 审核聚合, 用户管理 / 封禁, 统计仪表盘                                                                         |
| **deploy**               | **部署层**生产部署脚本与配置                    | `deploy.sh`(一键部署), `docker-compose.yml`, `docker/Dockerfile`, `docker-compose.env.example`               |
| **doc** / **docs** | **文档**技术方案与机制设计                      | 框架总结 / 部署手册 / 各机制设计文档, `doc/sql/`(数据库迁移脚本，需按顺序执行)                                |
| **scripts**              | **辅助脚本**联调与测试工具                      | `agent_stub.py`(Python Agent 联调桩), 文章上传脚本                                                           |

---

## 快速开始

### 环境准备

* **JDK**: 21（必需，系统默认 JDK 20 会报"不支持发行版本 21"，注意 `JAVA_HOME` 指向 21）
* **Maven**: 3.8+
* **基础设施**: MySQL 8 / Redis / RabbitMQ / Elasticsearch / MinIO，并先启动 **Nacos** 注册中心（所有地址统一通过 `.env` 的 `*_HOST` 占位符注入，见 [.env.example](.env.example)）
* **（可选）AI 对话联调**: Python 3.10+，`pip install fastapi uvicorn`

### 安装步骤

#### 1. 克隆项目

```bash
git clone https://github.com/oywq3000/oy-blog.git
cd oy-blog
```

#### 2. 配置环境变量

```bash
cp .env.example .env   # Windows: copy .env.example .env
```

编辑 `.env`，把 `MYSQL_HOST` / `REDIS_HOST` / `NACOS_HOST` 等改成你的中间件地址（该文件已被 `.gitignore` 忽略）。

#### 3. 编译打包

```bash
mvn clean package -DskipTests
```

产物输出到各模块 `target/` 目录（如 `oy-blog-gateway/target/oy-blog-gateway-1.0-SNAPSHOT.jar`）。

#### 4. 启动服务

先启动中间件（含 Nacos），再依次启动 gateway 与 6 个业务服务：

```bash
java -jar oy-blog-gateway/target/oy-blog-gateway-1.0-SNAPSHOT.jar
# 依次启动 user / article / agent / file / search / admin 六个 service（同理）
```

> **IDEA 用户**：只需配置一次根目录 `.env`（Run Configurations 模板），之后直接 Run 各模块的 `*Application` 启动类即可，详见 [local-dev-environment.md](doc/local-dev-environment.md)。
>
> **AI 对话联调**：启动 agent-service 前，先运行 `python scripts/agent_stub.py`（监听 `8001`，`AGENT_PYTHON_URL` 默认指向它），替换为真实 Python Agent 时只需改这个变量。

启动后前端 `/api` 请求即可代理到网关 `:8080`，网关启动日志中可看到各服务注册到 Nacos。

#### 5. 运行测试

```bash
mvn test                # 全量运行
mvn test -pl oy-blog-service/article-service -am          # 单模块（连带依赖模块）
mvn test -pl oy-blog-service/article-service -am -Dtest=ArticleBizServiceTest -Dsurefire.failIfNoSpecifiedTests=false  # 指定测试类
```

## 开源协议

本项目采用 [MIT License](LICENSE) 开源协议。