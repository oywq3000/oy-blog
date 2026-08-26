# oy-blog-admin 前端联调验收记录

> 日期: 2026-08-26 | 状态: **✅ API 层联调 7/7 通过；浏览器级人工验证清单待用户执行**

## 一、验证环境

- 前端：oy-blog-admin（G:\JavaWorkSpace\frontend\oy-blog-admin，Vite dev 5174 端口，代理 /admin-service、/user-service → 本地网关 8080）
- 后端：本地 4 服务（gateway 8080 / article-service 8091 / user-service 8093 / admin-service 8095），中间件 100.110.148.14
- 测试账号：oyadmin-test（admin 角色，SQL 创建，**验收后已清理**）
- 前端基线：55 单测全绿、`npm run build`（vue-tsc + vite）通过

## 二、API 层联调结果（经 vite 代理实测，7/7 通过）

| # | 检查项 | 结果 |
|---|------|------|
| 1 | dev server 首页 HTML | ✅ 200 |
| 2 | 登录 POST /user-service/auth/login | ✅ 返回 accessToken |
| 3 | 探活 GET /admin-service/admin/current-user | ✅ blogRole=ADMIN、username/status 完整 |
| 4 | 看板 overview | ✅ 5 项真实统计 |
| 5 | 看板 trend | ✅ 真实日期聚合（前端补零逻辑单测覆盖） |
| 6 | 看板 top-articles | ✅ 真实排行 |
| 7 | 文章管理 page | ✅ total=12 分页正确 |
| 8 | 评论审核 page | ✅ 真实评论数据 |
| 9 | 用户管理 page | ✅ total=3 分页正确 |

## 三、待用户浏览器验证清单

以下交互无法在无浏览器环境自动验证，请本地 `npm run dev`（后端按 doc/admin-service-phase1-acceptance.md 流程启动）后逐项检查：

1. 登录页：错误密码提示（单条 toast 不重复）、成功进入仪表盘
2. 仪表盘：5 卡片数字、趋势折线（含补零日期）、TOP10 横向条形渲染正常
3. 文章管理：列表分页/筛选、删除二次确认、标签/系列弹窗增删、新建跳编辑页
4. 编辑页：md-editor 渲染与输入、标题/内容空时按钮禁用、保存草稿/发布后跳回列表
5. 评论审核：待审/全部 tab、通过/拒绝（原因弹窗）/删除/置顶
6. 用户管理：封禁（二次确认）/解封、设/取消管理员（取消有确认）
7. 退出登录：跳回登录页、访问受保护页被守卫拦截

## 四、遗留项（转二期/后续）

- 评论审核页文章列只显示 articleId（后端 CommentAdminItemVo 需补 articleTitle 字段，前端一行改动）
- 概览卡片无 loading/空态；删除末页最后一条页码不回退；保存/发布无提交锁
- tsconfig 非 solution 式（vite.config.ts 逃逸 build 类型检查）；vue/pinia peer 版本缝隙
- 后端侧已有完整二期清单（见 admin-service-phase1-acceptance.md 与 SDD 账本终审）
