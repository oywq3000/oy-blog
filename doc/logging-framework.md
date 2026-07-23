# 日志框架文档

## 一、整体架构

```
┌─────────────────────────────────────────────────────────────┐
│  oy-blog-gateway (WebFlux)                                  │
│  ┌──────────────────────┐                                    │
│  │ GatewayLoggingFilter │ ← GlobalFilter，记录网关层请求/响应 │
│  └──────────────────────┘                                    │
│  输出: logs/gateway/gateway-access.log                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼  通过 X-Record-Id 串联
┌─────────────────────────────────────────────────────────────┐
│  article-service / user-service / file-service / ...        │
│  ┌──────────────┐                                            │
│  │ LoggingFilter│ ← Servlet Filter，自动拦截所有 HTTP 请求    │
│  └──────────────┘                                            │
│  输出: logs/article-service/article-service-access.log       │
└─────────────────────────────────────────────────────────────┘
```

**核心思路**：通过 Servlet Filter 在请求入口处自动拦截，不需要你在任何 Controller 上加注解。请求来了自动记，请求走了自动记，完全无侵入。

---

## 二、一次请求的完整生命周期

假设用户请求 `GET /article-service/article/read?page=1`，日志框架做了以下事情：

### 第一步：拦截请求

```
用户浏览器 → Gateway → LoggingFilter → Controller
                        ↑
                   这里被拦截
```

### 第二步：生成 recordId

```
1. 检查请求头里有没有 "X-Record-Id"
2. 有 → 说明是从网关转发过来的，沿用网关的 recordId
3. 没有 → 这是本服务的入口请求，生成一个新的 UUID（32位，去掉横线）
```

### 第三步：包装 request/response

因为 HTTP 的 request body 是流，只能读一次。如果我们在 Filter 里读掉了，Controller 就拿不到数据了。所以：

- **Request** 用 `ContentCachingRequestWrapper` 包装 → body 被缓存在内存中，Controller 读到的是缓存的副本
- **Response** 用 `ContentCachingResponseWrapper` 包装 → 响应数据也缓存，日志记完后再写回客户端

### 第四步：记录请求

```
→ 日志文件
[REQUEST] GET /article-service/article/read | query=page=1 | headers={accept:application/json, host:localhost:8091} | body=[EMPTY] | client=127.0.0.1
```

### 第五步：执行业务逻辑

```
chain.doFilter(wrappedRequest, wrappedResponse)
→ Controller 处理业务
→ Service 层执行数据库查询
→ 返回结果
```

这个阶段你的业务代码里用 `@Slf4j` 打的 log 也会正常输出到日志文件，而且**每条都自动带上 recordId**。

### 第六步：记录响应

```
← 日志文件
[RESPONSE] GET /article-service/article/read | status=200 | duration=23ms | reqBody=[EMPTY] | respBody={"code":200,"message":"success","data":[...]}
```

### 生命周期完成

```
一次请求 = 一条 REQUEST 日志 + N条业务日志 + 一条 RESPONSE 日志
                ↑                              ↑
          共用一个 recordId ←─────────────────┘
```

---

## 三、什么是 Logback

**通俗理解**：Logback 就是 Java 世界的"日志管理器"。你在代码里写 `log.info("xxx")`，Logback 负责把这些信息：
1. **格式化**（加上时间、线程名、日志级别等）
2. **分发**（同时输出到控制台和文件）
3. **滚动**（按天分文件，自动删除过期日志）

### Logback 和代码的关系

```
你的代码                Logback                   输出目标
─────────            ──────────                 ─────────
log.info("hello")  →  格式化               →  控制台（CONSOLE）
log.debug("xxx")   →  按 level 过滤         →  文件（FILE）
log.error("xxx")   →  按 logger 名路由      →  访问日志文件（ACCESS）
```

### Logger 名称路由

这是 Logback 最重要的概念。每个 `log.info()` 调用都属于一个特定的 Logger，你可以对不同 Logger 做不同配置：

```java
// 普通业务日志 → 走 FILE appender
private static final Logger log = LoggerFactory.getLogger(ArticleServiceImpl.class);

// 访问日志 → 走 ACCESS appender（专属文件）
private static final Logger log = LoggerFactory.getLogger("com.oyproj.common.logging.access");
```

在 `logback-spring.xml` 中，我们通过 `<logger>` 标签控制：

```xml
<!-- 访问日志只进 access 文件，不进业务日志文件 -->
<logger name="com.oyproj.common.logging.access" level="INFO" additivity="false">
    <appender-ref ref="ACCESS" />
    <appender-ref ref="CONSOLE" />
</logger>
```

`additivity="false"` 的意思是：这个 logger 的日志**不要**再向上传递给 root logger。否则同一条日志会同时出现在 `access.log` 和业务 `log` 里。

---

## 四、日志文件结构

启动服务后，在项目根目录自动生成 `logs/` 目录：

