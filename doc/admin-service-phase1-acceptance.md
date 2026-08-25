# admin-service 一期验收记录

> 日期: 2026-08-26 | 状态: **环境不可达，待部署后补验**

## 一、验证情况汇总

### 已完成验证（无需环境）

| 验证项 | 结果 |
|------|------|
| 全模块编译级集成（gateway + user/article/admin-service + common/service-api，JDK 21） | ✅ BUILD SUCCESS |
| admin-service 单测 | ✅ 13/13（探活 1 + BFF 9 + 看板 3） |
| gateway 单测（AuthenticationFilterTest） | ✅ 3/3 |
| user-service 单测（UserAuthBizServiceImplTest 2 + UserAdminBizServiceImplTest 4） | ✅ 6/6（TestUserStatDao 需真实 DB 的预存测试除外） |
| article-service 单测（ArticleAdminBizServiceImplTest 6 + CommentAdminBizServiceImplTest 4 + 回归） | ✅ 15/15 |
| service-api Fallback 单测 | ✅ 2/2 |

### 未完成验证（环境不可达）

2026-08-26 探测：Nacos 192.168.200.130:8848 连接失败（HTTP 000）、MySQL/Redis 不可达、无本地运行服务。以下验收清单待环境恢复后执行。

## 二、部署前必做（按顺序）

1. **执行评论审核迁移 SQL**（先于新代码发布）：`doc/sql/comment_moderation_migration.sql`
2. **执行管理员种子 SQL**：`doc/sql/admin_seed.sql`（注意：role.id 固定值 'seed-role-admin' 若与库中 UUID32 格式不符，需按库内格式微调；第二条授权语句不受影响）
3. **重启服务顺序**：user-service → article-service → gateway → admin-service（新服务）
4. **部署过渡期提示**：部署前已登录用户的 Redis 会话仍带旧 READER 角色，需重新登录（或等 access token TTL 过期）

## 三、全链路验收清单（环境恢复后执行）

1. **登录链路**：博主账号登录 → `GET /admin-service/admin/current-user` 返回 `blogRole=ADMIN`
2. **权限拦截**：普通 READER 登录 → 调 `/admin-service/admin/current-user` → 网关 403 统一 Result；READER 直接调 `/user-service/admin/users/page` → 同样 403
3. **文章管理**：`POST /admin-service/admin/article/publish` 发文 → 博客前台 `/article-service/article/read/published` 可见 → `POST /admin-service/admin/article/page` 列表含该文章 → 标签/系列增删改查正常
4. **评论审核**：READER 发文评论 → 用户端列表**看不到**（待审）→ 管理端 `/admin-service/admin/comment/page`（status=0）可见 → `POST /admin-service/admin/comment/audit` 通过 → 用户端可见 → moderation_log 有记录
5. **用户管理**：`POST /admin-service/admin/user/page` 列表正常 → 封禁某用户 → 该用户下一请求被网关拒绝（会话已清）
6. **统计看板**：`GET /admin-service/admin/dashboard/overview`、`/trend`、`/top-articles` 返回真实数据

## 四、已知遗留（转二期/前端计划）

- 待审回复的聚合计数泄漏（评论数含待审）：article-service 3 处（countByArticleId ×2、countByCommentIds、selectHotPage reply_count 子查询）
- 统计趋势只返回有访问的日期（前端补零）；overview 汇总含软删文章统计
- 既有 ArticleBizServiceImpl.saveDraft 的 `Result.ok(articleId)` 重载陷阱（data=null、id 进 errMsg，预存 bug）
- 评审收集的 minor 清单见工作区账本 progress.md（终审已阅）
