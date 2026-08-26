# oy-blog-admin 管理前端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建独立 Vue 3 管理前端项目 oy-blog-admin，实现登录、统计看板、文章管理（含 Markdown 编辑）、评论审核、用户管理五大页面，与已验收通过的 admin-service 后端对接。

**Architecture:** 独立 git 仓库（G:\JavaWorkSpace\frontend\oy-blog-admin），与博客前端 oy-blog-front 同级但不共享代码；Vite dev proxy 把 `/admin-service`、`/user-service` 前缀转发到本地网关 8080（不重写路径，网关按前缀路由）。axios 统一封装：注入 Bearer token、解包 Result 信封、错误统一提示、401/403 跳登录页。

**Tech Stack:** Vue 3.4 + Vite + TypeScript + Pinia + vue-router + axios + Element Plus + ECharts + md-editor-v3 + Vitest（happy-dom）

**Spec:** `docs/superpowers/specs/2026-08-26-blog-admin-service-design.md`（前端技术栈与页面清单见第十节；后端 API 契约见 `doc/admin-service-phase1-acceptance.md` 中的已验收接口）

## Global Constraints

- 项目目录：`G:\JavaWorkSpace\frontend\oy-blog-admin`（独立 git 仓库，本地提交即可，**不 push**——未获授权）
- Node/npm 使用系统自带版本；所有 npm 命令在该目录执行
- 后端接口契约（已验收，照抄）：
  - 登录：`POST /user-service/auth/login` `{username,password}` → `Result<TokenInfo>`（data.accessToken/refreshToken/expiresIn）；登录后 `GET /admin-service/admin/current-user` 校验 `data.blogRole === 'ADMIN'`
  - Result 信封：`{errCode, errMsg, isSuccess, data}`；isSuccess=false 或 errCode!=200 视为失败
  - 分页请求：`{page, size, ...filters}` POST body；分页响应 `{currentPage, pageSize, total, totalPages, data}`
  - 文章：`POST /admin/article/page`、`POST /admin/article/draft`、`POST /admin/article/publish`（ArticleSaveDto：id/title/summary/contentMd/contentHtml/coverUrl/tags/allowComment）、`DELETE /admin/article/{id}`、标签/系列（POST/DELETE/GET）
  - 评论：`POST /admin/comment/page`、`POST /admin/comment/audit` `{commentId,status(1通过|2拒绝),reason}`、`DELETE /admin/comment/{id}`、`POST /admin/comment/{id}/pin?pinned=0|1`
  - 用户：`POST /admin/user/page`、`POST /admin/user/{id}/ban|unban`、`POST /admin/user/role` `{userId,admin}`
  - 看板：`GET /admin/dashboard/overview|trend|top-articles`
- 测试：Vitest + happy-dom，测试文件放 `src/__tests__/*.test.ts`（博客前端同约定）；每个任务 RED→GREEN
- 图表规范（dataviz）：单系列趋势线用顺序蓝 `#2a78d6`（2px 线、圆角数据点、crosshair+tooltip）；TOP10 用横向条形（顺序蓝同色系、条形圆角、直接标签）；概览卡片是 stat tile 不是图表；**禁止双轴**；轴线/网格用次级墨色弱化；文字一律不用系列色
- 提交信息：`feat: 中文描述`，正文末加 Co-Authored-By 行
- 中文 UI 文案；不做 i18n（博客前端的 vue-i18n 不引入管理端）

---

### Task 1: 项目脚手架与构建验证

**Files:**
- Create: `G:\JavaWorkSpace\frontend\oy-blog-admin\package.json`、`vite.config.ts`、`tsconfig.json`、`tsconfig.node.json`、`index.html`、`.gitignore`、`src/main.ts`、`src/App.vue`、`src/env.d.ts`、`src/styles/base.css`、`src/router/index.ts`（空路由壳）、`src/__tests__/app-smoke.test.ts`

**Interfaces:**
- Produces: 可 `npm run dev`/`npm run build`/`npm test` 的工程骨架；App 根组件渲染 router-view