```
logs/
├── gateway/
│   ├── gateway.log              ← 网关业务日志（当天）
│   ├── gateway-access.log       ← 网关访问日志（当天）
│   └── gateway.2025-01-15.0.log ← 15天前的历史日志
├── article-service/
│   ├── article-service.log
│   ├── article-service-access.log
│   └── article-service.2025-01-15.0.log
├── user-service/
│   └── ...
├── file-service/
│   └── ...
├── message-service/
│   └── ...
└── search-service/
    └── ...
```

### 两类日志文件的区别

| | 业务日志 `.log` | 访问日志 `-access.log` |
|---|---|---|
| **内容** | 启动信息、业务逻辑日志、异常 | HTTP 请求/响应的输入输出 |
| **来源** | 你代码里的 `log.info/debug/error` | `LoggingFilter` / `GatewayLoggingFilter` |
| **格式** | 带 logger 名称（定位到具体类） | 不带 logger 名称（简洁，专注请求信息） |
| **大小上限** | 单文件 100MB 后滚动 | 单文件 200MB 后滚动 |

---

## 五、日志格式详解

### 控制台格式

```
2025-01-15 10:23:45.123 [http-nio-8091-exec-1] INFO  com.oyproj.controller.ArticleController [a1b2c3d4e5f6] - 收到文章查询请求
│                         │                      │      │                                         │              │
│                         │                      │      │                                         │              └─ 实际日志消息
│                         │                      │      │                                         └─ recordId
│                         │                      │      └─ 打印日志的类
│                         │                      └─ 日志级别 (INFO/DEBUG/WARN/ERROR)
│                         └─ 线程名
└─ 时间戳（精确到毫秒）
```

### 文件格式（无颜色）

```
2025-01-15 10:23:45.123 [http-nio-8091-exec-1] INFO  com.oyproj.controller.ArticleController [a1b2c3d4e5f6] - 收到文章查询请求
```

### 颜色说明（仅控制台）

- **黄色** `WARN` — 警告，需要注意但不影响运行
- **红色** `ERROR` — 错误，需要排查
- **绿色** `INFO` — 正常信息
- **蓝色** `DEBUG` — 调试细节

### 日志级别优先级

```
ERROR > WARN > INFO > DEBUG
  ↑       ↑      ↑       ↑
 最严重                     最详细
```

当前配置：业务代码（`com.oyproj`）输出 DEBUG 及以上，框架代码（Spring、MyBatis 等）只输出 WARN 及以上。

---

## 六、配置说明

### 在哪里配置

三个地方协同工作：

```
application.yml        logback-spring.xml        代码中的 @Slf4j
─────────────────      ──────────────────        ──────────────
设置日志文件路径       定义 appender 行为         实际打日志
和日志级别             定义文件滚动策略
                       定义日志格式
```

### application.yml 中的日志配置

```yaml
logging:
  level:
    com.oyproj: DEBUG        # 项目代码输出 DEBUG 级别
    org.springframework: WARN # Spring 框架只输出警告
    com.baomidou: WARN        # MyBatis-Plus 只输出警告
  file:
    path: ./logs              # 日志根目录，模块子目录由 logback-spring.xml 自动创建
```

### logback-spring.xml 的核心参数

```xml
<property name="LOG_PATH" value="${LOG_PATH:-logs}" />   <!-- 可通过 JVM 参数 -DLOG_PATH=xxx 覆盖 -->
<property name="MODULE_NAME" value="article-service" />   <!-- 每个服务不同 -->
<property name="MAX_HISTORY" value="15" />                <!-- 保留多少天 -->
<property name="MAX_FILE_SIZE" value="100MB" />           <!-- 单文件多大后滚动 -->
```

### 如何修改配置

**调整日志保留天数**：改 `logback-spring.xml` 中 `MAX_HISTORY` 的值。

**调整项目日志级别**：改 `application.yml` 中 `logging.level.com.oyproj` 的值。生产环境建议 `INFO`，开发环境用 `DEBUG`。

**修改日志路径**：启动时加参数 `-DLOG_PATH=/var/log/myapp`，或者改 `application.yml` 的 `logging.file.path`。

---

## 七、安全保护

日志框架内置了以下保护机制：

### 敏感数据脱敏

请求体和响应体中的以下字段会被自动替换为 `****`：

```
password, passwd, secret, token, authorization,
access_token, refresh_token, api_key, apikey, credential
```

效果：
```json
// 原始请求体
{"username":"admin","password":"mySecret123","email":"a@b.com"}
// 日志中记录为
{"username":"admin","password":"****","email":"a@b.com"}
```

请求头中的 `authorization`、`cookie`、`set-cookie`、`x-user-data` 也会被脱敏。

### 超长内容截断

请求/响应体超过 2048 字符会被截断，防止日志文件暴涨：

```
... (truncated 3589 chars)
```

响应体超过 4096 字节不会输出具体内容，只记录大小：

```
[LARGE_BODY 15234 bytes]
```

### 文件上传保护

`multipart/form-data` 请求不会记录 body 内容，只显示 `[BINARY]`。

---

## 八、recordId 全链路追踪

`recordId` 是一个 32 位的 UUID，用于将一次请求在所有服务中的日志串联起来。

