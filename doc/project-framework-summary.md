# oy-blog 项目框架总结

> 版本: 1.0-SNAPSHOT | Java 21 | Spring Boot 3.4.11

---

## 一、项目概览

**oy-blog** 是一个基于 Spring Cloud 微服务架构的博客系统，采用前后端分离设计，支持文章管理、用户认证、全文搜索、文件存储、消息推送等能力。

---

## 二、技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 基础框架 | Spring Boot | 3.4.11 |
| 微服务网关 | Spring Cloud Gateway | 2024.0.0 |
| 服务治理 | Spring Cloud Alibaba (Nacos + Sentinel) | 2023.0.1.0 |
| ORM | MyBatis Plus | 3.5.7 |
| 数据库 | MySQL | 8.0.33 |
| 缓存 | Redis (Lettuce 客户端) | - |
| 消息队列 | RabbitMQ (Spring AMQP) | - |
| 搜索引擎 | Elasticsearch | 8.17.10 |
| 安全框架 | Spring Security + JWT | jjwt 0.11.5 |
| 对象存储 | MinIO | 8.5.7 |
| 实时通信 | WebSocket (jakarta) | - |
| 接口文档 | SpringDoc OpenAPI (Swagger) | 2.7.0 |
| 分页插件 | PageHelper | 1.3.1 |
| 工具库 | Lombok / jBCrypt / Apache Commons Lang3 / Jackson | - |
| 服务调用 | OpenFeign + Sentinel 熔断 | - |

---

## 三、项目结构

