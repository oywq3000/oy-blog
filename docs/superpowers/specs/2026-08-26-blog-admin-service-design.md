# oy-blog 博客后台管理服务（admin-service）设计文档

> 日期: 2026-08-26 | 状态: 已与作者逐节确认 | 下一步: writing-plans 制定实现计划

---

## 一、背景与目标

为 oy-blog 微服务家族新增一个 **admin-service（博客后台管理服务）**，作为博客管理端的统一入口，配套一个独立的管理前端项目 `oy-blog-admin`。

**定位取舍（已确认）**：
- 使用者：**主要自己用，但预留多管理员扩展**（不写死权限模型）
- 取舍标准：**两者兼顾**——核心功能（写文章、审评论）按真实可用标准做好，其余模块作为技术探索逐步补全
- 架构方案：**BFF 聚合 + 管理自有域**（方案 A）。admin-service 通过 Feign 复用下游服务能力，只自建"管理特有"数据；不做完全独立管理域（方案 B，破坏单一数据源），不做管理功能大迁移（方案 C，作为远期演进方向记录）

## 二、现状盘点（2026-08-26 核实）

| 服务 | 现有能力 | 缺口 |
|------|------|------|
| gateway (8080) | 全局认证过滤器：解析 JWT、注入 X-User-Id/X-User-Type；白名单放行 | 无 ADMIN 角色拦截；无 admin-service 路由 |
| article-service | 作者侧：draft / publish / delete / stats/me / heatmap；用户侧：read、interaction、comment（add/reply/reaction）；moderation_log 表已存在 | 评论审核接口（待审/通过/拒绝/删除/置顶）；管理视角文章列表（全状态筛选）；标签/系列 CRUD（仅读接口） |
| user-service | auth/login（BCrypt + JWT + Redis 会话）、profile、email 验证；Role/Permission/UserRole/RolePermission/UserPermission 表已存在；ADMIN 角色已预留 | 用户管理接口（分页列表/封禁/解封/分配收回角色） |
| file-service | upload / comUpload（MinIO） | 文件列表 / 删除接口 |
| service-api | Feign: ArticleControllerClient、ArticleIndexClient、UserClient、FileUploadClient | admin 相关 Feign 客户端 |
| search-service / agent-service | 不涉及本设计 | - |

可复用的既有基建：`Result<T>` + `GlobalExceptionHandler` + i18n（messages/ValidationMessages 中英双语）、`@Log` + `OpLog` + `LogAspect`、UUID32 主键 + `@TableLogic` 逻辑删除、MP 分页（PaginationInnerInterceptor）、Feign + Sentinel FallbackFactory、RabbitMQ（article.index 链路已跑通）、CommonCache（Redis）、网关白名单机制、`/internal/**` 内部接口先例。

## 三、总体架构

```
┌─ 管理前端 oy-blog-admin（Vue 3，独立项目） ─┐
        │ /admin-service/**（网关校验角色 = ADMIN）
        ▼
┌─ admin-service（新增，端口 8095，Nacos 注册）─────┐
│  文章管理 ──Feign──▶ article-service（复用+补接口）    │
│  评论审核 ──Feign──▶ article-service（补审核接口）    │
│  用户管理 ──Feign──▶ user-service（补管理接口）       │
│  媒体库   ──Feign──▶ file-service（补列表/删除）     │
│  统计看板 ──只读直连同库统计表 + Redis 计数器         │
│  站点设置 / 操作日志 / 公告 / 通知 ── 自有库表 ★      │
└────────────────────────────────────────────────┘
```

admin-service 内部沿用项目现有轻量 DDD 分层：controller → service（接口+impl）→ dao → mapper → domain（entity/dto/vo），继承链 `AdminBizBase extends BaseBiz`，自带 i18n 资源文件。不发明新分层。

### 模块职责表

| 模块 | 数据/能力归属 | 实现方式 |
|------|------|------|
| 文章管理 | article-service | Feign 转发 + 下游补管理列表/标签系列接口 |
| 评论审核 | article-service | Feign + 下游补审核接口 |
| 用户管理 | user-service | Feign + 下游补管理接口 |
| 媒体库 | file-service | Feign + 下游补列表/删除接口 |
| 统计看板 | 各服务统计表（同库只读） | admin-service 直连聚合 |
| 站点设置 | admin-service 自有 | site_config 表 |
| 操作日志 | admin-service 自有 | @Log + OpLog 落库 |
| 公告/通知 | admin-service 自有 | announcement + notification 表 |

