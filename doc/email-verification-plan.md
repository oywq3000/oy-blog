# 注册邮箱验证码机制实施计划

> 本计划文档实施时同步保存到 `g:/JavaWorkSpace/oy-blog/doc/email-verification-plan.md`

## Context

注册流程目前过于草率：后端 `register()` 不校验邮箱重复（DB 有 email UNIQUE 索引，重复注册会 500）、无任何邮箱验证；前端 AuthModal 注册表单提交即成功。前端已定义"邮件链接验证"三接口（status/request/confirm）但**后端零实现**；`User.emailVerified/emailVerifiedAt` 字段已建但无人读写；message-service 有发信骨架但缺实现与模板。

**目标（用户已确认的决策）**：
1. 注册时 6 位数字邮箱验证码：发送接口 + register 校验，注册成功即 `emailVerified=1`
2. 顺带实现链接验证三接口，使 `/email/verify` 页面与 UserProfile 的"发送验证邮件"按钮可用
3. user-service 直接集成 spring-boot-starter-mail + thymeleaf 发信（不动 message-service）
4. SMTP 账号由用户提供，通过环境变量注入（MAIL_HOST / MAIL_USERNAME / MAIL_PASSWORD），授权码不进仓库

## 一、后端改动

### 1. oy-blog-common

| 文件 | 改动 |
|---|---|
| `common/constant/CachePrefix.java` | 追加枚举 `EMAIL_VERIFY_CODE`、`EMAIL_VERIFY_TOKEN`（getPrefix 返回 `{NAME}_`） |
| `common/base/ResultCode.java` | 追加 `EMAIL_DUPLICATE(410, "邮箱已被注册", "email.duplicate")`、`EMAIL_CODE_INVALID(411, "验证码错误或已过期", "email.code.invalid")` |
| `common/resources/i18n/messages.properties` + `_en_US` | 追加 `email.duplicate / email.code.send.frequent / email.code.send.limit / email.code.invalid / email.verify.needLogin / email.verify.tokenInvalid / email.verify.alreadyVerified / email.code.sent` 中英文案 |
| `common/resources/i18n/ValidationMessages.properties` + `_en_US` | 追加 `register.emailCode.notBlank` |

### 2. user-service

**2.1 依赖**（`user-service/pom.xml`，无版本号，由 spring-boot-starter-parent 3.4.11 管理）：`spring-boot-starter-mail`、`spring-boot-starter-thymeleaf`