- [ ] **Step 1: git init 与 package.json**

```bash
cd /g/JavaWorkSpace/frontend && mkdir oy-blog-admin && cd oy-blog-admin && git init
```

```json
{
  "name": "oy-blog-admin",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc -b && vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "axios": "^1.13.2",
    "echarts": "^5.6.0",
    "element-plus": "^2.10.0",
    "md-editor-v3": "^6.2.0",
    "pinia": "^3.0.0",
    "vue": "^3.4.0",
    "vue-router": "^4.6.3"
  },
  "devDependencies": {
    "@types/node": "^20.0.0",
    "@vitejs/plugin-vue": "^5.0.0",
    "@vue/test-utils": "^2.4.11",
    "@vue/tsconfig": "^0.5.0",
    "happy-dom": "^20.11.2",
    "typescript": "^5.4.0",
    "vite": "^6.0.0",
    "vitest": "^3.0.0",
    "vue-tsc": "^2.0.0"
  }
}
```

- [ ] **Step 2: vite.config.ts**（代理两个前缀直连网关，不重写）

```typescript
/// <reference types="vitest" />
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'happy-dom',
  },
  server: {
    host: '0.0.0.0',
    port: 5174,
    proxy: {
      // 网关按前缀路由（StripPrefix=1），代理不做重写
      '/admin-service': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/user-service': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (id.includes('node_modules/echarts')) return 'echarts'
          if (id.includes('node_modules/element-plus')) return 'element-plus'
          if (id.includes('node_modules/md-editor-v3')) return 'md-editor'
        },
      },
    },
  },
})
```

- [ ] **Step 3: tsconfig.json / tsconfig.node.json / index.html / .gitignore / env.d.ts / base.css**

tsconfig.json（`"extends": "@vue/tsconfig/tsconfig.dom.json"`、include `src/**/*.ts`、`src/**/*.vue`、compilerOptions `composite: true` 即可，与博客前端一致）。index.html：`<div id="app">` + module 入口 `/src/main.ts`。.gitignore：`node_modules/`、`dist/`、`*.local`。env.d.ts：`/// <reference types="vite/client" />`。base.css：`html,body,#app{height:100%;margin:0}`。

- [ ] **Step 4: 失败测试**

`src/__tests__/app-smoke.test.ts`:

```typescript
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import App from '../App.vue'

describe('App 冒烟', () => {
  it('渲染 router-view 壳', () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div>home</div>' } }],
    })
    const wrapper = mount(App, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('home')
  })
})
```

`src/App.vue` 先写占位版（`<template><router-view/></template>`）——测试预期失败（App.vue 未创建）。

- [ ] **Step 5: main.ts 与 App.vue，跑绿**

```typescript
// main.ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/base.css'

createApp(App).use(createPinia()).use(ElementPlus).use(router).mount('#app')
```

```vue
<!-- App.vue -->
<template>
  <router-view />
</template>
```

Run: `npm install`（约 1-3 分钟）→ `npm test` → `npm run build`
Expected: 测试 PASS，build 成功

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: oy-blog-admin 项目脚手架

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: axios 封装 request.ts

**Files:**
- Create: `src/api/request.ts`、`src/utils/token.ts`、`src/__tests__/request.test.ts`

**Interfaces:**
- Consumes: Task 1 骨架
- Produces: `request` axios 实例；`getToken/setToken/clearToken`（localStorage key `oy-admin-token`）；`request<T>(config)` 返回 `Promise<T>`（已解包 Result.data；isSuccess=false 时 reject 并统一弹出 Element Plus 消息）；401 清 token 跳 `/login`；403 弹"未授权"

- [ ] **Step 1: 失败测试**

`src/__tests__/request.test.ts`:

```typescript
import { describe, expect, it, vi, beforeEach } from 'vitest'
import axios from 'axios'
import request, { apiError } from '../api/request'
import { setToken, getToken, clearToken } from '../utils/token'

vi.mock('axios', () => {
  const mockInstance = {
    request: vi.fn(),
    defaults: { headers: { common: {} } },
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  }
  return { default: { create: () => mockInstance, __instance: mockInstance } }
})

const mocked = (axios as any).__instance

describe('request 封装', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clearToken()
  })

  it('解包 Result.data', async () => {
    mocked.request.mockResolvedValue({ data: { errCode: 200, errMsg: '成功', isSuccess: true, data: { id: 'a1' } } })
    const data = await request({ url: '/x', method: 'GET' })
    expect(data).toEqual({ id: 'a1' })
  })

  it('isSuccess=false 时 reject 并抛出 apiError', async () => {
    mocked.request.mockResolvedValue({ data: { errCode: 403, errMsg: '未授权', isSuccess: false, data: null } })
    await expect(request({ url: '/x', method: 'GET' })).rejects.toThrow(apiError)
  })

  it('携带 Authorization 头', () => {
    setToken('t-123')
    const interceptor = mocked.interceptors.request.use.mock.calls[0][0]
    const config = interceptor({ headers: {} })
    expect(config.headers.Authorization).toBe('Bearer t-123')
    expect(getToken()).toBe('t-123')
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /g/JavaWorkSpace/frontend/oy-blog-admin && npm test`
Expected: FAIL（request.ts 不存在）

- [ ] **Step 3: 写实现**

`src/utils/token.ts`:

```typescript
const TOKEN_KEY = 'oy-admin-token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}
```

`src/api/request.ts`:

```typescript
import axios, { AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { clearToken, getToken } from '../utils/token'

export interface ResultEnvelope<T = unknown> {
  errCode: number
  errMsg: string
  isSuccess: boolean
  data: T
}

export class ApiError extends Error {
  constructor(public errCode: number, message: string) {
    super(message)
  }
}

const instance = axios.create({ timeout: 15000 })

instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (response) => {
    const body = response.data as ResultEnvelope
    if (body && body.isSuccess === false) {
      if (body.errCode === 401) {
        clearToken()
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
      } else if (body.errCode === 403) {
        ElMessage.error(body.errMsg || '未授权')
      } else {
        ElMessage.error(body.errMsg || '请求失败')
      }
      return Promise.reject(new ApiError(body.errCode, body.errMsg))
    }
    return response
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      clearToken()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    } else if (status === 403) {
      ElMessage.error('未授权')
    } else {
      const msg = error?.response?.data?.errMsg || '网络异常'
      ElMessage.error(msg)
    }
    return Promise.reject(new ApiError(status ?? -1, error?.response?.data?.errMsg ?? '网络异常'))
  },
)

/** 发起请求并解包 Result.data；业务失败会抛出 ApiError */
export default async function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  const response = await instance.request<ResultEnvelope<T>>(config)
  return response.data.data
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `npm test`
Expected: PASS（3 用例）

- [ ] **Step 5: Commit**

```bash
git add src/api/request.ts src/utils/token.ts src/__tests__/request.test.ts
git commit -m "feat: axios 封装与 token 工具

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: auth store、登录页与路由守卫

**Files:**
- Create: `src/store/auth.ts`、`src/views/LoginView.vue`、`src/router/index.ts`（完整路由 + beforeEach 守卫）、`src/__tests__/login-view.test.ts`、`src/__tests__/router-guard.test.ts`

**Interfaces:**
- Consumes: Task 2 的 request/token
- Produces: `useAuthStore`（state: token/user；actions: `login(username,password)`、`logout()`、`fetchCurrentUser()`）；`/login` 路由（免守卫）；其余路由守卫：无 token → `/login`；登录成功拉 current-user 校验 `blogRole==='ADMIN'`，非 ADMIN 弹错并登出

- [ ] **Step 1: 失败测试**（登录页 + 守卫）