```
oy-blog/
├── pom.xml                          # 根 POM，依赖版本管理
├── oy-blog-common/                  # 【公共模块】共享代码
│   └── src/main/java/com/oyproj/common/
│       ├── annotation/              # 自定义注解 (@Log)
│       ├── aop/                     # AOP 切面 (LogAspect)
│       ├── base/                    # 基础类
│       │   ├── BaseException.java   #   异常基类
│       │   ├── Result.java          #   统一响应体
│       │   ├── ResultCode.java      #   错误码枚举
│       │   └── OpLog.java           #   操作日志实体
│       ├── component/               # 第三方组件 (IpParseApi)
│       ├── config/                  # 配置类
│       │   ├── JWTConfig.java       #   JWT 配置
│       │   └── MessageSourceConfig  #   国际化配置
│       ├── constant/                # 常量/枚举
│       │   ├── BlogRole.java        #   用户角色: READER/GUEST/ADMIN
│       │   ├── HeaderConstant.java  #   请求头常量
│       │   ├── CachePrefix.java     #   缓存 Key 前缀
│       │   └── CommonConstant.java  #   通用常量
│       ├── domain/                  # 通用领域对象
│       │   ├── dto/UserDTO.java     #   用户传输对象
│       │   ├── model/IpDomain.java  #   IP 信息模型
│       │   └── vo/PageVo.java       #   分页 VO
│       ├── exception/               # 异常体系
│       │   ├── GlobalExceptionHandler # 全局异常处理 (@RestControllerAdvice)
│       │   ├── BaseException        #   基础异常
│       │   ├── ForbiddenException   #   403 禁止访问
│       │   ├── UnAuthorizedException#   401 未认证
│       │   ├── NotFoundException    #   404 资源不存在
│       │   ├── ValidationException  #   400 参数验证
│       │   └── UnsupportedUserTypeException
│       ├── mq/                      # 消息队列
│       │   ├── config/RabbitMQConfig #  RabbitMQ 配置 (交换机/队列/绑定)
│       │   ├── constants/            #  MQ 常量 (路由 key 等)
│       │   └── domain/              #  MQ 消息体 (ArticleIndexMessage)
│       ├── properties/              # 配置属性类 (JWTProperties)
│       ├── security/                # 安全模块
│       │   ├── config/SecurityConfig# Spring Security 配置 (禁用 form/httpBasic/session/csrf)
│       │   ├── domain/SecurityUser  #  Spring Security UserDetails 实现
│       │   └── filter/AuthFilter    #  认证过滤器 (解析 Header 构建 SecurityContext)
│       ├── service/                 # 公共服务
│       │   ├── CommonCache          #   统一缓存接口 (Redis)
│       │   ├── impl/CommonCacheImpl #   缓存实现
│       │   └── base/BaseBiz         #   业务基类 (UUID 生成/国际化/Bean 拷贝)
│       └── utils/                   # 工具类
│           ├── JwtUtil / JsonUtil / I18nUtils
│           ├── BeanCopyUtils / ConvertUtils
│           ├── FileUtils / HttpUtils / SqlUtils
│           ├── UUIDUtils / ServletUtils
│
├── oy-blog-gateway/                 # 【API 网关】端口 8080
│   └── src/main/java/com/oyproj/
│       ├── GatewayApplication.java  # 启动类
│       ├── filter/
│       │   └── AuthenticationFilter  # 全局认证过滤器 (GlobalFilter, Order=-100)
│       ├── domain/
│       │   └── AuthenticationResult  # 认证结果对象
│       ├── properties/
│       │   └── AuthProperties        # 白名单配置
│       └── utils/
│           └── GuestUtil             # 游客 Cookie 工具
│
└── oy-blog-service/                 # 【服务父模块】
    ├── pom.xml
    ├── service-api/                 # 【Feign 接口定义】
    │   └── api/
    │       ├── article/client/      #   文章服务 Feign 客户端
    │       ├── file/client/         #   文件服务 Feign 客户端
    │       ├── user/client/         #   用户服务 Feign 客户端
    │       └── config/FeignConfig   #   Feign 全局配置
    │
    ├── user-service/                # 【用户服务】端口 8093
    │   ├── domain/entity/           #   User, Role, Permission, UserRole, RolePermission, UserPermission
    │   ├── domain/dto/              #   LoginDto, RegisterDto, UpdatePasswordDto, UpdateProfileDto
    │   ├── domain/vo/               #   UserVo, UserPublicVo, SimpleUserVo
    │   ├── controller/              #   UserAuthController, UserProfileController
    │   ├── service/                 #   UserAuthBizService, UserProfileBizService, UserCommonBizService, UserStatService
    │   ├── dao/                     #   UserDao, UserStatDao (MyBatis XML)
    │   ├── mapper/                  #   MyBatis Plus Mapper
    │   ├── starategy/               #   用户行为策略
    │   │   ├── UserBehaviorStrategy  #   策略接口: supports(), getProfile()
    │   │   ├── factory/             #   UserBehaviorStrategyFactory (自动注入策略列表)
    │   │   └── impl/                #   CommonUserBehaviorStrategy, GuestUserBehaviorStrategy
    │   ├── base/UserBizBase         #   业务基类 (继承 BaseBiz)
    │   ├── config/CacheConfig       #   Redis 缓存配置
    │   └── utils/SecurityUtil       #   安全工具
    │
    ├── article-service/             # 【文章服务】
    │   ├── domain/entity/           #   Article, ArticleContent, ArticleChapter, ArticleRevision,
    │   │                            #   ArticleStats, ArticleLog, ArticleFavorite, ArticleLike,
    │   │                            #   ArticleSeries, ArticleSeriesItem, ArticleAttachment,
    │   │                            #   ArticleCategory, ArticleTag, Category, Tag,
    │   │                            #   Comment, CommentReply, CommentReaction, ModerationLog
    │   ├── domain/dto/              #   ArticleSaveDto, ArticleViewDto, CommentSaveDto 等
    │   ├── domain/vo/               #   ArticleVo, ArticleContentVo, ArticleChapterVo, CommentVo 等
    │   ├── controller/              #   4 个控制器，按职责拆分:
    │   │   ├── ArticleController          # 文章管理 (发布/草稿/删除/统计)
    │   │   ├── ArticleReadController      # 文章阅读 (列表/内容/章节/历史/标签)
    │   │   ├── ArticleInteractionController # 文章互动 (点赞/收藏)
    │   │   └── ArticleCommentController   # 评论管理 (评论/回复/表态)
    │   ├── service/                 #   6 个业务接口 + 1 个 MQ 生产者
    │   │   ├── ArticleBizService / impl      # 文章管理 (CRUD)
    │   │   ├── ArticleReadBizService / impl  # 文章阅读 (查询)
    │   │   ├── ArticleInteractionBizService  # 互动 (点赞/收藏)
    │   │   ├── ArticleCommentBizService      # 评论
    │   │   ├── ArticleCommonBizService       # 公共服务 (文件上传)
    │   │   └── ArticleMessageProducer        # MQ 消息发送
    │   ├── dto/                    #  数据访问层 (16 个 DAO 接口 + Impl)
    │   │   └── impl/               #   基于 MyBatis Plus BaseMapper 实现
    │   ├── mapper/                 #   MyBatis Plus Mapper 接口 (18 个)
    │   ├── dao/                    #   MyBatis XML Dao (UserArticleStatDao)
    │   ├── base/ArticleBaseBizService # 业务基类 (继承 BaseBiz，增加分页和 userId 获取)
    │   └── utils/PageUtils         #   分页工具
    │
    ├── file-service/               # 【文件服务】
    │   ├── controller/FileController # 文件上传接口
    │   ├── service/impl/
    │   │   ├── AbstractFileService  # 抽象基类 (模板方法模式)
    │   │   ├── FileServiceImpl      # 默认实现
    │   │   └── strategy/            # 存储策略
    │   │       ├── BaseFileService  # 基类
    │   │       └── MinioFileService # MinIO 存储实现
    │   ├── base/BaseUpload          # 上传基类
    │   └── config/StorageConfig     # 存储配置
    │
    ├── search-service/             # 【搜索服务】
    │   ├── controller/EsSearchController # ES 搜索接口
    │   ├── consumer/ArticleIndexConsumer # RabbitMQ 消费者 (监听文章索引/删除消息)
    │   ├── domain/entity/ArticleDocument # ES 文档实体
    │   ├── Repository/ArticleSearchRepository # Spring Data ES Repository
    │   ├── service/SearchBizService # 搜索业务
    │   └── config/ElasticsearchConfig # ES 配置
    │
    └── message-service/            # 【消息服务】
        ├── controller/WebSocketServer    # WebSocket 端点 (/ws/message/{userId})
        ├── service/MessageService        # 消息服务接口
        ├── service/EmailService / impl   # 邮件服务
        ├── strategy/MessageStrategy      # 消息策略接口
        │   └── impl/
        │       ├── EmailMessageStrategy       # 邮件消息
        │       ├── SiteMessageStrategy        # 站内消息
        │       └── WechatOfficialMessageStrategy # 微信公众号消息
        └── constant/MessageTypeEnum      # 消息类型枚举
```

