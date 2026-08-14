# agent-service 框架详解（面向前端开发者）

> 目标读者：不熟悉 Java 的前端开发者。本文用前端概念类比讲解 agent-service 的每一层代码。
> 配套阅读：[前端契约文档](../frontend/oy-blog-front/docs/agent-api.md)（前端视角的 API 清单）。
> 更新日期：2026-08-13

---

## 0. 一句话总结

agent-service 是一个 **Spring Boot 微服务**（端口 8095），职责是给前端当"AI 聊天中间商"：

```
前端 Vue (5173)
   │  /api/agent/** （REST + SSE）
   ▼
网关 gateway (8080) —— 校验 token、注入 X-User-Id 头、把 /agent/** 转发到 agent-service
   │  /conversations、/chat/stream ...（前缀被剥掉）
   ▼
agent-service (8095) —— 本文主角：存库、统一响应格式、转发给 Python
   │  POST /chat/stream（内网直连，无鉴权）
   ▼
Python Agent (8001) —— 纯 LLM 编排，不碰数据库、不碰用户
```

它做的事情用前端的话说就是：**"API 层 + 状态管理 + 转发代理"**，没有页面。

---

## 1. 最小 Java 知识（看懂本文的前提）

| Java 概念 | 前端类比 | 说明 |
|---|---|---|
| 注解 `@xxx` | 装饰器（decorator） | 写在类/方法/字段上，Spring 启动时"扫描"它们来决定类的角色 |
| `@RestController` | 定义一个 API 路由文件 | 这个类里的方法就是 HTTP 接口 |
| `@GetMapping("/xxx")` | `router.get('/xxx', handler)` | 方法上的注解 = 路由路径 + 方法 |
| `@Service` / `@Component` | 注册到 DI 容器 | 等价于"可被注入的模块" |
| `@RequiredArgsConstructor` | 自动 `inject` 依赖 | Lombok 在编译时生成构造函数，把 `private final` 字段全部注入 |
| `private final XxxMapper mapper;` | `const mapper = useXxx()` | 声明依赖，Spring 启动时自动把单例实例塞进来（**构造器注入**） |
| 泛型 `Result<T>` / `PageVo<T>` | `Result<T>`（TS 泛型） | 同一个容器装不同类型的数据 |
| Lambda / 方法引用 `::` | 箭头函数 `() => {}` | 匿名回调函数 |
| Lombok `@Data @Builder` | 自动生成代码 | 自动生成 getter/setter/构造函数，省掉手写样板 |
| `interface` + `impl` | 接口 + 实现类 | Java 惯例：先定义"能做什么"，再写"怎么做"，方便替换实现 |

---

## 2. 项目结构与分层（对照前端目录）

```
agent-service/src/main/java/com/oyproj/
├── AgentApplication.java          # main() 入口 ≈ main.ts 的 createApp
├── config/                        # ≈ vite.config / 配置文件
│   ├── AgentProperties.java       #   把 application.yml 的 agent.* 绑定成对象
│   ├── PythonWebClientConfig.java #   构造"发请求给 Python"的客户端（≈ 封装的 fetch）
│   └── PageHelperRegister.java    #   注册分页插件（历史坑，见 §6.1）
├── controller/                    # ≈ src/api/（路由层，只做参数接收和返回）
│   ├── AgentConversationController.java   # /conversations 系列
│   ├── AgentChatController.java           # /chat/stream（SSE）
│   ├── AgentMessageController.java        # /messages/{id}/feedback
│   └── AgentSuggestionController.java     # /suggestions
├── service/                       # ≈ src/composables/（业务逻辑层）
│   ├── AgentConversationService.java      # 接口定义
│   ├── AgentChatService.java              # 接口定义
│   └── impl/
│       ├── AgentConversationServiceImpl.java  # 会话 CRUD 业务
│       └── AgentChatServiceImpl.java          # 聊天流式编排（最核心，§5）
├── component/                     # 可复用的"工具组件"
│   ├── ActiveStreamRegistry.java  # 进行中的流登记表（Map）
│   └── PythonSseClient.java       # 调 Python + 解析 SSE 帧
├── domain/
│   ├── entity/                    # ≈ 数据库表结构（每张表一个类）
│   │   ├── AgentConversation.java #   agent_conversation 表
│   │   └── AgentMessage.java      #   agent_message 表
│   ├── dto/                       # 请求体类型（前端发过来的 JSON）
│   │   ├── ChatStreamRequest.java
│   │   ├── RenameRequest.java
│   │   └── FeedbackRequest.java
│   └── vo/                        # 响应体类型（返回给前端的 JSON）
│       ├── ConversationVo.java
│       ├── MessageVo.java
│       └── SuggestedQuestionVo.java
├── mapper/                        # 数据库操作层（MyBatis-Plus）
│   ├── AgentConversationMapper.java
│   └── AgentMessageMapper.java
└── utils/CurrentUserUtil.java     # 从"登录态"里拿当前用户 id
```

