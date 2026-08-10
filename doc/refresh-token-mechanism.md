# Refresh Token 刷新机制说明

## 一、为什么需要两个 Token？

### 问题背景

JWT（JSON Web Token）是无状态的——一旦签发，服务端无法主动让它失效，只能等它自己过期。

如果只用一个 access token：

| 方案 | 问题 |
|---|---|
| 过期时间短（如 5 分钟） | 用户频繁掉线，体验差 |
| 过期时间长（如 30 天） | token 泄露后攻击者可长期滥用，无法撤销 |

**双 token 方案**用一个"短命"的 access token + 一个"长命"的 refresh token 来解决：

```
access token  → 短期有效（本项目 2h），每次请求都带，做实际鉴权
refresh token → 长期有效（本项目 7d），只在 access token 过期后使用，只发一次
```

这样即使 access token 泄露，攻击者也只能在 2 小时内使用；而 refresh token 很少在网络上传输，泄露风险低。

---

## 二、完整交互流程

### 时序图

```
┌─────────┐          ┌─────────┐          ┌──────────┐          ┌───────┐
│ 前端/App │          │ Gateway │          │ user-service│        │ Redis │
└────┬────┘          └────┬────┘          └─────┬──────┘          └───┬───┘
     │                    │                     │                    │
     │  ① POST /auth/login (用户名+密码)        │                    │
     │───────────────────►│────────────────────►│                    │
     │                    │                     │ 验证账号密码        │
     │                    │                     │ 生成 access token  │
     │                    │                     │ 生成 refresh token │
     │                    │                     │ 存 refresh 到 Redis├►
     │  {accessToken:"a", │                     │ 存 session 到 Redis├►
     │   refreshToken:"r"}│                     │                    │
     │◄───────────────────│◄────────────────────│                    │
     │                    │                     │                    │
     │  ② GET /article/list                    │                    │
     │  Authorization: Bearer a                │                    │
     │───────────────────►│ 解析 JWT → 合法     │                    │
     │                    │ 查 Redis → session存在                  │
     │                    │────────────────────►│                    │
     │  数据              │◄────────────────────│                    │
     │◄───────────────────│                     │                    │
     │                    │                     │                    │
     │  ... 两小时后 access token 过期 ...      │                    │
     │                    │                     │                    │
     │  ③ GET /article/list                    │                    │
     │  Authorization: Bearer a                │                    │
     │───────────────────►│ 解析 JWT → 过期！   │                    │
     │  401 Token无效或已过期                   │                    │
     │◄───────────────────│                     │                    │
     │                    │                     │                    │
     │  ④ POST /auth/refresh                   │                    │
     │  {refreshToken:"r"}                     │                    │
     │───────────────────►│ (白名单，跳过鉴权)   │                    │
     │                    │────────────────────►│                    │
     │                    │                     │ 解析 refresh token │
     │                    │                     │ 对比 Redis 中存储值 │
     │                    │                     │ 匹配 → 生成新 a' r' │
     │                    │                     │ 替换 Redis refresh├►
     │  {accessToken:"a'",│                     │                    │
     │   refreshToken:"r'"}│                    │                    │
     │◄───────────────────│◄────────────────────│                    │
     │                    │                     │                    │
     │  ⑤ GET /article/list (用新 token a')    │                    │
     │───────────────────►│                     │                    │
     │  数据              │                     │                    │
     │◄───────────────────│                     │                    │
```

### 各步骤解释

| 步骤 | 说明 |
|---|---|
| ① 登录 | 用户名密码正确后，服务端**同时签发**两个 token。access token 2h 过期，refresh token 7d 过期。**refresh token 被存入 Redis**，key 为 `{REFRESH_TOKEN}_<userId>` |
| ② 正常请求 | 每次请求带 access token。Gateway 验证：JWT 签名正确 + 未过期 + Redis 中有对应 session |
| ③ 过期拒绝 | access token 过期后，Gateway 返回 401 |
| ④ 刷新 | 前端用 refresh token 换一对新 token。**旧的 refresh token 立即失效**（旋转策略） |
| ⑤ 继续请求 | 用新的 access token 正常访问 |

---

## 三、Token 旋转（Refresh Token Rotation）

**关键设计：每次刷新后，旧的 refresh token 立即作废。**

```
登录后 Redis：   {REFRESH_TOKEN}_user123 = "r1"

第一次刷新后：    {REFRESH_TOKEN}_user123 = "r2"    (r1 失效)
第二次刷新后：    {REFRESH_TOKEN}_user123 = "r3"    (r2 失效)
```

**为什么需要旋转？**