---

## 四、架构分层

项目采用 **DDD 轻量分层** 架构，结合传统三层进行改良：

```
┌─────────────────────────────────────────────┐
│  Controller 层                               │
│  (REST API, @RestController, 路由映射)        │
├─────────────────────────────────────────────┤
│  Service 层                                  │
│  (业务逻辑, Interface → Impl, 事务管理)       │
├─────────────────────────────────────────────┤
│  Dao 层 (数据访问)                            │
│  (封装 Mapper 操作, 自定义查询逻辑)           │
├─────────────────────────────────────────────┤
│  Mapper 层 (ORM 映射)                        │
│  (MyBatis Plus BaseMapper, 基本 CRUD)        │
├─────────────────────────────────────────────┤
│  Domain 层                                   │
│  (Entity/DTO/VO, 领域对象分离)               │
└─────────────────────────────────────────────┘
```

### 对象流转

```
HTTP Request
  → Controller 接收 DTO
    → Service 处理 (DTO → Entity)
      → Dao/Mapper 持久化 (Entity → DB)
    → Service 返回 (Entity → VO)
  → Controller 封装 Result<VO> 响应
```

---

## 五、请求链路

### 5.1 用户请求全链路

```
客户端
  │
  ▼
Spring Cloud Gateway (8080)
  │
  ├─ AuthenticationFilter (GlobalFilter, Order=-100)
  │   ├─ 检查 Authorization Header → JWT 解析
  │   ├─ 验证 Redis 缓存中的用户状态
  │   ├─ 判断角色: READER / GUEST / ADMIN
  │   ├─ 白名单路径放行 (游客)
  │   └─ 注入 Header: X-User-Id, X-User-Type
  │
  ├─ 路由转发 (StripPrefix=1)
  │   ├─ /user-service/**    → lb://user-service
  │   ├─ /article-service/** → lb://article-service
  │   ├─ /file-service/**    → lb://file-service
  │   └─ /search-service/**  → lb://search-service
  │
  ▼
微服务
  │
  ├─ AuthFilter (Servlet Filter)
  │   ├─ 检查 X-Service-Call 头 (服务间调用)
  │   ├─ 解析 X-User-Id / X-User-Type
  │   ├─ READER → 从 Redis 缓存获取 UserDTO
  │   ├─ GUEST  → 构建游客身份 (status=2)
  │   └─ ADMIN  → 构建管理员身份
  │   └─ 设置 SecurityContextHolder
  │
  ├─ Spring Security Filter Chain
  │   └─ .anyRequest().authenticated()
  │       ├─ 未认证 → 401 UnAuthorizedException
  │       └─ 认证通过 → 进入 Controller
  │
  ▼
Controller → Service → Dao → MyBatis → MySQL
```