```typescript
// src/__tests__/login-view.test.ts
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ElMessage } from 'element-plus'
import LoginView from '../views/LoginView.vue'
import { useAuthStore } from '../store/auth'
import request from '../api/request'

vi.mock('../api/request')
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), success: vi.fn() } }))

describe('登录页', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('登录成功跳转 /', async () => {
    vi.mocked(request)
      .mockResolvedValueOnce({ accessToken: 't1', expiresIn: 7200 } as any) // login
      .mockResolvedValueOnce({ blogRole: 'ADMIN' } as any) // current-user
    const wrapper = mount(LoginView)
    await wrapper.find('input[type="text"]').setValue('admin')
    await wrapper.find('input[type="password"]').setValue('pw')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(useAuthStore().token).toBe('t1')
  })

  it('非 ADMIN 登录被拒绝', async () => {
    vi.mocked(request)
      .mockResolvedValueOnce({ accessToken: 't2', expiresIn: 7200 } as any)
      .mockResolvedValueOnce({ blogRole: 'READER' } as any)
    const wrapper = mount(LoginView)
    await wrapper.find('input[type="text"]').setValue('reader')
    await wrapper.find('input[type="password"]').setValue('pw')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(useAuthStore().token).toBeNull()
    expect(ElMessage.error).toHaveBeenCalled()
  })
})
```

```typescript
// src/__tests__/router-guard.test.ts
import { describe, expect, it, beforeEach } from 'vitest'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../store/auth'
import { buildRoutes } from '../router'

describe('路由守卫', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('无 token 访问受保护页 → 跳 /login', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: buildRoutes() })
    await router.push('/article')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('有 token 访问受保护页 → 放行', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: buildRoutes() })
    useAuthStore().token = 't'
    await router.push('/article')
    expect(router.currentRoute.value.path).toBe('/article')
  })
})
```

（注：`buildRoutes()` 与守卫 `beforeEach` 都在 `src/router/index.ts` 导出——守卫在测试中注册：`router.beforeEach(guard)`；实现时按此拆分以保持可测。）

- [ ] **Step 2: 跑测试确认失败**

Run: `npm test`
Expected: FAIL

- [ ] **Step 3: 写实现**

`src/store/auth.ts`:

```typescript
import { defineStore } from 'pinia'
import request from '../api/request'
import { clearToken, getToken, setToken } from '../utils/token'

interface UserInfo {
  id: string
  username: string | null
  blogRole: string
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken() ?? '',
    user: null as UserInfo | null,
  }),
  actions: {
    async login(username: string, password: string) {
      const tokenInfo = await request<{ accessToken: string; expiresIn: number }>({
        url: '/user-service/auth/login',
        method: 'POST',
        data: { username, password },
      })
      setToken(tokenInfo.accessToken)
      this.token = tokenInfo.accessToken
      await this.fetchCurrentUser()
    },
    async fetchCurrentUser() {
      const user = await request<UserInfo>({ url: '/admin-service/admin/current-user', method: 'GET' })
      if (user.blogRole !== 'ADMIN') {
        this.logout()
        throw new Error('非管理员账号')
      }
      this.user = user
    },
    logout() {
      clearToken()
      this.token = ''
      this.user = null
    },
  },
})
```

`src/views/LoginView.vue`（Element Plus 表单：username/password 两个输入框 + 提交按钮；submit 时 `await auth.login(...)` 成功 `router.push('/')`，失败 `ElMessage.error('用户名或密码错误')`；组件脚本里捕获 ApiError/Error）。

`src/router/index.ts`:

```typescript
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../store/auth'

export function buildRoutes(): RouteRecordRaw[] {
  return [
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: () => import('../views/DashboardView.vue') },
    { path: '/article', component: () => import('../views/ArticleListView.vue') },
    { path: '/article/edit/:id?', component: () => import('../views/ArticleEditView.vue') },
    { path: '/comment', component: () => import('../views/CommentAuditView.vue') },
    { path: '/user', component: () => import('../views/UserManageView.vue') },
  ]
}

export function guard() {
  return (to: { meta?: { public?: boolean } }) => {
    const auth = useAuthStore()
    if (!to.meta?.public && !auth.token) {
      return '/login'
    }
    return true
  }
}

const router = createRouter({ history: createWebHistory(), routes: buildRoutes() })
router.beforeEach(guard())
export default router
```