**一个请求的流转**（对应前端：组件 → composable → api → axios）：

```
HTTP 请求
  → Controller（路由层，接收参数，拿当前用户）
  → Service（业务逻辑：校验、组装、调用）
  → Mapper（MyBatis-Plus 生成 SQL 读写 MySQL）
  → 返回 Result<T>（统一信封）
```

注意方向：**Controller 依赖 Service，Service 依赖 Mapper**，层与层之间只能向下依赖（等价于前端"views 不直接 import axios，要走 composable"）。

---

## 3. 配置文件（resources/）

### 3.1 application.yml —— 这个服务的一切开关

```yaml
server:
  port: 8095                          # 端口

spring:
  datasource:                         # MySQL 连接（192.168.200.130 的 oyblog 库）
    url: jdbc:mysql://192.168.200.130:3306/oyblog?...
    username: root
    password: root
  data:
    redis:                            # Redis（AuthFilter 需要它缓存用户信息）
      host: 192.168.200.130
  mvc:
    async:
      request-timeout: 600000         # ★ 关键：Tomcat 默认 30 秒会掐断 SSE 长流

agent:                                # 自定义配置（下面 AgentProperties 读的就是它）
  python:
    base-url: http://localhost:8001   # Python 地址，接真服务只改这里
  suggestions:                        # 推荐问题（GET /suggestions 返回的就是它）
    - icon: idea
      text: 帮我写一篇关于 Java 集合框架的博客
```

### 3.2 bootstrap.yml + bootstrap-dev.yml —— 服务注册

```yaml
# bootstrap.yml：服务名叫 agent-service，激活 dev 环境
spring:
  application:
    name: agent-service

# bootstrap-dev.yml：Nacos 注册中心地址
spring:
  cloud:
    nacos:
      discovery:
        server-addr: http://192.168.200.130:8848
```

**Nacos 是什么**：服务注册中心，相当于一个"通讯录"。网关配置里写的是 `lb://agent-service`（按名字找服务），agent-service 启动时到 Nacos 报到，网关转发时从 Nacos 查到它的真实地址 8095。**忘了配 Nacos 的后果**：网关报 503。

### 3.3 AgentApplication.java —— 入口

```java
@SpringBootApplication          // 告诉 Spring：从这里开始扫描本项目所有注解
@EnableTransactionManagement    // 开启数据库事务
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);  // ≈ npm run dev
    }
}
```

注意包名必须与 user-service 一样是 `com.oyproj`——因为 oy-blog-common（公共库）里的安全过滤器等组件都放在这个包名下，Spring 只扫描主类所在包及其子包。

---

## 4. 逐文件讲解

### 4.1 config/AgentProperties.java —— 读配置

```java
@Data
@ConfigurationProperties(prefix = "agent")   // 把 yml 里 agent.* 这段自动填进来
public class AgentProperties {
    private Python python = new Python();                  // agent.python.*
    private List<SuggestedQuestionVo> suggestions;         // agent.suggestions 数组
    public static class Python {
        private String baseUrl = "http://localhost:8001";  // agent.python.base-url
    }
}
```

等价于前端的 `import config from './config.yaml'`。`AgentSuggestionController` 直接把它返回给前端。

### 4.2 config/PythonWebClientConfig.java —— 发请求给 Python 的客户端

```java
@Configuration
@EnableConfigurationProperties(AgentProperties.class)   // 激活上面的配置类
public class PythonWebClientConfig {
    @Bean
    public WebClient pythonWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);  // 连不上 5 秒快速失败
        return WebClient.builder()
                .baseUrl(agentProperties.getPython().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
```

**WebClient ≈ 前端的 fetch**，但它是"响应式"的（Reactor 框架），能流式读响应体——这是 axios 做不到的，所以聊天转发用它。

注意它**只设了连接超时（5s），没设读取超时**——因为 SSE 长流期间 30 秒不出字是正常的（深度思考），任何整体超时都会误杀正常流。