### 5.2 服务间调用链路

```
article-service
  │
  ├─ Feign Client (service-api 定义)
  │   ├─ UserClient        → user-service     (获取用户信息)
  │   └─ FileUploadClient  → file-service     (文件上传)
  │
  ├─ 设置 Header: X-Service-Call = "true"
  │   └─ 目标服务 AuthFilter 识别并放行
  │
  ├─ Sentinel 熔断降级 (FallbackFactory)
  │
  ▼
目标服务
```

### 5.3 异步消息链路

```
article-service (生产者)
  │
  ├─ 事务提交后 (TransactionSynchronization.afterCommit)
  │   └─ CompletableFuture.runAsync
  │       └─ ArticleMessageProducer.sendArticleIndexMessage()
  │           └─ RabbitTemplate.convertAndSend()
  │
  ▼
RabbitMQ
  ├─ article.index.exchange (Direct Exchange)
  │   ├─ routingKey: article.index  → article.index.queue
  │   └─ routingKey: article.delete → article.delete.queue
  │
  ▼
search-service (消费者)
  └─ ArticleIndexConsumer
      ├─ @RabbitListener → handleArticleIndex()
      │   ├─ CREATE/UPDATE → ArticleSearchRepository.save()
      │   └─ DELETE → ArticleSearchRepository.deleteById()
      └─ @RabbitListener → handleArticleDelete()
```

---

## 六、安全体系

### 6.1 认证流程

```
1. 用户登录 (user-service /auth/login)
   → 验证用户名/密码 (BCrypt)
   → 生成 JWT Token (access + refresh)
   → UserDTO 存入 Redis (key: user:id:{userId})
   → 返回 TokenInfo

2. 后续请求
   → Gateway 提取 Authorization: Bearer {token}
   → JWT 解析获取 userId
   → Redis 验证用户缓存存在 → 认证通过

3. 注销登录
   → Redis 删除用户缓存
   → Token 自然过期 (access: 2h, refresh: 7d)
```

### 6.2 角色体系

| 角色 | 枚举 | 说明 |
|------|------|------|
| READER | `BlogRole.READER` | 注册登录用户，JWT 认证 |
| GUEST | `BlogRole.GUEST` | 游客，Cookie 标识，白名单路径访问 |
| ADMIN | `BlogRole.ADMIN` | 管理员 (预留) |

### 6.3 通用安全配置

```java
// SecurityConfig 核心配置
http.formLogin(disable)      // 禁用表单登录
    .httpBasic(disable)       // 禁用 HTTP Basic
    .logout(disable)          // 禁用默认注销
    .sessionManagement(disable) // 无状态会话
    .csrf(disable)            // 禁用 CSRF
    .anonymous(disable)       // 禁用匿名用户
    .requestCache(NullRequestCache) // 禁用重定向缓存
    .anyRequest().authenticated();  // 所有请求需认证
```

---

## 七、设计模式应用

### 7.1 策略模式 (Strategy Pattern)

**用户行为策略** (`user-service/starategy/`):
```
UserBehaviorStrategy (接口)
  ├─ supports(): BlogRole           — 标识支持的角色
  └─ getProfile(): UserVo           — 获取用户信息

UserBehaviorStrategyFactory          — 自动注入 List<UserBehaviorStrategy>
  └─ getStrategy(BlogRole): Strategy — 根据角色返回对应策略

实现类:
  ├─ CommonUserBehaviorStrategy     — READER 用户
  └─ GuestUserBehaviorStrategy      — GUEST 用户
```

