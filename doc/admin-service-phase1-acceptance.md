# admin-service 一期验收记录

> 日期: 2026-08-26 | 状态: **✅ 全链路验收通过（本地实例 + 服务器中间件）**

## 一、验收环境

- 中间件（服务器 100.110.148.14，地址取自项目根 .env）：Nacos / MySQL / Redis / RabbitMQ / ES 全部可达
- 应用实例：本地启动 4 个服务（gateway 8080 / article-service 8091 / user-service 8093 / admin-service 8095），按 .env 注入中间件地址与 dev 命名空间（与服务器容器互相隔离）
- 测试账号：验收专用 oyadmin-test（admin 角色）/ oyreader-test（普通读者），SQL 创建 + bcrypt 密码，**验收后已全部清理**
- 前置：`comment_moderation_migration.sql` 与 `admin_seed.sql` 已按顺序在 oyblog 库执行

## 二、验收结果（6/6 全过）

| # | 验收项 | 结果 |
|---|------|------|
| 1 | 登录链路：admin 登录 → `GET /admin-service/admin/current-user` | ✅ blogRole=ADMIN，id/username/status 完整 |
| 2a | READER 访问管理端 | ✅ 403 统一 Result |
| 2b | READER 直连下游管理接口 `/user-service/admin/users/page` | ✅ 403（@RequirePermission 生效） |
| 2c | 无 token 访问 | ✅ 401 |
| 3a | BFF 发布文章 | ✅ 返回 articleId（发布链路含 MQ 索引消息） |
| 3b | 博客前台可见 | ✅ 公开列表可查到 |
| 3c | 管理列表（全状态筛选 + 统计补全） | ✅ 分页/统计字段正常 |
| 3d | 标签列表 | ✅ |
| 4a | READER 发表评论 | ✅ 落库 status=0（待审） |
| 4b | 用户端不可见（待审过滤） | ✅ total=0 |
| 4c | 管理端待审列表 | ✅ status=0 可见 |
| 4d | 审核通过 | ✅ |
| 4e | 用户端可见 | ✅ total=1 |
| 4f | moderation_log 落库 | ✅ action=approve、operator_id 正确、UTF-8 存储无损 |
| 5a | 用户管理列表 + admin 标志 | ✅ |
| 5b | 封禁用户 | ✅ status=0 + 双 key 会话清除 |
| 5c | 被封禁用户下一次请求 | ✅ 401（需认证路径），白名单路径降级为游客 |
| 6a | 统计总览 | ✅ 真实数据（文章/浏览/点赞/评论/用户数） |
| 6b | 近 30 天趋势 | ✅ 真实日期聚合 |
| 6c | TOP10 热门文章 | ✅ 真实排行 |

## 三、验收中发现并修复的缺陷（3 处）

| 缺陷 | 修复 | commit |
|------|------|--------|
| 库中 role.code 为小写 `admin`，代码/种子用大写 `ADMIN` → 角色解析与授权全部失效 | resolveBlogRole 改 equalsIgnoreCase；assignRole 改查 `admin`；种子 SQL 改小写 + 用户名改 `oywq` | 77ab09e |
| user-service 缺分页拦截器 → 用户列表 total=0 | 补 MybatisPlusConfig（与 article-service 同款） | a5f00d7 |
| AuthFilter 的 ADMIN 分支不读缓存 → 探活 username=null | ADMIN 分支与 READER 一致读缓存取完整 UserDTO | a5f00d7 |

另有：FeignBean 冲突（两个 Admin 客户端与既有同服务客户端 Bean 名冲突）由用户提交 contextId 修复（c6136d1），本地启动验证通过。

## 四、验收数据清理

测试文章（软删）、评论（逻辑删）、moderation_log、article_content/article_tag/article_stats/article_chapter、两个测试用户与 user_role 均已清理；库中真实文章 12 篇、原有账号未受影响。

## 五、部署提醒（服务器侧生效前必做）

1. 服务器部署新代码前：执行 `doc/sql/comment_moderation_migration.sql`（已在本库执行过则跳过）
2. 执行 `doc/sql/admin_seed.sql` 给 `oywq` 授 admin 角色（本次已执行）
3. 部署后已登录用户需重新登录（旧会话角色为 READER）