### 4.3 config/PageHelperRegister.java —— 分页插件

```java
@EventListener(ApplicationReadyEvent.class)   // 等整个 Spring 启动完再执行
public void register() {
    PageInterceptor interceptor = new PageInterceptor();   // PageHelper 分页拦截器
    ...
    factory.getConfiguration().addInterceptor(interceptor);  // 塞进 MyBatis 的 SqlSessionFactory
}
```

背景见 §6.1 的坑。它做的事：给数据库查询层挂一个"拦截器"，当业务代码调用 `PageHelper.startPage(1, 20)` 后，下一条 SQL 自动被改写成带 `LIMIT` 的分页查询。

### 4.4 domain/entity —— 数据库表 ↔ Java 对象

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("agent_conversation")     // 对应哪张表
public class AgentConversation {
    @TableId(value = "id", type = IdType.INPUT)   // 主键，且由调用方（前端）提供
    private String id;          // 会话ID（前端生成 conv_*）
    private String userId;      // 归属用户（网关注入的 x-user-id）
    private String title;       // 标题
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

对照建表 SQL（doc/sql/agent_chat.sql）看：**每个字段 = 表里一列**。`AgentMessage` 的 `@TableId(type = IdType.ASSIGN_UUID)` 表示 id 由 MyBatis-Plus 自动生成（UUID），插入后自动回填到对象里——这就是 done 事件里 messageId 的来源。

### 4.5 mapper —— 数据库操作层（神奇的地方）

```java
@Mapper
public interface AgentConversationMapper extends BaseMapper<AgentConversation> {
    // 空的！但已经有 selectById / selectList / insert / updateById / deleteById 等方法
}
```

**MyBatis-Plus 的 BaseMapper**：继承它就能白嫖一堆 CRUD 方法，SQL 自动生成。前端类比：`const db = createCrud<Conversation>('agent_conversation')`，然后 `db.selectById(id)`。

唯一手写的 SQL 是批量统计消息数（避免 N+1 查询——查一页 20 个会话就查 20 次消息表）：

```java
@Select("<script>SELECT conversation_id, COUNT(*) AS cnt FROM agent_message " +
        "WHERE conversation_id IN (...) GROUP BY conversation_id</script>")
List<Map<String, Object>> countByConversationIds(@Param("ids") List<String> ids);
// 一次 SQL 查出 20 个会话各自的消息数
```

### 4.6 domain/dto 与 domain/vo —— 出入参类型

- **DTO（Data Transfer Object，入参）**：前端 POST 的 JSON 体。如 `ChatStreamRequest { conversationId, message, deepThinking, model }`。Spring 会自动把请求体 JSON 反序列化成这个对象（`@RequestBody ChatStreamRequest req`）。
- **VO（View Object，出参）**：返回给前端的 JSON。字段名刻意与前端 `types/agent.ts` 里的 `Conversation`/`Message` 接口**逐字段对齐**（如 `createdAt`、`messageCount`）。

### 4.7 utils/CurrentUserUtil.java —— 当前用户是谁

```java
public static String getUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof SecurityUser securityUser) {
        return securityUser.getUser().getId();    // 真实 userId 或游客 guestId
    }
    throw new UnAuthorizedException("未登录");
}
```

**鉴权是怎么串起来的**（这是理解整个后端安全模型的关键）：

1. 网关的 `AuthenticationFilter` 校验 JWT（登录用户）或放行白名单（游客），然后**向下游请求头里塞 `X-User-Id` 和 `X-User-Type`**
2. agent-service 里的 `AuthFilter`（来自 oy-blog-common 公共库）读到这两个头，构造一个 `SecurityUser` 放进 `SecurityContextHolder`（相当于前端的"全局登录态 store"）
3. 业务代码用 `CurrentUserUtil.getUserId()` 取值

所以 agent-service **自己完全不碰 JWT、不碰密码**——它信任网关。这也是所有接口必须做 owner 校验的原因（白名单放行了游客，任何游客都可能发任意 id 的请求，只能靠比对 `user_id` 字段防止越权）。

### 4.8 component/ActiveStreamRegistry.java —— 进行中的流登记表

```java
public record ActiveStream(SseEmitter emitter, AtomicReference<Disposable> subscription) {}
private final Map<String, ActiveStream> streams = new ConcurrentHashMap<>();
//  ≈ const activeStreams = new Map<conversationId, { emitter, subscription }>()
```

- `register()`：用 `putIfAbsent`（原子操作），返回 false 说明该会话已有流 → 拒绝第二个并发流
- 每个活跃流记两样东西：**emitter**（SSE 响应通道，用来结束响应）和 **subscription**（WebClient 订阅句柄，用来掐断与 Python 的连接）
- 纯内存态：服务重启即丢（Python 侧流会因 TCP 断开自然终止），单机部署够用

### 4.9 component/PythonSseClient.java —— 调 Python + 解析 SSE

**职责 1：发起流式请求**（方法 `streamChat`）：

```java
return pythonWebClient.post()
        .uri("/chat/stream")
        .bodyValue(payload)                  // {conversationId, userId, message, history, deepThinking, model}
        .retrieve()
        .bodyToFlux(DataBuffer.class)        // ★ 以"字节块"流式读响应体
        .subscribe(
            buffer -> { 解码后 parser.feed(chunk) },     // 每来一块数据回调一次
            err -> listener.onError(503, "AI 服务暂不可用"), // 连接失败
            parser::streamEnded                          // 流正常结束
        );
```

为什么用 `DataBuffer`（原始字节）而不是 `String`：Spring 的 `StringDecoder` 不认识 `text/event-stream` 这个 Content-Type，导致一个字节都收不到（§6.2 的坑）。现在手动用 `CharsetDecoder` 解码，它能记住"上一个 chunk 结尾切了一半的中文字符"这种状态，跨 chunk 拼接不乱码。

**职责 2：解析 SSE 帧**（内部类 `StreamParser`，逻辑照抄前端 useAgentChat.ts）：

```
event: token\ndata: {"content":"你"}\n\n      ← 按 \n\n 切成一块
```
解析出 event 名和 data JSON，回调 `listener.onToken / onThinking / onDone / onError`。连接正常关闭但没收到 done/error 时，补一个 `onDone(null)` 兜底。

**职责 3：通知停止**（方法 `stopChat`）：POST `/chat/stop`，发完不管结果（fire-and-forget）。

`StreamListener` 是回调接口——前端类比：`streamChat(payload, { onToken, onThinking, onDone, onError })`。

### 4.10 service/impl/AgentConversationServiceImpl.java —— 会话业务

对应前端的 `useAgentChat` 里除聊天外的一切。挑三个典型方法：

**分页列表**（`listConversations`）：

```java
PageHelper.startPage(page, size);                    // 1. 开启分页（ThreadLocal 记着）
List<AgentConversation> records = conversationMapper.selectList(
        new LambdaQueryWrapper<AgentConversation>()
                .eq(AgentConversation::getUserId, userId)      // WHERE user_id = ?
                .orderByDesc(AgentConversation::getUpdatedAt));// ORDER BY updated_at DESC
PageInfo<AgentConversation> pageInfo = new PageInfo<>(records); // 2. 拿 total/pages
PageHelper.clearPage();                              // 3. 清理，防止污染后续 SQL
```

`LambdaQueryWrapper` ≈ 前端的 query builder：`where('user_id', userId).orderBy('updated_at', 'desc')`。`PageHelper.startPage` 是"声明式分页"：它把页码存进线程变量，拦截器看到后自动给下一条 SQL 拼 `LIMIT` 并额外执行 `COUNT(*)`。

**owner 校验**（`requireOwned`）—— 所有按 id 操作的入口：

```java
AgentConversation conv = conversationMapper.selectById(conversationId);
if (conv == null || !conv.getUserId().equals(userId)) {
    throw new NotFoundException("会话不存在");   // 不存在和不是你的，都报"不存在"，防止枚举
}
```

抛出的异常由公共库的 `GlobalExceptionHandler` 捕获，转成 `{"isSuccess":false,...}` 返回。

**反馈**（`feedback`）：

```java
int rows = messageMapper.update(null, new LambdaUpdateWrapper<AgentMessage>()
        .eq(AgentMessage::getId, messageId)
        .eq(AgentMessage::getUserId, userId)     // ★ 消息级 owner 校验（agent_message 冗余了 user_id 列）
        .set(AgentMessage::getFeedback, feedback));
if (rows == 0) throw new NotFoundException("消息不存在");   // UPDATE 影响 0 行 = 没这消息
```

### 4.11 service/impl/AgentChatServiceImpl.java —— 聊天编排（最核心）

对应前端 `useAgentChat.sendMessage()`。一次调用的完整流程（**强烈建议对着前端源码并行读**）：

```
POST /chat/stream {conversationId, message, deepThinking, model}
│
├─ 0. 参数校验：conversationId/message 不能为空 → 否则发 error 事件
│    同会话已有活跃流（registry.get != null）→ 发 error「已有对话在生成中」
│
├─ 1. 会话 upsert（对应前端"新建会话不发请求，首条消息隐式建档"的设计）
│    ├─ 查不到 → insert（id 用前端传来的 conv_*，标题取消息前 20 字）
│    ├─ 查到了但不是你的 → 发 error「会话不存在」（防越权）
│    └─ 查到了且是你的 → 若标题还是默认「新对话」就用首条消息自动命名；刷新 updated_at
│
├─ 2. 落库用户消息（id 自动生成 UUID）
│
├─ 3. 取最近 20 条历史，升序，塞进给 Python 的 payload.history
│    （Python 是无状态的，不存对话，每次都要把上下文带过去）
│
├─ 4. registry.register(conversationId, active)  —— 登记活跃流，失败说明并发了
│
├─ 5. new SseEmitter(0L)  —— 创建 SSE 响应通道（0 = 不设超时）
│    pythonSseClient.streamChat(payload, {
│        onToken(t)    → 累加进 StringBuilder + emitter 转发给前端
│        onThinking(t) → 记下首次思考时间 + 累加 + 转发
│        onDone()      → finish()：落库 assistant 消息（含思考耗时）
│                        → 发 done{messageId: Java生成的UUID}
│                        → emitter.complete() + registry.remove()
│        onError(code) → 发 error 事件 + complete（失败不落库 assistant 消息）
│    })
│
└─ 6. 收尾：emitter.onCompletion/onTimeout/onError（客户端断开时触发）
         → registry.remove + subscription.dispose()（掐断到 Python 的连接）
         → 通知 Python /chat/stop
```

几个值得注意的设计：

- **消息 id 由 Java 生成**：前端收到 done 后会用 `doneMsgId || 本地临时id` 回填，而 feedback 接口靠的就是这个 id，所以必须传 Java 落库后的真实 id，Python 传回来的 messageId 直接忽略
- **失败不落库**：Python 报错/宕机时只保存用户消息，不伪造 assistant 消息，刷新后前端只看到自己说的话——诚实
- **`finished` 原子标记**：done/error/连接关闭三条路都会触发收尾，用 `AtomicBoolean.compareAndSet` 保证只执行一次（前端类比：防重复提交的 `isFinished` 锁）
- **`stop()` 方法**：dispose 订阅 + complete emitter + 通知 Python。前端点停止时其实会先 abort 本地 fetch（所以这条路径是给"服务器主动停止"兜底的），幂等

### 4.12 controller —— 路由层（对照前端 api/agent.ts）

```java
@RestController
@RequestMapping("/conversations")       // 注意：没有 /agent 前缀！网关 StripPrefix=1 已剥掉
public class AgentConversationController {

    @GetMapping                       // GET /conversations?page&size
    public Result<PageVo<List<ConversationVo>>> list(...) {
        return Result.ok(conversationService.listConversations(CurrentUserUtil.getUserId(), page, size));
    }

    @PostMapping("/{id}/stop")         // POST /conversations/{id}/stop
    public Result<Void> stop(@PathVariable String id) { ... }
    ...
}
```

| Java 注解 | HTTP 接口 | 前端调用位置 |
|---|---|---|
| `@GetMapping`（conversations） | GET /conversations | `getConversations()` |
| `@GetMapping("/{id}/messages")` | GET /conversations/{id}/messages | `getMessages()` |
| `@DeleteMapping("/{id}")` | DELETE /conversations/{id} | `deleteConversation()` |
| `@PatchMapping("/{id}")` | PATCH /conversations/{id} | `renameConversation()` |
| `@PostMapping("/{id}/stop")` | POST /conversations/{id}/stop | `stopGeneration()` |
| `@PostMapping("/stream")`（chat 控制器） | POST /chat/stream（SSE） | `useAgentChat` 里的 fetch |
| `@PostMapping("/{messageId}/feedback")` | POST /messages/{id}/feedback | `submitFeedback()` |
| `@GetMapping`（suggestions 控制器） | GET /suggestions | `getSuggestedQuestions()` |

`{id}` 是路径参数：`@PathVariable String id` ≈ 前端的 `router.get('/:id')` + `req.params.id`。

所有接口统一返回 `Result<T>`——公共库定义的信封 `{errCode, errMsg, isSuccess, data}`，前端 axios 拦截器就是按它判断成败的。

---

## 5. 一次聊天请求的全链路时序

```
浏览器                    网关(8080)                 agent-service(8095)         Python(8001)
  │ POST /api/agent/chat/stream
  │ (fetch, 手拼 Bearer)     │
  ├─────────────────────────▶│ ① 白名单放行(游客) 或 JWT 校验(登录)
  │                          │ ② 注入 X-User-Id 头
  │                          │ ③ 路由 /agent/** StripPrefix=1
  │                          ├──────────────────────────▶ POST /chat/stream
  │                          │                          │ ④ AuthFilter: X-User-Id → SecurityContext
  │                          │                          │ ⑤ upsert 会话、落库用户消息、读历史
  │                          │                          ├──────────▶ POST /chat/stream (带历史)
  │                          │                          │ ◀────────── event:thinking（流式）
  │ ◀── event:thinking ──────┼──────────────────────────┤ ⑥ 累加 + 透传
  │                          │                          │ ◀────────── event:token × N
  │ ◀── event:token ×N ──────┼──────────────────────────┤
  │                          │                          │ ◀────────── event:done{pyMsgId}
  │                          │                          │ ⑦ 落库 assistant(Java生成id)、算 thinkingTime
  │ ◀── event:done ──────────┼──────────────────────────┤
  │ 前端回填 messageId，停止按钮可 feedback
```

三个中间件各自只做一件事：网关管"你是谁"，Java 管"数据归谁、存在哪"，Python 管"答案怎么来"。

---

## 6. 两个坑的原理（了解即可，代码已处理）

### 6.1 为什么用 PageHelper 而不是 MyBatis-Plus 自带分页

项目根 pom 里**全局排除了 jsqlparser**（SQL 解析库，MyBatis-Plus 分页插件依赖它，而 PageHelper 5.2 需要它的旧版 4.0，两个版本会打架）。所以全项目惯例是 pagehelper（article-service 同款）。agent-service 里 `PageHelperRegister` 就是从 article-service 抄的。

### 6.2 为什么 SSE 要手动解码字节

两个连环坑，都踩过并修复：

1. **公共库的 LoggingFilter** 用 `ContentCachingResponseWrapper` 包装响应来记录日志——它会把响应体全部缓冲，SSE 流式响应直接被它憋死（响应提前以空 body 结束）。修复：`/chat/stream` 加入 `LoggingUtils` 的排除路径列表。
2. **WebClient 的 StringDecoder** 不支持 `text/event-stream`，`bodyToFlux(String.class)` 一个字节都收不到。修复：`bodyToFlux(DataBuffer.class)` 拿原始字节 + 有状态的 `CharsetDecoder` 手动解码。

### 6.3 为什么超时要配三处

SSE 长流（深度思考可能 30 秒以上没输出）会被三层的默认 30 秒超时掐断：

| 层 | 配置 | 位置 |
|---|---|---|
| 网关 | 路由 `metadata.response-timeout: 600000` | gateway application.yml |
| Tomcat | `spring.mvc.async.request-timeout: 600000` | agent-service application.yml |
| SseEmitter | `new SseEmitter(0L)`（0 = 不超时） | AgentChatServiceImpl |

---

## 7. 改代码速查（前端同学也能动手）

**加一个新接口**的固定套路：

1. `domain/dto` 或 `domain/vo` 加出入参类（字段名对齐前端 types）
2. `service/impl` 加业务方法（用 mapper 的现成方法，需要新 SQL 才改 mapper）
3. `controller` 加一个方法 + 注解（路径、HTTP 方法、`Result.ok(...)` 返回）
4. 重启服务，curl 验证

**调试技巧**：

- 日志在 `logs/agent-service/agent-service.log`（DEBUG 级别，能看到每一条 SQL 和参数）
- 绕过网关直测：curl 带 `-H "X-User-Id: 任意id" -H "X-User-Type: READER"` 直连 8095
- Windows curl 传中文会变 GBK 乱码报 "Invalid UTF-8"——把 JSON 写进文件用 `--data-binary @file.json`

**接真实 Python 服务的唯一改动**：`application.yml` 的 `agent.python.base-url`，协议见 scripts/agent_stub.py 顶部注释（Java↔Python 契约：POST /chat/stream 带 history，SSE 事件 token/thinking/done/error；POST /chat/stop）。