## 四、权限模型（三层纵深，预留多管理员）

```
① 网关层：/admin-service/** 校验角色 = ADMIN，非 ADMIN → 403
② 服务层：admin-service 自身 Security 规则（防绕过网关直连）：
   - /public/**：不要求 ADMIN——公告读取对任意认证身份（含 GUEST）开放；通知读取按 X-User-Id 过滤只能读自己的
   - 其余路径：要求 ADMIN
③ 注解层：@RequirePermission("admin:xxx") 挂在 Controller 方法上
```

- **@RequirePermission 现在就要挂上**，但 MVP 实现只做"ADMIN 角色"校验：权限码常量集中定义、注解机制先跑通，将来接入 user-service 现有 `Permission/RolePermission` 表做细粒度校验时 Controller 零改动。
- 管理员角色分配：用户管理模块经 Feign 操作 user-service 的 `user_role` 表（授予/收回 ADMIN 角色）。
- 登录链路：管理前端**直接**调用 user-service 现有 `/auth/login`，admin-service 不转发登录；后续管理请求走 `/admin-service/**`。

## 五、数据库设计（admin-service 自有表，同一个 oyblog 库）

| 表名 | 用途 | 关键字段 |
|------|------|------|
| `site_config` | 站点设置 | config_key、config_value、group_name、description |
| `op_log` | 操作日志 | operator_id、operator_name、action、func、ip、params、result、created_at（对齐现有 OpLog 常量语义；现状核实：LogAspect 仅打印日志不落库，本表由 admin-service 新建并承担落库） |
| `announcement` | 站内公告 | title、content、status、pinned、published_at、created_by |
| `notification` | 评论回复通知 | receiver_id、type、comment_id、article_id、content、is_read、created_at |

约定：UUID32 主键（`UUIDUtils.getId()`）、`@TableLogic` 逻辑删除、MP 自动填充时间字段。

## 六、核心数据流

### ① 登录（不经过 admin-service）
```
管理前端 → 网关 /user-service/auth/login → user-service 校验(BCrypt) → JWT(含 ADMIN 角色) → Redis 存会话
之后每个请求：网关解析 JWT → 角色 = ADMIN 才放行 /admin-service/**
```

### ② 写文章（BFF 转发，现有链路不动）
```
前端 → admin-service /admin/article/publish → Feign article-service /article/publish(X-Service-Call)
  → 落库(事务) → afterCommit 异步发 MQ → search-service 更新 ES
```

### ③ 评论审核
```
前端 → admin-service /admin/comment/audit → Feign article-service → 评论状态变更 + 写 moderation_log
```

### ④ 评论回复通知（新异步链路）
```
博客前端回复评论 → article-service 落库后发 MQ(新增 comment.notify 路由)
  → admin-service 消费 → 写 notification 表
用户读自己的通知：GET /admin-service/public/notification/**（网关白名单，仅能读自己的，READER 可用）
```
依赖：oy-blog-front 增加"消息中心"界面（列入二期）；MVP 先在后台能查看通知数据。

### ⑤ 统计看板
```
admin-service 只读直连同库统计表(article_stats / article_log 等) + Redis 计数器(CommonCache)
  → 聚合：访问趋势、阅读/点赞/收藏排行、评论数、热力图
```
只读直连的理由：报表 SQL 是多表聚合，走 Feign 会导致"每个报表一个接口"的接口爆炸；只读不破坏单一数据源（所有写操作仍只走各服务自身）。

### ⑥ 公告展示
```
管理员后台发布公告 → 博客前端 GET /admin-service/public/announcement/**（网关白名单，游客可读）
```

## 七、错误处理（全部沿用现有约定）

| 场景 | 处理方式 |
|------|------|
| 参数校验失败 | JSR-303 + ValidationException → 400 |
| 权限不足 | ForbiddenException → 403 统一 Result 格式 |
| Feign 下游故障 | Sentinel FallbackFactory → Result 错误（i18n 文案，如 admin.downstream.unavailable） |
| admin 自有表操作 | 本地事务 |
| 通知消息丢失 | 最终一致，可接受（预留死信队列，与 search-service 死信一并处理） |