**消息发送策略** (`message-service/strategy/`):
```
MessageStrategy (接口)
  ├─ support(MessageTypeEnum): boolean
  └─ send(MessageSendDto): void

实现类:
  ├─ EmailMessageStrategy           — 邮件消息
  ├─ SiteMessageStrategy            — 站内消息
  └─ WechatOfficialMessageStrategy  — 微信消息
```

**文件存储策略** (`file-service/service/impl/strategy/`):
```
BaseFileService (抽象基类)
  └─ MinioFileService               — MinIO 存储
```

### 7.2 模板方法模式 (Template Method)

```
AbstractFileService
  └─ upload(FileUploadDto): FileVo  — final 方法，定义上传流程
     └─ baseUpload.upload(...)      — 委托给具体存储实现
  └─ delete / getUrl / exists       — final 方法
  └─ download(...)                  — abstract，子类实现
```

### 7.3 继承链 (业务基类层次)

```
BaseBiz (common)
  ├─ getId(): UUID
  ├─ I18n(): 国际化
  ├─ copyProperties(): Bean 拷贝
  └─ copyList(): 列表拷贝
      │
      ├── UserBizBase (user-service)
      │   ├─ getCurrentUserId()
      │   ├─ getCurrentUserDTO()
      │   ├─ getCurrentUserBlogType()
      │   └─ getUser(key)
      │
      └── ArticleBaseBizService (article-service)
          ├─ getUserId(): 从 Header 获取
          └─ getPage(Supplier, Class): 分页查询
```

### 7.4 AOP 切面

```
@Log 注解 (oy-blog-common/annotation/Log.java)
  └─ LogAspect (oy-blog-common/aop/LogAspect.java)
      └─ 记录操作日志: action, func, 请求 IP, 参数

@OpLog 实体 (oy-blog-common/base/OpLog.java)
  └─ 操作日志数据结构
```

### 7.5 全局异常处理

```
GlobalExceptionHandler (@RestControllerAdvice)
  ├─ BaseException          → 400 BAD_REQUEST
  ├─ UnAuthorizedException  → 401 UNAUTHORIZED
  ├─ ForbiddenException     → 403 FORBIDDEN
  ├─ NotFoundException      → 404 NOT_FOUND
  ├─ ValidationException    → 400 BAD_REQUEST
  ├─ MethodArgumentNotValidException → 400 (JSR-303)
  └─ Exception (兜底)       → 500 INTERNAL_SERVER_ERROR
```

### 7.6 统一响应体

```java
Result<T> {
    errCode: Integer,    // 业务状态码
    errMsg: String,      // 提示信息 (国际化)
    isSuccess: Boolean,  // 是否成功
    data: T              // 业务数据
}
```

---

## 八、数据库设计要点

### 8.1 核心表 (article-service)

| 实体 | 表名 | 说明 |
|------|------|------|
| Article | `article` | 文章主表 (标题/状态/可见性/SEO/审核等 30+ 字段) |
| ArticleContent | `article_content` | 文章正文 (Markdown + HTML) |
| ArticleChapter | `article_chapter` | 章节目录 (层级/锚点/路径) |
| ArticleRevision | `article_revision` | 修订历史快照 |
| ArticleStats | `article_stats` | 文章统计 (浏览/点赞/评论/收藏数) |
| ArticleLog | `article_log` | 浏览记录 |
| ArticleLike | `article_like` | 点赞记录 |
| ArticleFavorite | `article_favorite` | 收藏记录 |
| ArticleSeries | `article_series` | 文章系列 |
| Category | `category` | 分类 |
| Tag | `tag` | 标签 |
| Comment | `comment` | 评论 |
| CommentReply | `comment_reply` | 评论回复 |
| CommentReaction | `comment_reaction` | 评论表态 (赞/踩) |
| ModerationLog | `moderation_log` | 审核日志 |

### 8.2 核心表 (user-service)

| 实体 | 表名 | 说明 |
|------|------|------|
| User | `user` | 用户主表 (用户名/密码/邮箱/头像/状态等) |
| Role | `role` | 角色 |
| Permission | `permission` | 权限 |
| UserRole | `user_role` | 用户-角色关联 |
| RolePermission | `role_permission` | 角色-权限关联 |
| UserPermission | `user_permission` | 用户直接权限 |