**2.2 配置**（`user-service/src/main/resources/application.yml`）：
```yaml
app:
  host: http://localhost:5173   # 邮件链接前缀指向前端 dev origin；生产改域名
spring:
  mail:
    host: ${MAIL_HOST:smtp.qq.com}        # 真实 SMTP 通过环境变量注入（QQ/163 均可）
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}           # IDEA 运行配置 Environment variables 里设置
    password: ${MAIL_PASSWORD:}           # QQ/163 填授权码（不是登录密码）
    default-encoding: UTF-8
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

**2.3 新增类**（`com/oyproj/` 包，均参照现有风格）：

- `utils/VerifyCodeUtils.java`：`genCode()` SecureRandom 6 位数字（`String.format("%06d",...)`）；`genToken()` UUID 去横线
- `service/EmailSendService.java` + `impl/EmailSendServiceImpl.java`：`sendVerifyCode(to, code)` / `sendVerifyLink(to, verifyUrl, username)`。实现参照 message-service 的 `MailUtils`（JavaMailSender + TemplateEngine），**模板名传 `"mail/verify-code"` / `"mail/verify-email"`（不带 templates/ 前缀，避免双前缀 bug）**；主题"【OY Blog】注册验证码"/"【OY Blog】邮箱验证"
- `domain/dto/EmailCodeSendDto.java`：`email` 字段复用现有校验注解 `{register.email.notBlank}` / `{register.email.invalid}`
- `service/EmailVerifyBizService.java` + `impl/EmailVerifyBizServiceImpl.java`（`extends UserBizBase`，构造 `(UserDao, CommonCache)`，复用 `cache/userDao/appHost/I18n`）：
  - `sendCode(dto)`：邮箱已注册 → `BaseException(EMAIL_DUPLICATE)`；防刷 `cache.incr("{EMAIL_VERIFY_CODE}_SEND_"+email, 60L) > 0` → 频繁拒绝、`incr("{EMAIL_VERIFY_CODE}_DAILY_"+email, 86400L) >= 10` → 达上限（CommonCacheImpl 的 incr 首次调用返回 0 并设过期）；生成码 `cache.put("{EMAIL_VERIFY_CODE}_"+email, code, 300L)`（5 分钟）；发信；返回 `Result.ok(I18n("email.code.sent"))`
  - `request()`：**登录判定用 `getCurrentUserBlogType() != BlogRole.READER`**（游客经 AuthFilter 注入 GUEST 角色，`getCurrentUserId()` 对游客返回 guestId 不报错，不能作为登录依据）→ 未登录抛 `email.verify.needLogin`；`userDao.getById(getCurrentUserId())` 不存在 → NotFound；已验证 → `email.verify.alreadyVerified`；生成 token `cache.put("{EMAIL_VERIFY_TOKEN}_"+token, userId, 86400L)`；发链接邮件 `appHost + "/email/verify?token=" + token`
  - `confirm(token)`：`cache.getString("{EMAIL_VERIFY_TOKEN}_"+token)` 为 null → `email.verify.tokenInvalid`；查用户 → `setEmailVerified(1); setEmailVerifiedAt(now); updateById`；`cache.remove` 单次使用
  - `status()`：游客 → `Result.ok(false)`；已登录查 DB `emailVerified==1`
- `controller/EmailVerifyController.java`（`@RequestMapping("/email/verification")`，风格对齐 UserAuthController）：
  - `POST /send-code`（body EmailCodeSendDto，@Valid）
  - `POST /request`（无参）
  - `POST /confirm`（@RequestParam token）
  - `GET /status`

**2.4 邮件模板**（新建 `user-service/src/main/resources/templates/mail/`）：
- `verify-code.html`：变量 `code`、`expireMinutes`，6 位码放大展示
- `verify-email.html`：变量 `verifyUrl`、`expireHours`、`username`，主按钮链接

**2.5 注册改造** `service/impl/UserAuthBizServiceImpl.java`（**不改构造函数**，避免破坏现有测试）：
- `register()` 用户名查重后追加邮箱查重：`userDao.getByEmail(req.getEmail()) != null` → `BaseException(EMAIL_DUPLICATE)`
- 验证码校验：`cache.getString("{EMAIL_VERIFY_CODE}_"+email)` 与 `req.getEmailCode()` 比对，不匹配 → `ValidationException(I18n("email.code.invalid"))`；成功后 `cache.remove` 防重用
- User builder 追加 `.emailVerified(1).emailVerifiedAt(LocalDateTime.now())`

**2.6** `domain/dto/RegisterDto.java` 追加：`@NotBlank(message = "{register.emailCode.notBlank}") private String emailCode;`

### 3. 网关

`oy-blog-gateway/src/main/resources/application.yml` 的 `auth.whitelist` 追加 `- /user-service/email/verification/**`（游客可调 send-code/confirm；request 对游客在业务层返回 400 友好提示，与 profile/info/** 处理策略一致，不触发前端 401 刷新逻辑）

## 二、前端改动

### 1. `src/api/auth.ts`
- `RegisterDto` 加 `emailCode?: string`
- 新增 `sendEmailCode(data: { email: string })` → `POST /email/verification/send-code`
- 现有三接口不动

### 2. 新 composable `src/composables/useEmailCode.ts`
把倒计时逻辑从 UI 抽出（便于测试）：参数为发送函数，返回 `{ cooldown, sending, send(email), reset }`。60s 倒计时，cooldown>0 或 sending 时直接 return（防重复点击）

### 3. `src/components/AuthModal.vue`
- ref 区加 `emailCode / sendCodeCooldown / isSendingCode`；`resetState()` 清空
- 模板：email 行（381-390 行）之后插入验证码行（复用 `.form-group/.has-error` 样式）：6 位输入框 + 发送按钮（**`type="button"` 防止触发表单提交**），按钮文案三分支（发送验证码 / {seconds}秒后重发 / processing），`:disabled="isSendingCode || sendCodeCooldown > 0 || !email"`
- `handleSubmit` 注册分支：`!emailCode` → `error = t('auth.codeRequired')`；register payload 加 `emailCode`
- style 加 `.code-send-btn`（flex-shrink:0，disabled 态）

### 4. i18n `src/locales/zh.ts` / `en.ts`（auth 块追加）
`sendCode / resendInSeconds('{seconds}秒后重发' / 'Resend in {seconds}s') / codeSent / verifyCodePlaceholder / codeRequired`。错误提示不新增 key——后端 errMsg 已本地化，前端直接展示 err.message（现有模式）

### 5. `EmailVerification.vue` / `UserProfile.vue`：后端实现后**零改动**即可用

## 三、测试（TDD：先写失败测试）

### 后端（纯 Mockito，模式照抄 `UserAuthBizServiceImplRefreshTest`：反射注入 I18nUtils.messageSource + 直接 new）

**`EmailVerifyBizServiceImplTest.java`**（mock UserDao/CommonCache/EmailSendService）：
1. sendCode 成功：put 300L 的码为 6 位数字、sendVerifyCode 被调
2. sendCode 邮箱已注册 → BaseException(410)
3. sendCode 60s 防刷（incr 返回 1）→ 拒绝且未发信
4. sendCode 每日上限（incr 返回 10）→ 拒绝
5. request 游客 → 拒绝 needLogin
6. request 登录未验证：token 入缓存 86400L、邮件含 `/email/verify?token=` 链接
7. request 已验证 → 拒绝
8. confirm 有效 token：emailVerified=1、emailVerifiedAt 非空、updateById 被调、token 被删
9. confirm 无效 token → 拒绝
10. status：游客 false / 已验证 true / 未验证 false

**`UserAuthBizServiceImplEmailCodeTest.java`**：
11. register 码缺失/不匹配 → 拒绝且未 save
12. register 码正确：save 的 User emailVerified=1、码 key 被删
13. register 邮箱重复 → BaseException(410)

### 前端
- **`email-code.test.ts`**：useEmailCode 倒计时（fake timers 每秒递减）、冷却中 send 不触发、失败重置、reset 清状态
- **`auth-api.test.ts`**（照抄 request-refresh.test.ts 的 adapter-mock 模式）：sendEmailCode 的 URL/method/body、register payload 透传 emailCode

## 四、部署与验证

1. **部署顺序**：`mvn -pl oy-blog-common -am install` → 重启 gateway（白名单）→ 重启 user-service（新依赖+SMTP 配置）→ 前端 vite 热更。注意：RegisterDto 加必填字段后**前后端需同批上线**（老前端注册会 400）
2. **SMTP 自检**：填真实账号后先调 send-code 看日志（`AuthenticationFailedException`=账号/授权码错；`Could not connect`=host/port 错）
3. **curl 序列**（直达网关 8080）：
   - `POST /user-service/email/verification/send-code {"email":...}` → 收 6 位码
   - `POST /user-service/auth/register {...,"emailCode":"<码>"}` → 成功
   - 登录拿 token → `POST /email/verification/request`（带 Bearer）→ 收链接邮件
   - `POST /email/verification/confirm?token=...` → `GET /email/verification/status`
   - 1 秒内重复 send-code → "发送过于频繁"
4. **前端手工**：注册弹窗 → 发码（按钮 60s 倒计时）→ 填码注册 → 自动切登录；个人中心徽章"已验证"；/email/verify?token= 链接模式
5. **测试命令**：后端 `mvn -pl oy-blog-service/user-service test`；前端 `npm test`
6. **Redis 检查**：`keys *EMAIL_VERIFY*` 应见 code 键（5 分钟过期）、SEND_/DAILY_ 计数键；注册成功后 code 键消失

## 五、可选加固（不在本次范围，仅记录）

- 验证码比对原子化（Lua/getAndDel）消除 getString→remove 竞态
- 后端按 `lang` header 设置 Locale（en 用户收英文 errMsg）
- 发码按 IP 维度限频
- AuthModal 组件挂载测试（@vue/test-utils）