（注：`/dashboard` 等 view 组件此时还不存在 → 本任务先建 4 个占位 view 文件，内容 `<template><div>todo</div></template>`，Task 4-9 逐个替换。占位文件名必须与路由一致。）

- [ ] **Step 4: 跑测试确认通过**

Run: `npm test && npm run build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/store/auth.ts src/views/ src/router/index.ts src/__tests__/login-view.test.ts src/__tests__/router-guard.test.ts
git commit -m "feat: 登录页、auth store 与路由守卫

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: 布局框架（侧边栏 + 顶栏）

**Files:**
- Modify: `src/App.vue`（登录页外挂布局）、Create: `src/components/AdminLayout.vue`、`src/__tests__/admin-layout.test.ts`

**Interfaces:**
- Produces: `AdminLayout`（Element Plus 菜单：仪表盘/文章管理/评论审核/用户管理；顶栏显示 `user.username ?? user.id` + 退出按钮 logout 跳登录）

- [ ] **Step 1: 失败测试**

```typescript
// src/__tests__/admin-layout.test.ts
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AdminLayout from '../components/AdminLayout.vue'
import { useAuthStore } from '../store/auth'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

describe('AdminLayout', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('菜单含四个模块与退出按钮', () => {
    const wrapper = mount(AdminLayout)
    expect(wrapper.text()).toContain('仪表盘')
    expect(wrapper.text()).toContain('文章管理')
    expect(wrapper.text()).toContain('评论审核')
    expect(wrapper.text()).toContain('用户管理')
    expect(wrapper.text()).toContain('退出')
  })

  it('退出清空 token 并跳登录', async () => {
    const auth = useAuthStore()
    auth.token = 't'
    auth.user = { id: 'u1', username: 'admin', blogRole: 'ADMIN' }
    const wrapper = mount(AdminLayout)
    await wrapper.find('[data-test="logout"]').trigger('click')
    await flushPromises()
    expect(auth.token).toBe('')
  })
})
```

- [ ] **Step 2: 跑测试确认失败 → Step 3 实现 → Step 4 跑绿**

`AdminLayout.vue`：`<el-container>` + 左侧 `<el-menu :router="true">`（4 个 el-menu-item，index 为路由路径）+ 右侧 header（用户名 + 退出按钮 `data-test="logout"`，点击 `auth.logout()` + `router.push('/login')`）+ `<el-main><router-view/></el-main>`。App.vue 改为：`<LoginView v-if="$route.path==='/login'"/> <AdminLayout v-else/>`（简单按路径切换，不做嵌套路由）。

- [ ] **Step 5: Commit**

```bash
git add src/components/AdminLayout.vue src/App.vue src/__tests__/admin-layout.test.ts
git commit -m "feat: 管理端布局框架

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: API 模块（admin-article/comment/user/dashboard）

**Files:**
- Create: `src/api/admin-article.ts`、`src/api/admin-comment.ts`、`src/api/admin-user.ts`、`src/api/admin-dashboard.ts`、`src/types/admin.ts`、`src/__tests__/admin-api.test.ts`

**Interfaces:**
- Produces（后续页面任务的精确签名）:

```typescript
// src/types/admin.ts
export interface PageDto { page: number; size: number }
export interface PageVo<T> { currentPage: number; pageSize: number; total: number; totalPages: number; data: T }
export interface ArticleSaveDto { id?: string; title: string; summary?: string; contentMd: string; contentHtml?: string; coverUrl?: string; tags?: string[]; allowComment?: number }
export interface ArticleAdminItem { id: string; title: string; summary: string; status: string; publishAt: string; updateAt: string; views: number; likes: number; comments: number }
export interface CommentAdminItem { id: string; articleId: string; userId: string; content: string; status: number; isPinned: number; commentAt: string }
export interface UserAdminItem { id: string; username: string; email: string; status: number; avatarUrl: string | null; admin: boolean; createdAt: string }
export interface OverviewVo { articleCount: number; viewCount: number; likeCount: number; commentCount: number; userCount: number }
export interface DailyTrendVo { date: string; count: number }
export interface TopArticleVo { id: string; title: string; views: number; likes: number; comments: number }
```