### 工作原理

```
用户请求（无 recordId）
    │
    ▼
GatewayLoggingFilter
    │  生成 recordId: a1b2c3d4...
    │  写入响应头 X-Record-Id
    ▼
路由到 article-service
    │  请求头带 X-Record-Id: a1b2c3d4...
    ▼
LoggingFilter
    │  从请求头读取 recordId（复用，不重新生成）
    ▼
article-service 内部 Feign 调用 user-service
    │  FeignConfig 从 MDC 取 recordId
    │  写到 Feign 请求头 X-Record-Id
    ▼
user-service
    │  继续沿用同一个 recordId
```

### 如何利用 recordId 排查问题

```bash
# 在 gateway 日志中找到有问题的请求
grep "a1b2c3d4" logs/gateway/gateway-access.log

# 用同一个 recordId 在所有服务中追踪
grep "a1b2c3d4" logs/*/service-access.log

# 同时查看业务日志中该请求的所有信息
grep "a1b2c3d4" logs/*/service.log
```

---

## 九、实时查看日志

### Linux/Mac 生产环境

```bash
# 实时跟踪业务日志（最常用）
tail -f logs/article-service/article-service.log

# 实时跟踪访问日志
tail -f logs/article-service/article-service-access.log

# 只看 ERROR 级别
tail -f logs/article-service/article-service.log | grep ERROR

# 跟踪某个 recordId 的请求完整过程
tail -f logs/article-service/article-service.log | grep "a1b2c3d4"

# 统计最近 100 行中各状态码的数量
tail -100 logs/article-service/article-service-access.log | grep -oP 'status=\d+' | sort | uniq -c
```

### Windows 开发环境

```powershell
# PowerShell 中类似 tail 的命令
Get-Content logs\article-service\article-service.log -Wait -Tail 50

# 或者在 IDE 中直接打开日志文件查看
```

---

## 十、涉及的文件清单

### 新建文件（10个）

| 文件 | 用途 |
|------|------|
| `oy-blog-common/.../logging/LoggingUtils.java` | 工具类：脱敏、截断、路径排除 |
| `oy-blog-common/.../logging/LoggingFilter.java` | Servlet Filter：HTTP 请求/响应自动日志 |
| `oy-blog-common/.../logging/config/LoggingAutoConfiguration.java` | 自动配置：仅在 Servlet 环境注册 Filter |
| `oy-blog-gateway/.../filter/GatewayLoggingFilter.java` | 网关 Filter：WebFlux 环境请求/响应日志 |
| `oy-blog-service/article-service/.../logback-spring.xml` | article-service 日志配置 |
| `oy-blog-service/user-service/.../logback-spring.xml` | user-service 日志配置 |
| `oy-blog-service/file-service/.../logback-spring.xml` | file-service 日志配置 |
| `oy-blog-service/search-service/.../logback-spring.xml` | search-service 日志配置 |
| `oy-blog-service/message-service/.../logback-spring.xml` | message-service 日志配置 |
| `oy-blog-service/message-service/.../application.yml` | message-service 基础配置 |

### 修改文件（7个）

| 文件 | 改动内容 |
|------|----------|
| 5 个服务的 `application.yml` | 追加 `logging.*` 配置节 |
| `oy-blog-gateway/.../application.yml` | 追加 `logging.*` 配置节 |
| `service-api/.../config/FeignConfig.java` | 追加 recordId 跨服务传播 |

---

## 十一、常见问题

### Q: 为什么 POST 请求的 reqBody 显示 `[NOT_READ_YET]`？

A: 这是因为 `ContentCachingRequestWrapper` 的特性——请求体在第一次被读取时才缓存。如果请求体在 REQUEST 日志记录时还没被 Controller 读取，就会显示 `[NOT_READ_YET]`。但在 RESPONSE 日志中会显示实际的请求体内容，因为此时已经被读取过了。

### Q: 日志文件会不会把磁盘占满？

A: 不会。有两个保护机制：
1. **按天滚动**：每天生成新文件，旧文件只保留 15 天
2. **按大小滚动**：单文件超过 100MB（业务日志）或 200MB（访问日志）自动切分

### Q: 开发时能不能不看那么多日志？

A: 可以。把 `application.yml` 中的 `com.oyproj: DEBUG` 改为 `INFO`，就不会输出详细调试信息了。

### Q: 如果我想给某个 Controller 加更详细的日志怎么办？

A: 项目中有一个现成的 `@Log` 注解（`com.oyproj.common.annotation.Log`）+ `LogAspect`，把它加在方法上即可记录该方法的入参和返回值，比 Filter 记录得更细。目前虽然没有方法在用，但机制是完整的。

### Q: 网关的 access 日志为什么没有请求体？

A: 网关是基于 WebFlux（Reactor 响应式编程）的，读取请求体是一个异步操作，如果强行读会破坏路由转发。所以网关只记录 method、URI、headers、status 和耗时。详细的请求体在具体服务（article-service 等）的 access 日志中可以看到。