### 8.3 数据层特点

- 使用 **UUID32** 作为主键 (通过 `UUIDUtils.getId()` 生成)
- **逻辑删除**: `@TableLogic` 注解
- **软删除**: Article 使用 `deleted_at` 字段
- MyBatis Plus 的 `BaseMapper` 提供基础 CRUD，复杂查询使用自定义 Dao
- 部分复杂统计使用 MyBatis XML (`UserArticleStatDao.xml`, `UserStatDao.xml`)

---

## 九、缓存设计

### Redis 数据结构使用

| 场景 | 数据结构 | Key 前缀 |
|------|----------|----------|
| 用户会话 | String | `user:id:{userId}` |
| 通用缓存 | String / Hash | 可配置 |
| 计数器 | String (INCR) | 可配置 |
| 去重统计 | HyperLogLog | 可配置 |
| 关键词排行 | Sorted Set (ZINCRBY) | 可配置 |
| 队列/范围查询 | Sorted Set | 可配置 |

### CommonCache 统一接口

```java
CommonCache<T> {
    get/put/remove/hasKey           // 基础 KV
    multiGet/multiSet/multiDel      // 批量操作
    putHash/getHash                 // Hash 操作
    incr/cumulative/counter         // 计数器
    incrementScore/reverseRangeWithScores  // Sorted Set
    zAdd/zRemove/zRangeByScore      // Zset 操作
    keys/vagueDel                   // 模糊匹配/删除
}
```

---

## 十、微服务基础设施

### 10.1 服务发现 (Nacos)

各服务通过 `bootstrap.yml` 配置 Nacos 注册中心和配置中心地址。

### 10.2 流量控制 (Sentinel)

- user-service 启用了 Sentinel (`sentinel.enabled=true`)
- Sentinel Dashboard: `192.168.200.130:8858`
- Feign 集成了 Sentinel 熔断降级 (`feign.sentinel.enabled=true`)
- 所有 Feign 客户端都有对应的 FallbackFactory

### 10.3 配置中心

- `bootstrap-dev.yml` 存放环境相关的 Nacos 配置
- `application.yml` 存放应用自身配置

---

## 十一、国际化 (i18n)

```
resources/i18n/
  ├─ messages.properties              (默认中文)
  ├─ messages_en_US.properties        (英文)
  ├─ ValidationMessages.properties    (验证消息-中文)
  └─ ValidationMessages_en_US.properties (验证消息-英文)
```

- 通过 `I18nUtils` 工具类统一处理
- `ResultCode` 枚举中的 `messageKey` 字段对应国际化 key
- `MessageSourceConfig` 配置 `basename` 和编码

---

## 十二、待完善模块 (TODO)

根据代码中的 TODO 注释:

| 位置 | 内容 |
|------|------|
| `AuthFilter.java` | 完成权限绑定功能 (Role/Permission) |
| `BaseBiz.java` | 获取当前登录用户 ID 功能 |
| `search-service` | 死信队列处理 |
| `user-service` | 扩展更多用户行为策略 |
| 全局 | 部分 Service 仅有接口框架，待实现完整业务逻辑 |

---

## 十三、关键配置速览

| 服务 | 端口 | 说明 |
|------|------|------|
| Gateway | 8080 | 统一入口 |
| user-service | 8093 | 用户认证与管理 |
| article-service | (Nacos 注册) | 文章核心业务 |
| file-service | (Nacos 注册) | 文件存储 (MinIO) |
| search-service | (Nacos 注册) | Elasticsearch 搜索 |
| message-service | (Nacos 注册) | 消息推送 + WebSocket |

| 中间件 | 地址 |
|------|------|
| MySQL | 192.168.200.130:3306 (oyblog) |
| Redis | 192.168.200.130:6379 |
| RabbitMQ | (默认端口) |
| Elasticsearch | (默认端口) |
| MinIO | (默认端口) |
| Nacos | (Nacos 地址在 bootstrap-dev.yml 中) |
| Sentinel Dashboard | 192.168.200.130:8858 |

> 以上 IP 现统一由项目根 `.env` 的 `SERVER_IP` 环境变量控制（yml 中为 `${SERVER_IP:192.168.200.130}` 占位符），改法见 [doc/local-dev-environment.md](local-dev-environment.md)。