```typescript
// admin-article.ts
articlePage(dto: PageDto & { status?: string; keyword?: string }): Promise<PageVo<ArticleAdminItem[]>>
saveDraft(dto: ArticleSaveDto): Promise<string>
publish(dto: ArticleSaveDto): Promise<{ articleId?: string }>
deleteArticle(id: string): Promise<boolean>
saveTag(dto: { id?: string; name: string; isCommon?: number }): Promise<string>
deleteTag(id: string): Promise<boolean>
listTags(): Promise<{ id: string; name: string; isCommon: number }[]>
saveSeries / deleteSeries / listSeries（同构）

// admin-comment.ts
commentPage(dto: PageDto & { status?: number }): Promise<PageVo<CommentAdminItem[]>>
audit(dto: { commentId: string; status: number; reason?: string }): Promise<boolean>
deleteComment(id: string): Promise<boolean>
pinComment(id: string, pinned: 0 | 1): Promise<boolean>

// admin-user.ts
userPage(dto: PageDto & { keyword?: string; status?: number }): Promise<PageVo<UserAdminItem[]>>
banUser(id: string): Promise<boolean>
unbanUser(id: string): Promise<boolean>
assignRole(dto: { userId: string; admin: boolean }): Promise<boolean>

// admin-dashboard.ts
overview(): Promise<OverviewVo>
trend(): Promise<DailyTrendVo[]>
topArticles(): Promise<TopArticleVo[]>
```

- [ ] **Step 1: 失败测试**（mock `../api/request`，断言每个函数以正确的 method/url/data 调用 request 并返回 data——例如 `articlePage({page:1,size:10,status:'draft'})` 应调用 `request({url:'/admin-service/admin/article/page',method:'POST',data:{page:1,size:10,status:'draft'}})`）

- [ ] **Step 2: 跑测试确认失败 → Step 3 实现 → Step 4 跑绿**

实现即一行行 `return request<T>({ url, method, data })`（URL 前缀 `/admin-service`，与后端已验收路径一致）。

- [ ] **Step 5: Commit**

```bash
git add src/api/admin-*.ts src/types/admin.ts src/__tests__/admin-api.test.ts
git commit -m "feat: 管理端 API 模块与类型定义

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: 仪表盘页（统计看板）

**Files:**
- Modify: `src/views/DashboardView.vue`（替换占位）、Create: `src/composables/useEcharts.ts`、`src/__tests__/dashboard-view.test.ts`

**Interfaces:**
- Consumes: Task 5 的 admin-dashboard.ts
- Produces: 概览 5 个 stat tile（文章/浏览/点赞/评论/用户数）；近 30 天访问趋势折线（ECharts，**前端补零**：对 trend 返回的日期做 30 天连续化处理）；TOP10 热门文章横向条形

- [ ] **Step 1: 失败测试**

```typescript
// src/__tests__/dashboard-view.test.ts
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DashboardView from '../views/DashboardView.vue'
import { fillZeroDays } from '../views/DashboardView.vue' // 同文件导出纯函数
import { overview, trend, topArticles } from '../api/admin-dashboard'

vi.mock('../api/admin-dashboard')

describe('DashboardView', () => {
  beforeEach(() => vi.clearAllMocks())

  it('渲染 5 个概览数字', async () => {
    vi.mocked(overview).mockResolvedValue({ articleCount: 12, viewCount: 75, likeCount: 4, commentCount: 4, userCount: 4 })
    vi.mocked(trend).mockResolvedValue([{ date: '2026-08-22', count: 5 }])
    vi.mocked(topArticles).mockResolvedValue([{ id: 'a1', title: '热门', views: 22, likes: 1, comments: 0 }])
    const wrapper = mount(DashboardView)
    await flushPromises()
    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('75')
  })
})