**明确声明：admin-service 是编排者，不引入分布式事务**——写操作都在下游服务自身事务内完成（现有发布文章链路已验证）。

## 八、对现有服务的改动清单

| 服务 | 改动 | 接口形态 |
|------|------|------|
| article-service | 新增 `ArticleAdminController` | 管理视角文章分页列表（全状态筛选）、标签 CRUD、系列 CRUD |
| article-service | 新增 `CommentAdminController` | 待审列表、审核通过/拒绝、删除、置顶 |
| article-service | 评论回复后发 MQ | 新增 comment.notify exchange/queue（沿用 ArticleMessageProducer 模式） |
| user-service | 新增 `UserAdminController` | 用户分页列表、封禁/解封、分配/收回 ADMIN 角色 |
| file-service | FileController 补充 | 文件列表 / 删除接口 |
| service-api | 新增 Feign 客户端 | AdminArticleClient、AdminUserClient、AdminFileClient（均带 FallbackFactory） |
| gateway | 路由 + 拦截 | `/admin-service/**` 转发；ADMIN 角色校验；白名单增加 `/admin-service/public/**` |

## 九、测试策略

| 层级 | 做法 |
|------|------|
| admin-service 单测 | JUnit 5 + Mockito，mock Feign 客户端与 Mapper。注意项目已知坑：Result.ok 依赖 I18nUtils 静态 messageSource（需反射注入）；mockStatic 窗口内求值会 UnfinishedStubbing（用 doReturn） |
| 下游接口单测 | article/user/file-service 新增接口按各服务现有单测约定补齐 |
| 全链路验证 | 本地起 Nacos + MySQL + Redis，按验收清单手工走一遍 |
| 前端 | oy-blog-admin 配 Vitest，覆盖 axios 封装、路由守卫与关键组件 |

**验收清单（"真实可用"的判据）**：登录 → 发文章 → 博客前台可见 → 审核评论 → 封禁用户 → 看板有数据 → 发布公告前台可见。

## 十、前端概要（oyb-blog-admin 独立项目）

技术栈：Vue 3 + Vite + TS + Pinia + vue-router + axios（拦截器带 token、统一错误弹窗）+ **Element Plus** + ECharts + md-editor-v3（与博客前端同款编辑器）。

页面清单：登录、仪表盘（统计看板）、文章管理（列表/编辑/标签/系列）、评论审核、用户管理、站点设置、媒体库、公告管理、通知管理、操作日志。

路由守卫：无 token → 登录页。

## 十一、分期实施路线

```
一期（核心真实可用）          二期（周边能力）
├─ admin-service 骨架        ├─ 站点设置
├─ 网关路由 + ADMIN 拦截     ├─ 媒体库（下游补列表/删除）
├─ 登录链路打通              ├─ 操作日志（@Log 落库）
├─ 文章管理（补下游接口）    ├─ 公告 + 评论回复通知（MQ 新链路）
├─ 评论审核（补下游接口）    └─ oy-blog-front 消息中心配合
├─ 用户管理（补下游接口）
├─ 统计看板
└─ oy-blog-admin 前端框架 + 对应页面
```

## 十二、明确不做（YAGNI）

以下仅记录演进方向，不进本期设计：细粒度权限数据接入（机制已预留）、定时发布、邮件通知、数据大屏。

## 十三、风险与缓解

| 风险 | 缓解 |
|------|------|
| 下游服务补接口改动量大 | 改动集中在各服务新增 Admin Controller，不触碰用户端现有链路；分服务提交 |
| 统计看板只读直连打破服务边界 | 仅限报表只读聚合，写操作仍走 Feign；如未来拆分数据库再改为 Feign 统计接口 |
| 通知链路跨服务（article → admin） | MQ 异步 + 最终一致；MVP 后台可见数据即可，用户端消息中心二期再做 |
| 网关 ADMIN 拦截影响现有白名单 | 只新增 `/admin-service/**` 与 `/admin-service/public/**` 规则，不触碰现有规则 |