假设 refresh token r1 泄露给攻击者：
- **有旋转**：用户正常刷新 → Redis 存的是 r2。攻击者用 r1 来刷新 → Redis 中 r1 不匹配 → **拒绝，同时清除该用户所有 token**，强制重新登录
- **无旋转**：refresh token 永久有效，攻击者可以一直刷新，用户无法察觉

本项目实现中，如果检测到 refresh token 不匹配（可能是重放攻击），会**立即删除该用户的 session 和 refresh token**，强制重新登录。

---

## 四、前端该怎么做

### 请求拦截器伪代码

```
// 1. 发送请求前，自动带上 access token
function request(config) {
  config.headers.Authorization = "Bearer " + getAccessToken()
  return send(config)
}

// 2. 收到 401 时，自动尝试刷新
function onResponse(response) {
  if (response.status === 401) {
    // 尝试刷新（只试一次，避免死循环）
    const refreshed = await refreshAccessToken()
    if (refreshed) {
      // 用新 token 重试原请求
      response.config.headers.Authorization = "Bearer " + getAccessToken()
      return send(response.config)
    } else {
      // 刷新也失败了，跳转登录页
      redirectToLogin()
    }
  }
  return response
}

// 3. 刷新函数
async function refreshAccessToken() {
  const res = await fetch("/user-service/auth/refresh", {
    method: "POST",
    body: JSON.stringify({ refreshToken: getRefreshToken() })
  })
  if (res.ok) {
    const data = res.data
    saveAccessToken(data.accessToken)    // 保存新的 access token
    saveRefreshToken(data.refreshToken)  // 保存新的 refresh token（重要！）
    return true
  }
  return false
}
```

### 关键点

| 点 | 说明 |
|---|---|
| 刷新接口不带 Authorization | `/auth/refresh` 在白名单中，不需要鉴权。refresh token 放在请求体里 |
| 刷新后更新两个 token | 旋转策略意味着 refresh token 也变了，前端**必须**同时更新两个 token |
| 防止并发刷新 | 多个请求同时 401 时，应该排队或缓存刷新中的 Promise，避免重复调用 |
| 401 不一定是过期 | 也可能是 token 被篡改、用户被禁用。刷新一次失败后就该跳登录 |

---

## 五、后端各组件职责

```
┌─────────────────────────────────────────────────────────┐
│ Gateway (AuthenticationFilter)                          │
│                                                         │
│ 每个请求经过时：                                          │
│  1. 取出 Authorization: Bearer <token>                  │
│  2. JwtUtil.parseToken(token) → 验签 + 验过期            │
│  3. 检查 type claim 必须是 "access"（拒绝 refresh token） │
│  4. 检查 Redis {USER_ID}_<userId> 存在（session 有效）    │
│  5. 通过 → 转发请求，注入 X-User-Id 头                    │
│  6. 失败 → 返回 401                                     │
│                                                         │
│ 白名单路径（login/register/refresh/health/...）→ 跳过鉴权  │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ user-service (UserAuthBizServiceImpl)                    │
│                                                         │
│  login():  生成两个 token → 存 Redis → 返回              │
│            Redis key: {USER_ID}_<id>, TTL 2h            │
│            Redis key: {REFRESH_TOKEN}_<id>, TTL 7d      │
│                                                         │
│  refresh(): 解析 refresh JWT → 对比 Redis → 生成新 token │
│             → 替换 Redis → 返回（旧 refresh 立即失效）    │
│                                                         │
│  logout(): 删除两个 Redis key → 清 SecurityContext       │
└─────────────────────────────────────────────────────────┘
```

## 六、Redis Key 一览

| Key 模式 | 内容 | TTL | 用途 |
|---|---|---|---|
| `{USER_ID}_<userId>` | UserDTO 序列化 | 2h | Gateway 判断 session 是否存活 |
| `{REFRESH_TOKEN}_<userId>` | refresh token JWT 字符串 | 7d | refresh 时校验 + 旋转策略 |
| `{GUEST_ID}_<uuid>` | - | 30d | 游客身份 cookie |

## 七、安全考量

| 措施 | 说明 |
|---|---|
| **Token 类型区分** | access 和 refresh 有不同的 `type` claim，Gateway 拒绝 refresh token 用于接口访问 |
| **Refresh Token 旋转** | 每次刷新换新的 refresh token，旧 token 作废。不匹配时清除所有 token |
| **登出即失效** | 登出时删除 Redis 中两个 key，access token 即使未过期也无法通过 Gateway 的 Redis 校验 |
| **签名验证** | 所有 JWT 都用 HMAC-SHA256 签名，篡改即拒绝 |
| **Redis 为权威源** | JWT 虽然自包含，但 Gateway 额外查 Redis。这确保了登出、被踢等操作能立即生效 |