describe('fillZeroDays', () => {
  it('补齐缺失日期为 0', () => {
    const out = fillZeroDays([{ date: '2026-08-25', count: 3 }], 3)
    expect(out).toHaveLength(3)
    expect(out.map((d) => d.count)).toEqual([0, 0, 3])
    expect(out[0].date).toBe('2026-08-23')
  })
})
```

（注：`fillZeroDays` 与组件同文件导出，组件 mount 时 ECharts 初始化需 mock：`vi.mock('echarts', () => ({ init: () => ({ setOption: vi.fn(), dispose: vi.fn(), resize: vi.fn() }) }))` 放在测试顶部。）

- [ ] **Step 2: 跑测试确认失败 → Step 3 实现 → Step 4 跑绿**

实现要点（dataviz 规范）：
- stat tile：数字大字 + 中文标签，无图
- 折线 option：`color: ['#2a78d6']`、`lineStyle:{width:2}`、`symbolSize:6`、tooltip `trigger:'axis'` + `axisPointer:{type:'cross'}`、轴线/网格线 `#d8d8d4` 弱化、无 legend（单系列）
- TOP10 条形 option：`xAxis:{type:'value'}`、`yAxis:{type:'category', data: titles, inverse:true}`（第一行是最热文章）、`color:['#2a78d6']`、`itemStyle:{borderRadius:[0,4,4,0]}`、`barWidth:14`、直接标签（label `{show:true, position:'right', color:'#52514e'}`）
- `useEcharts`：`init(el)` onMounted、`setOption`、onBeforeUnmount `dispose()`、window resize 监听

- [ ] **Step 5: Commit**

```bash
git add src/views/DashboardView.vue src/composables/useEcharts.ts src/__tests__/dashboard-view.test.ts
git commit -m "feat: 统计看板仪表盘页

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: 文章管理页（列表 + 标签/系列）

**Files:**
- Modify: `src/views/ArticleListView.vue`（替换占位）、Create: `src/__tests__/article-list-view.test.ts`

**Interfaces:**
- Consumes: Task 5 的 admin-article.ts
- Produces: 分页表格（标题/状态 tag/发布时间/浏览/点赞/评论 + 操作列：编辑、删除（确认框））；筛选：状态下拉 + 关键字；"新建文章"按钮跳 `/article/edit`；标签管理弹窗（列表 + 新增/删除）；系列管理弹窗（同构）

- [ ] **Step 1: 失败测试**（mock admin-article 模块：断言表格渲染 title/status tag；点删除调 deleteArticle 并二次确认；标签弹窗增删调 saveTag/deleteTag）

- [ ] **Step 2: 跑测试确认失败 → Step 3 实现 → Step 4 跑绿**

实现要点：`<el-table>` + `el-pagination`（`@current-change` 重查）；删除用 `ElMessageBox.confirm`（测试里 mock element-plus 的 MessageBox）。

- [ ] **Step 5: Commit**

```bash
git add src/views/ArticleListView.vue src/__tests__/article-list-view.test.ts
git commit -m "feat: 文章管理列表页与标签系列管理

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: 文章编辑页（md-editor-v3）

**Files:**
- Modify: `src/views/ArticleEditView.vue`（替换占位）、Create: `src/__tests__/article-edit-view.test.ts`

**Interfaces:**
- Consumes: Task 5 的 saveDraft/publish
- Produces: 标题输入 + `md-editor` 组件（v-model 绑定 contentMd）+ 摘要输入 + "保存草稿"“发布”按钮；路由参数 `:id` 有值时预留编辑能力（一期只支持新建：列表页编辑按钮一期也跳新建，id 参数仅记录不加载——见测试）

- [ ] **Step 1: 失败测试**（mock saveDraft/publish：填标题+内容 → 点"保存草稿"调 saveDraft 并提示成功；点"发布"调 publish；标题空时禁用按钮。md-editor-v3 在测试中 `vi.mock('md-editor-v3', () => ({ default: { name: 'MdEditor', props: ['modelValue'], template: '<div class="mock-md-editor"/>' } }))`）

- [ ] **Step 2: 跑测试确认失败 → Step 3 实现 → Step 4 跑绿**

实现要点：`const form = reactive({ title:'', summary:'', contentMd:'', tags: [] as string[] })`；`md-editor v-model="form.contentMd"`；按钮 `:disabled="!form.title || !form.contentMd"`；成功后 `router.push('/article')`。

- [ ] **Step 5: Commit**

```bash
git add src/views/ArticleEditView.vue src/__tests__/article-edit-view.test.ts
git commit -m "feat: 文章编辑页（md-editor-v3）

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: 评论审核页与用户管理页

**Files:**
- Modify: `src/views/CommentAuditView.vue`、`src/views/UserManageView.vue`（替换占位）、Create: `src/__tests__/comment-audit-view.test.ts`、`src/__tests__/user-manage-view.test.ts`

**Interfaces:**
- Consumes: Task 5 的 admin-comment.ts / admin-user.ts
- Produces: 评论页——状态 tab（待审/全部）、表格（内容/文章/时间/状态）、操作（通过、拒绝（带原因输入）、删除、置顶/取消）；用户页——表格（用户名/邮箱/状态/是否管理员/注册时间）、操作（封禁/解封、设为管理员/取消管理员）

- [ ] **Step 1: 失败测试**（mock 对应 api 模块：断言待审列表渲染、通过调 audit({commentId,status:1})、封禁调 banUser、角色切换调 assignRole）

- [ ] **Step 2: 跑测试确认失败 → Step 3 实现 → Step 4 跑绿**

实现要点：两个页面均为"表格 + 分页 + 操作列"，与 Task 7 同构；拒绝评论用 `ElMessageBox.prompt` 收集原因（测试 mock）。

- [ ] **Step 5: Commit**

```bash
git add src/views/CommentAuditView.vue src/views/UserManageView.vue src/__tests__/comment-audit-view.test.ts src/__tests__/user-manage-view.test.ts
git commit -m "feat: 评论审核页与用户管理页

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: 联调验收

**Files:** 无（结果记录到后端仓库 `doc/oy-blog-admin-frontend-acceptance.md`——注意前端是独立仓库，该文档提交到后端仓库）

**前置**：本地起后端 4 服务（gateway/user/article/admin，env 注入见后端验收记录）；前端 `npm run dev`（端口 5174）。

- [ ] **Step 1: 手工清单**——① 登录（错误密码提示）；② 看板三个报表渲染且趋势补零；③ 文章列表分页/筛选；④ 新建文章保存草稿与发布（前台可见）；⑤ 标签增删；⑥ 评论审核通过/拒绝后前台可见性变化；⑦ 封禁用户后其会话失效；⑧ 退出登录后访问受保护页跳登录
- [ ] **Step 2: 修复联调问题**（若有，按 systematic-debugging 流程）
- [ ] **Step 3: 提交验收记录**

```bash
# 在前端仓库
git add -A && git commit -m "docs: 联调验收记录与修复

Co-Authored-By: Claude <noreply@anthropic.com>"
# 后端仓库记录联调结论（如有 API 契约调整）
```

---

## 计划自审记录

- **Spec 覆盖**：spec 第十节页面清单中，一期五页（登录/仪表盘/文章/评论/用户）对应 Task 3/6/7/8/9；二期页面（站点设置/媒体库/公告/通知/操作日志）不在本计划
- **类型一致性**：Task 5 导出的函数签名与 Task 6-9 测试/页面调用一致；Task 2 的 request 返回 `Promise<T>`（已解包）与 Task 5 的封装一致
- **占位符**：Task 3 的 4 个占位 view 文件名与路由一一对应并在 Task 6-9 被替换，无遗留占位
- **风险**：Element Plus 组件在 happy-dom 下的表单/弹窗交互是 mock 重点（各测试已注明 mock 点）；ECharts 在组件测试中整体 mock
