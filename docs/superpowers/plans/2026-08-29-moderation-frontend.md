# 文章审核前端 实现计划（创作中心 + 管理端审核页）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐文章 AI 审核的前端配合项：管理端新增"文章审核"页（对接已就绪的 `/admin/moderation` 端点）；博客前端创作中心新增"审核中"列表页 + 状态徽标 + publish 结果提示 + 15 秒自动轮询。

**Architecture:** 两个独立 Vue 3 仓库并行改动：`oy-blog-admin`（管理端，照抄 CommentAuditView 三件套范式）与 `oy-blog-front-dev1`（博客前端，扩展 useCreatorList 状态参数 + CreatorArticleTable 状态徽标 + 新路由页 + 轮询）。后端零改动（端点与字段均已就绪）。

**Tech Stack:** Vue 3.4 + TypeScript + Element Plus（admin）/ 原生 table + vue-i18n（blog-front）+ Vitest。

**Spec:** `docs/superpowers/specs/2026-08-29-async-moderation-design.md`（§5.4 前端配合项）+ `docs/superpowers/specs/2026-08-29-article-ai-moderation-design.md`（§11 验收清单）

## Global Constraints

- **仓库 A（管理端）**：`G:\JavaWorkSpace\frontend\oy-blog-admin`，分支 **master**——**不在 master 直接提交**：新建本地分支 `feat-moderation` 提交，不 push（controller 已裁决，同 BlogAgent 先例）。
- **仓库 B（博客前端）**：`G:\JavaWorkSpace\frontend\oy-blog-front-dev1`，分支 dev1，直接提交不 push。
- Node/npm 用系统自带；测试用 Vitest（两仓库已有约定；admin 测试目录 `src/__tests__/*.test.ts`）。
- **后端契约（照抄，勿改后端）**：
  - `POST /admin/moderation/page` `{page,size}` → `PageVo<ArticleModerationItem[]>`（currentPage/pageSize/total/totalPages/data）
  - `POST /admin/moderation/audit` `{articleId, approve:boolean, reason?}` → boolean
  - `ArticleModerationItem`：`{articleId, kind:'NEW'|'EDIT', title, authorId, summary, reviewReason, createdAt, pendingTitle?, pendingSummary?}`（kind=NEW → title 即待审标题；kind=EDIT → title 为当前对外旧标题、pendingTitle 为待生效新标题）
  - `GET /article/read/me?status=xxx`（listMine 端点）接受任意 status 字符串：`ai_reviewing`/`pending_review`/`rejected` 均可查；ArticleInfo 已含 `reviewStatus`/`reviewReason`（后端 Task 9 已加，前端类型要补）
  - `POST /article/publish` 返回 `data={articleId, verdict, reason}`；verdict ∈ exempt/approved/rejected/ai_reviewing
- 状态展示语义：`reviewStatus==='ai_reviewing'` 且 status==='published' → "编辑审核中"（旧版展示中）；否则按 reviewStatus 映射（ai_reviewing→AI 审核中、manual→待人工审核、rejected→已驳回+reason、exempt/approved→不显示徽标）
- 博客前端 i18n：新文案必须加 zh/en 两套 key（位置 grep `creator.published` 找到既有 locale 文件）
- 中文 UI 文案；提交信息 `feat: 中文描述` + Co-Authored-By 行
- 每任务 RED→GREEN（纯函数逻辑可测；UI 结构靠模板照抄 + 手动冒烟）

---

## 文件结构总览

**仓库 A（oy-blog-admin）**
- Modify `src/types/admin.ts`（+ArticleModerationItem 等）
- Create `src/api/admin-moderation.ts`
- Create `src/views/ArticleModerationView.vue`
- Create `src/__tests__/moderation-view.test.ts`（纯映射函数测试）
- Modify `src/router/index.ts`、`src/components/AdminLayout.vue`

**仓库 B（oy-blog-front-dev1）**
- Modify `src/api/article.ts`（ArticleInfo +reviewStatus/reviewReason；MyArticlesParams.status 扩值；publishArticle 返回泛型）
- Create `src/utils/reviewStatus.ts`（徽标映射 + verdict 文案映射，纯函数）
- Create `src/__tests__/reviewStatus.test.ts`
- Modify `src/components/CreatorArticleTable.vue`（状态徽标列 + status prop 扩 'reviewing'）
- Modify `src/composables/useCreatorList.ts`（status 类型 + publishDraft 参数化 + 返回 verdict）
- Create `src/views/CreatorReviewing.vue`
- Modify `src/views/CreatorCenter.vue`、`src/components/CreatorSidebar.vue`、`src/router/index.ts`
- Modify `src/views/ArticleEditor.vue`（submitArticle 按 verdict 分支）
- Modify `src/views/CreatorPublished.vue`（编辑审核中才轮询）

---

## Task 1: 管理端文章审核页（仓库 A）

**Files:**
- Modify: `/g/JavaWorkSpace/frontend/oy-blog-admin/src/types/admin.ts`
- Create: `/g/JavaWorkSpace/frontend/oy-blog-admin/src/api/admin-moderation.ts`
- Create: `/g/JavaWorkSpace/frontend/oy-blog-admin/src/views/ArticleModerationView.vue`
- Create: `/g/JavaWorkSpace/frontend/oy-blog-admin/src/__tests__/moderation-view.test.ts`
- Modify: `/g/JavaWorkSpace/frontend/oy-blog-admin/src/router/index.ts`
- Modify: `/g/JavaWorkSpace/frontend/oy-blog-admin/src/components/AdminLayout.vue`

**Interfaces:**
- Consumes: 已有 `src/api/request.ts`（request<T> 直接解包 data）、`types/admin.ts` 的 PageDto/PageVo
- Produces: 页面 `/moderation` + 菜单项「文章审核」

- [ ] **Step 1: 建分支并写失败测试**

```bash
cd /g/JavaWorkSpace/frontend/oy-blog-admin && git checkout -b feat-moderation
```

`src/__tests__/moderation-view.test.ts`（先建测试，跑红——映射函数还不存在；映射函数放在 `src/views/moderationMeta.ts`，与 Vue 组件分离以便测试）：

```ts
import { describe, expect, it } from 'vitest'
import { KIND_LABEL, KIND_TYPE, itemTitle, itemSubtitle } from '../views/moderationMeta'

describe('moderationMeta', () => {
  it('kind 标签映射', () => {
    expect(KIND_LABEL.NEW).toBe('新文章')
    expect(KIND_LABEL.EDIT).toBe('待审编辑')
    expect(KIND_TYPE.NEW).toBe('primary')
    expect(KIND_TYPE.EDIT).toBe('warning')
  })

  it('NEW 项标题为待审标题、无副标题', () => {
    const item = { articleId: 'a1', kind: 'NEW', title: '待审文章', authorId: 'u1', summary: '', reviewReason: 'AI 觉得有歧义', createdAt: '2026-08-29 10:00:00' }
    expect(itemTitle(item)).toBe('待审文章')
    expect(itemSubtitle(item)).toBeNull()
  })

  it('EDIT 项标题为待生效新标题、副标题为当前旧标题', () => {
    const item = { articleId: 'a1', kind: 'EDIT', title: '旧标题', authorId: 'u1', summary: '', reviewReason: '', createdAt: '2026-08-29 10:00:00', pendingTitle: '新标题' }
    expect(itemTitle(item)).toBe('新标题')
    expect(itemSubtitle(item)).toBe('当前对外：旧标题')
  })
})
```

Run: `cd /g/JavaWorkSpace/frontend/oy-blog-admin && npx vitest run src/__tests__/moderation-view.test.ts`
Expected: FAIL（模块不存在）

- [ ] **Step 2: 类型 + API**

`src/types/admin.ts` 追加：

```ts
export interface ArticleModerationItem {
  articleId: string
  /** NEW=待审新文章；EDIT=已发布文章的待审编辑 */
  kind: 'NEW' | 'EDIT'
  /** NEW=待审文章标题；EDIT=当前对外展示的旧标题 */
  title: string
  authorId: string
  summary: string
  /** AI 转人工理由 */
  reviewReason: string
  createdAt: string
  /** 仅 EDIT：待生效标题 */
  pendingTitle?: string
  /** 仅 EDIT：待生效摘要 */
  pendingSummary?: string
}

export interface ArticleModerationAuditDto {
  articleId: string
  approve: boolean
  reason?: string
}
```

`src/api/admin-moderation.ts`：

```ts
import request from './request'
import type { ArticleModerationAuditDto, ArticleModerationItem, PageDto, PageVo } from '../types/admin'

export function moderationPage(dto: PageDto): Promise<PageVo<ArticleModerationItem[]>> {
  return request<PageVo<ArticleModerationItem[]>>({ url: '/admin-service/admin/moderation/page', method: 'POST', data: dto })
}

export function auditModeration(dto: ArticleModerationAuditDto): Promise<boolean> {
  return request<boolean>({ url: '/admin-service/admin/moderation/audit', method: 'POST', data: dto })
}
```

- [ ] **Step 3: 映射函数 + 页面**

`src/views/moderationMeta.ts`：

```ts
import type { ArticleModerationItem } from '../types/admin'

export const KIND_LABEL: Record<string, string> = { NEW: '新文章', EDIT: '待审编辑' }
export const KIND_TYPE: Record<string, 'primary' | 'warning' | 'info'> = { NEW: 'primary', EDIT: 'warning' }

/** 列表标题：EDIT 显示待生效新标题，NEW 显示待审标题 */
export function itemTitle(item: ArticleModerationItem): string {
  return item.kind === 'EDIT' && item.pendingTitle ? item.pendingTitle : item.title
}

/** 副标题：仅 EDIT 显示当前对外旧标题 */
export function itemSubtitle(item: ArticleModerationItem): string | null {
  return item.kind === 'EDIT' ? `当前对外：${item.title}` : null
}
```

`src/views/ArticleModerationView.vue`（照抄 CommentAuditView 结构）：

```vue
<template>
  <div class="moderation-view">
    <div class="toolbar">
      <span class="hint">AI 判"有歧义"的文章在此人工审核：通过即发布/替换生效，驳回即退回作者。</span>
    </div>

    <el-table :data="rows" class="moderation-table" data-test="moderation-table">
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="KIND_TYPE[row.kind] ?? 'info'" size="small" data-test="kind-tag">
            {{ KIND_LABEL[row.kind] ?? row.kind }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="标题" min-width="240">
        <template #default="{ row }">
          <div>{{ itemTitle(row) }}</div>
          <div v-if="itemSubtitle(row)" class="subtitle">{{ itemSubtitle(row) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="AI 理由" min-width="180">
        <template #default="{ row }">{{ row.reviewReason || '-' }}</template>
      </el-table-column>
      <el-table-column label="提交时间" width="180">
        <template #default="{ row }">{{ row.createdAt }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="success" :data-test="`approve-${row.articleId}`" @click="onAudit(row, true)">通过</el-button>
          <el-button link type="danger" :data-test="`reject-${row.articleId}`" @click="onReject(row)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        data-test="pagination"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import { auditModeration, moderationPage } from '../api/admin-moderation'
import type { ArticleModerationItem } from '../types/admin'
import { KIND_LABEL, KIND_TYPE, itemSubtitle, itemTitle } from './moderationMeta'

const rows = ref<ArticleModerationItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10

async function reload() {
  const res = await moderationPage({ page: page.value, size: pageSize })
  rows.value = res.data
  total.value = res.total
}

function onPageChange(p: number) {
  page.value = p
  reload()
}

async function onAudit(row: ArticleModerationItem, approve: boolean, reason?: string) {
  await auditModeration({ articleId: row.articleId, approve, reason })
  ElMessage.success(approve ? '已通过' : '已驳回')
  reload()
}

// 驳回需先输入原因（取消则静默）
async function onReject(row: ArticleModerationItem) {
  let value: string
  try {
    const res = await ElMessageBox.prompt(`请输入驳回「${itemTitle(row)}」的原因`, '驳回文章', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '原因（必填）',
    })
    value = res.value
  } catch {
    return
  }
  await onAudit(row, false, value)
}

onMounted(reload)
</script>

<style scoped>
.moderation-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.subtitle {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.moderation-table {
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}

.pager {
  display: flex;
  justify-content: flex-end;
}
</style>
```

- [ ] **Step 4: 路由 + 菜单**

`src/router/index.ts` buildRoutes 数组在 `/comment` 行后加：

```ts
    { path: '/moderation', component: () => import('../views/ArticleModerationView.vue') },
```

`src/components/AdminLayout.vue` 菜单在「评论审核」后加：

```vue
        <el-menu-item index="/moderation">文章审核</el-menu-item>
```

- [ ] **Step 5: 跑绿 + 全量测试 + 构建检查**

Run: `cd /g/JavaWorkSpace/frontend/oy-blog-admin && npx vitest run`（全量）+ `npx vue-tsc --noEmit`（类型检查；若仓库未配 vue-tsc 脚本则跳过）
Expected: 全绿、类型无误

- [ ] **Step 6: 提交（feat-moderation 分支）**

```bash
cd /g/JavaWorkSpace/frontend/oy-blog-admin && git add src/types/admin.ts src/api/admin-moderation.ts src/views/ArticleModerationView.vue src/views/moderationMeta.ts src/__tests__/moderation-view.test.ts src/router/index.ts src/components/AdminLayout.vue && git commit -m "feat: 文章审核页（待审队列+通过/驳回，对接 /admin/moderation）"
```

---

## Task 2: 博客前端类型扩展 + 状态徽标（仓库 B）

**Files:**
- Modify: `/g/JavaWorkSpace/frontend/oy-blog-front-dev1/src/api/article.ts`
- Create: `/g/JavaWorkSpace/frontend/oy-blog-front-dev1/src/utils/reviewStatus.ts`
- Create: `/g/JavaWorkSpace/frontend/oy-blog-front-dev1/src/__tests__/reviewStatus.test.ts`
- Modify: `/g/JavaWorkSpace/frontend/oy-blog-front-dev1/src/components/CreatorArticleTable.vue`
- Modify: `/g/JavaWorkSpace/frontend/oy-blog-front-dev1/src/composables/useCreatorList.ts`

**Interfaces:**
- Produces（Task 3/4 依赖）:
  - `ArticleInfo.reviewStatus?: string; reviewReason?: string`
  - `MyArticlesParams.status: 'published' | 'draft' | 'ai_reviewing' | 'pending_review' | 'rejected'`
  - `publishArticle(data): Promise<ResultObject<PublishResultData>>`，`PublishResultData = { articleId: string; verdict: string; reason?: string }`
  - `reviewStatusMeta(reviewStatus: string | undefined, articleStatus: string | undefined): { label: string; tone: 'info' | 'warning' | 'danger' } | null`（null=不显示）
  - `verdictFeedback(verdict: string, reason?: string): { text: string; tone: 'success' | 'error' | 'info' }`（Task 4 用）
  - `useCreatorList(status, pageSize)`：status 参数扩为上述 5 值；`publishDraft(id, isDraft: boolean): Promise<{ ok: boolean; verdict?: string; reason?: string }>`

- [ ] **Step 1: 写失败测试** `src/__tests__/reviewStatus.test.ts`

```ts
import { describe, expect, it } from 'vitest'
import { reviewStatusMeta, verdictFeedback } from '../utils/reviewStatus'

describe('reviewStatusMeta', () => {
  it('编辑审核中：published 且 ai_reviewing', () => {
    expect(reviewStatusMeta('ai_reviewing', 'published')).toEqual({ label: '编辑审核中', tone: 'warning' })
  })
  it('AI 审核中（新文章）', () => {
    expect(reviewStatusMeta('ai_reviewing', 'ai_reviewing')).toEqual({ label: 'AI 审核中', tone: 'info' })
  })
  it('待人工审核', () => {
    expect(reviewStatusMeta('manual', 'pending_review')).toEqual({ label: '待人工审核', tone: 'warning' })
  })
  it('已驳回（携带理由展示）', () => {
    expect(reviewStatusMeta('rejected', 'rejected')).toEqual({ label: '已驳回', tone: 'danger' })
  })
  it('approved/exempt 不显示徽标', () => {
    expect(reviewStatusMeta('approved', 'published')).toBeNull()
    expect(reviewStatusMeta('exempt', 'published')).toBeNull()
    expect(reviewStatusMeta(undefined, 'published')).toBeNull()
  })
})

describe('verdictFeedback', () => {
  it('ai_reviewing → 已提交审核', () => {
    expect(verdictFeedback('ai_reviewing')).toEqual({ text: '已提交 AI 审核，请稍候查看结果', tone: 'info' })
  })
  it('rejected → 驳回文案含原因', () => {
    expect(verdictFeedback('rejected', '广告引流')).toEqual({ text: '审核未通过：广告引流', tone: 'error' })
  })
  it('approved/exempt → 发布成功', () => {
    expect(verdictFeedback('approved')).toEqual({ text: '发布成功', tone: 'success' })
    expect(verdictFeedback('exempt')).toEqual({ text: '发布成功', tone: 'success' })
  })
  it('未知 verdict 兜底发布成功文案', () => {
    expect(verdictFeedback('unknown')).toEqual({ text: '发布成功', tone: 'success' })
  })
})
```

Run: `cd /g/JavaWorkSpace/frontend/oy-blog-front-dev1 && npx vitest run src/__tests__/reviewStatus.test.ts`
Expected: FAIL（模块不存在）

- [ ] **Step 2: 实现 utils + 类型**

`src/utils/reviewStatus.ts`：

```ts
/** 审核状态徽标映射。返回 null 表示不显示徽标。 */
export function reviewStatusMeta(
  reviewStatus: string | undefined,
  articleStatus: string | undefined
): { label: string; tone: 'info' | 'warning' | 'danger' } | null {
  if (!reviewStatus) return null
  // 已发布文章正在审编辑 → "编辑审核中"（旧版对外展示中）
  if (reviewStatus === 'ai_reviewing' && articleStatus === 'published') {
    return { label: '编辑审核中', tone: 'warning' }
  }
  switch (reviewStatus) {
    case 'ai_reviewing':
      return { label: 'AI 审核中', tone: 'info' }
    case 'manual':
      return { label: '待人工审核', tone: 'warning' }
    case 'rejected':
      return { label: '已驳回', tone: 'danger' }
    default:
      return null // approved/exempt 等正常状态不显示徽标
  }
}

/** publish 返回 verdict → 提示文案与色调 */
export function verdictFeedback(verdict: string, reason?: string): { text: string; tone: 'success' | 'error' | 'info' } {
  if (verdict === 'ai_reviewing') {
    return { text: '已提交 AI 审核，请稍候查看结果', tone: 'info' }
  }
  if (verdict === 'rejected') {
    return { text: reason ? `审核未通过：${reason}` : '审核未通过，请修改后重新提交', tone: 'error' }
  }
  // approved / exempt / 未知 → 发布成功
  return { text: '发布成功', tone: 'success' }
}
```

`src/api/article.ts`：

1. `ArticleInfo` 接口末尾加：

```ts
  // 审核字段（后端 Task 9 新增，创作中心状态徽标用）
  reviewStatus?: string;
  reviewReason?: string;
```

2. `MyArticlesParams.status` 改为：

```ts
export type CreatorArticleStatus = 'published' | 'draft' | 'ai_reviewing' | 'pending_review' | 'rejected';

export interface MyArticlesParams {
  status: CreatorArticleStatus;
  pageNum?: number;
  pageSize?: number;
}
```

3. `publishArticle` 改为：

```ts
export interface PublishResultData {
  articleId: string;
  verdict: string;
  reason?: string;
}

export const publishArticle = (data: ArticleSaveDto) => {
  return request.post<any, ResultObject<PublishResultData>>(baseUrl + '/article/publish', data);
};
```

（`ResultObject` 定义改为泛型 `data: T`——检查现有 `ResultObject` 的 `data: any` 用法：改 `data: T` 需同步 `export interface ResultObject<T = any>`，其余调用点不受影响）

- [ ] **Step 3: 表格徽标 + useCreatorList**

`CreatorArticleTable.vue`：
1. props 的 status 扩为 `'published' | 'draft' | 'reviewing'`
2. 时间列：`status === 'published' ? publishAt : updatedAt` 保持不变（reviewing 走 updatedAt）✓ 现有逻辑已兼容
3. 标题列链接：非 published 时走编辑页 ✓ 现有逻辑已兼容
4. 在标题列后加状态列（th 仅当 status !== 'published' 或恒显示——直接恒显示）：

```vue
          <th class="col-status">{{ $t('creator.status') }}</th>
```

行内：

```vue
          <td class="col-status">
            <span
              v-if="statusMeta(article)"
              :class="['status-badge', `status-badge--${statusMeta(article)!.tone}`]"
              :title="article.reviewReason || ''"
            >
              {{ statusMeta(article)!.label }}
            </span>
          </td>
```

script 加：

```ts
import { reviewStatusMeta } from '../utils/reviewStatus';

function statusMeta(article: ArticleInfo) {
  return reviewStatusMeta(article.reviewStatus, article.status);
}
```

5. 操作列：reviewing 与 draft 相同（编辑/发布/删除）——模板 `v-else` 分支已覆盖（status !== 'published' 走 else）✓；仅当 reviewStatus 存在徽标（即被驳回文章）时也应有"编辑"……已覆盖。
6. 样式：`.col-status { width: 110px; }` + badge 三种色调（info 灰蓝/warning 橙/danger 红，沿用 variables 或直接色值）

`useCreatorList.ts`：
1. status 参数类型改 `CreatorArticleStatus`（import 自 article.ts）
2. `publishDraft(id, isDraft: boolean)`：

```ts
  const publishDraft = async (id: string, isDraft: boolean): Promise<{ ok: boolean; verdict?: string; reason?: string }> => {
    try {
      const res = await publishArticle({ id, title: '', contentMd: '', contentHtml: '' });
      if (res.isSuccess) {
        await load(currentPage.value);
        if (articles.value.length === 0 && currentPage.value > 1) {
          await load(currentPage.value - 1);
        }
        if (isDraft) {
          decrementDraftCount();
        }
        refreshDraftCount();
        return { ok: true, verdict: res.data?.verdict, reason: res.data?.reason };
      }
      return { ok: false };
    } catch (error) {
      console.error('Failed to publish draft:', error);
      return { ok: false };
    }
  };
```

- [ ] **Step 4: i18n 文案**（grep `creator.published` 找到 locale 文件，zh/en 都加）

```ts
creator.status: '状态'
creator.reviewing: '审核中'
creator.reviewingAi: 'AI 审核中'
creator.reviewingManual: '待人工审核'
creator.reviewingRejected: '已驳回'
```

（值以文件实际格式为准——json/ts，照抄相邻 key 格式）

- [ ] **Step 5: 跑绿 + 全量**

Run: `cd /g/JavaWorkSpace/frontend/oy-blog-front-dev1 && npx vitest run`
Expected: 全绿

- [ ] **Step 6: 提交**

```bash
cd /g/JavaWorkSpace/frontend/oy-blog-front-dev1 && git add src/api/article.ts src/utils/reviewStatus.ts src/__tests__/reviewStatus.test.ts src/components/CreatorArticleTable.vue src/composables/useCreatorList.ts <i18n 文件> && git commit -m "feat: 创作中心审核状态徽标 + 类型扩展（reviewStatus/verdict）"
```

---

## Task 3: 博客前端"审核中"列表页 + 路由（仓库 B）

**Files:**
- Create: `/g/JavaWorkSpace/frontend/oy-blog-front-dev1/src/views/CreatorReviewing.vue`
- Modify: `/g/JavaWorkSpace/frontend/oy-blog-front-dev1/src/views/CreatorCenter.vue`（第三个 tab）
- Modify: `/g/JavaWorkSpace/frontend/oy-blog-front-dev1/src/components/CreatorSidebar.vue`（侧栏第三项）
- Modify: `/g/JavaWorkSpace/frontend/oy-blog-front-dev1/src/router/index.ts`（/creator/reviewing 路由）
- Modify: `/g/JavaWorkSpace/frontend/oy-blog-front-dev1/src/views/CreatorDrafts.vue`（publishDraft 签名适配）

**Interfaces:**
- Consumes: Task 2 的类型与 useCreatorList 扩展

- [ ] **Step 1: 路由**

`src/router/index.ts` 在 `drafts` 后加：

```ts
        {
          path: 'reviewing',
          name: 'creator-reviewing',
          component: () => import('../views/CreatorReviewing.vue')
        },
```

- [ ] **Step 2: CreatorCenter tab + CreatorSidebar**

`CreatorCenter.vue` 在草稿 tab 后加：

```vue
        <router-link
          to="/creator/reviewing"
          class="tab-btn"
          active-class="tab-btn--active"
        >
          {{ t('creator.reviewing') }}
        </router-link>
```

`CreatorSidebar.vue` 照既有 navGroups 结构加「审核中」项（grep navGroups 结构后仿写，icon 用既有可用的或省略）。

- [ ] **Step 3: CreatorReviewing.vue**

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useCreatorList } from '../composables/useCreatorList';
import CreatorArticleTable from '../components/CreatorArticleTable.vue';
import CreatorPagination from '../components/CreatorPagination.vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import type { CreatorArticleStatus } from '../api/article';
import { useToast } from '../composables/useToast';

const router = useRouter();
const { t } = useI18n();
const toast = useToast();

const status = ref<CreatorArticleStatus>('ai_reviewing');
const { articles, currentPage, totalPages, isLoading, load, removeArticle, publishDraft } =
  useCreatorList('ai_reviewing');

onMounted(() => load(1));

const tabs: { value: CreatorArticleStatus; label: string }[] = [
  { value: 'ai_reviewing', label: t('creator.reviewingAi') },
  { value: 'pending_review', label: t('creator.reviewingManual') },
  { value: 'rejected', label: t('creator.reviewingRejected') },
];

// 注意：useCreatorList 的 status 是闭包捕获的，切换 tab 需重建列表状态——
// 用 key 重挂载组件比改 composable 简单：外层用 <component :key="status"> 或直接 v-if 三个实例。
// 实现选型：watch status 变更 → 通过 useCreatorList 暴露的 load 无法换 status；
// 因此改为直接调 getMyArticles（同 useCreatorList.load 逻辑，status 为响应式参数）。
</script>
```

> 注意：`useCreatorList` 的 status 是闭包参数、`load` 不接收新 status——Tab 切换不能复用。**裁决**（controller 已定）：`useCreatorList` 增加响应式支持成本高于收益，CreatorReviewing.vue **不使用 useCreatorList**，直接在本组件内写列表逻辑（照 useCreatorList.load 的 20 行实现，status 用 ref 响应式传入 getMyArticles）：

```vue
<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { getMyArticles, deleteArticle, publishArticle } from '../api/article';
import type { ArticleInfo, CreatorArticleStatus } from '../api/article';
import { useCreatorStore } from '../store/creator';
import { useToast } from '../composables/useToast';
import { verdictFeedback } from '../utils/reviewStatus';
import CreatorArticleTable from '../components/CreatorArticleTable.vue';
import CreatorPagination from '../components/CreatorPagination.vue';

const router = useRouter();
const { t } = useI18n();
const toast = useToast();
const { refreshDraftCount } = useCreatorStore();

const status = ref<CreatorArticleStatus>('ai_reviewing');
const articles = ref<ArticleInfo[]>([]);
const currentPage = ref(1);
const totalPages = ref(0);
const isLoading = ref(false);

const tabs: { value: CreatorArticleStatus; label: string }[] = [
  { value: 'ai_reviewing', label: t('creator.reviewingAi') },
  { value: 'pending_review', label: t('creator.reviewingManual') },
  { value: 'rejected', label: t('creator.reviewingRejected') },
];

async function load(pageNum: number) {
  isLoading.value = true;
  try {
    const res = await getMyArticles({ status: status.value, pageNum, pageSize: 10 });
    if (res.isSuccess && res.data) {
      articles.value = res.data.data;
      totalPages.value = res.data.totalPages;
      currentPage.value = res.data.currentPage;
    }
  } catch {
    // 拦截器已提示
  } finally {
    isLoading.value = false;
  }
}

watch(status, () => { currentPage.value = 1; load(1); });

// 15 秒轮询：审核结果异步落库，前端定时刷新状态（仅停留在本页时）
let timer: ReturnType<typeof setInterval> | null = null;
onMounted(() => { load(1); timer = setInterval(() => load(currentPage.value), 15000); });
onUnmounted(() => { if (timer) clearInterval(timer); });

const handleEdit = (id: string) => router.push(`/creator/articles/${id}/edit`);

const handleDelete = async (id: string) => {
  if (!window.confirm('确定要删除这篇文章吗？删除后无法恢复。')) return;
  try {
    const res = await deleteArticle(id);
    if (res.isSuccess) await load(currentPage.value);
  } catch { /* 拦截器已提示 */ }
};

const handlePublish = async (id: string) => {
  // 已驳回文章"重新发布"：走 publish 接口重新触发审核
  try {
    const res = await publishArticle({ id, title: '', contentMd: '', contentHtml: '' });
    if (res.isSuccess) {
      const fb = verdictFeedback(res.data?.verdict, res.data?.reason);
      toast.addToast(fb.text, fb.tone === 'success' ? 'success' : fb.tone === 'error' ? 'error' : 'info');
      refreshDraftCount();
      await load(currentPage.value);
    }
  } catch { /* 拦截器已提示 */ }
};
</script>

<template>
  <div class="reviewing-page">
    <div class="sub-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        :class="['sub-tab', { 'sub-tab--active': status === tab.value }]"
        @click="status = tab.value"
      >
        {{ tab.label }}
      </button>
    </div>
    <CreatorArticleTable
      :articles="articles"
      status="reviewing"
      :is-loading="isLoading"
      @edit="handleEdit"
      @delete="handleDelete"
      @publish="handlePublish"
    />
    <CreatorPagination
      :current-page="currentPage"
      :total-pages="totalPages"
      @page-change="load"
    />
  </div>
</template>

<style lang="scss" scoped>
@use '../styles/variables' as *;

.sub-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
}

.sub-tab {
  padding: 8px 16px;
  border: 1px solid $color-border;
  border-radius: $radius-sm;
  background: none;
  cursor: pointer;
  color: $color-text-secondary;

  &--active {
    color: $color-accent-primary;
    border-color: $color-accent-primary;
  }
}
</style>
```

- [ ] **Step 4: CreatorDrafts.vue 适配**

`useCreatorList('draft')` 解构出 `publishDraft` 的调用处改为 `publishDraft(id, true)`；成功后用返回的 verdict 弹提示（import verdictFeedback + useToast；若该文件已有 toast 先例照抄）。

- [ ] **Step 5: 构建检查 + 提交**

Run: `cd /g/JavaWorkSpace/frontend/oy-blog-front-dev1 && npx vitest run`（回归）
Commit:

```bash
cd /g/JavaWorkSpace/frontend/oy-blog-front-dev1 && git add src/views/CreatorReviewing.vue src/views/CreatorCenter.vue src/components/CreatorSidebar.vue src/router/index.ts src/views/CreatorDrafts.vue && git commit -m "feat: 创作中心审核中列表页（AI审核中/待人工/已驳回 三子页 + 15s 轮询）"
```

---

## Task 4: 博客前端 publish 结果处理 + 已发布页轮询（仓库 B）

**Files:**
- Modify: `/g/JavaWorkSpace/frontend/oy-blog-front-dev1/src/views/ArticleEditor.vue`（submitArticle 按 verdict 分支）
- Modify: `/g/JavaWorkSpace/frontend/oy-blog-front-dev1/src/views/CreatorPublished.vue`（编辑审核中才轮询）

**Interfaces:**
- Consumes: Task 2 的 `verdictFeedback` 与类型

- [ ] **Step 1: ArticleEditor.submitArticle 改造**

`src/views/ArticleEditor.vue` 的 submitArticle 成功分支改为（import verdictFeedback + useToast 已存在——该文件已有 addToast 用法）：

```ts
    if (res.isSuccess) {
      const fb = verdictFeedback(res.data?.verdict, res.data?.reason);
      addToast(fb.text, fb.tone === 'success' ? 'success' : fb.tone === 'error' ? 'error' : 'info');
      refreshDraftCount();
      if (res.data?.verdict === 'ai_reviewing') {
        router.push('/creator/reviewing');
      } else if (res.data?.verdict === 'rejected') {
        // 驳回：留在编辑页让作者修改后重发
        return;
      } else {
        router.push('/creator/published');
      }
    }
```

（`res.data` 的 verdict 可能为 undefined——老后端/豁免路径兼容：undefined 走 else 跳 published，行为与现状一致）

- [ ] **Step 2: CreatorPublished.vue 轮询**

```vue
<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useCreatorList } from '../composables/useCreatorList';
import CreatorArticleTable from '../components/CreatorArticleTable.vue';
import CreatorPagination from '../components/CreatorPagination.vue';

const router = useRouter();
const { articles, currentPage, totalPages, isLoading, load, removeArticle } = useCreatorList('published');

onMounted(() => {
  load(1);
});

// 有"编辑审核中"的文章时才轮询（旧版展示中，等待审核结果替换生效）
const hasReviewingEdit = computed(() => articles.value.some(a => a.reviewStatus === 'ai_reviewing'));
let timer: ReturnType<typeof setInterval> | null = null;
watch(hasReviewingEdit, (on) => {
  if (timer) { clearInterval(timer); timer = null; }
  if (on) {
    timer = setInterval(() => load(currentPage.value), 15000);
  }
});
onUnmounted(() => { if (timer) clearInterval(timer); });

const handleEdit = (id: string) => {
  router.push(`/creator/articles/${id}/edit`);
};

const handleDelete = async (id: string) => {
  if (!window.confirm('确定要删除这篇文章吗？删除后无法恢复。')) return;
  await removeArticle(id);
};

const handlePageChange = (page: number) => {
  load(page);
};
</script>
```

（模板不变）

- [ ] **Step 3: 回归 + 提交**

Run: `cd /g/JavaWorkSpace/frontend/oy-blog-front-dev1 && npx vitest run`
Commit:

```bash
cd /g/JavaWorkSpace/frontend/oy-blog-front-dev1 && git add src/views/ArticleEditor.vue src/views/CreatorPublished.vue && git commit -m "feat: 发布结果按 verdict 提示与跳转 + 编辑审核中文章轮询"
```

---

## Task 5: 两端手动冒烟清单 + 收尾

- [ ] **Step 1（管理端）**：`npx vite` 起 dev server → 登录 admin → 菜单出现「文章审核」→ 队列列表渲染（有数据时 NEW/EDIT 徽标、通过/驳回按钮、驳回弹理由框）；无数据时空表正常。有真实待审数据时验证通过/驳回后列表刷新。
- [ ] **Step 2（博客前端）**：dev server → 创作中心出现「审核中」tab → 三子 tab 切换查询；发布一篇文章 → toast"已提交 AI 审核" → 跳审核中列表；已驳回文章显示红色徽标、hover 显示理由；重新发布成功。已发布列表出现"编辑审核中"文章时自动 15s 轮询（Network 面板可见周期性 read/me 请求）。
- [ ] **Step 3**：把验收结果补充进 `G:\JavaWorkSpace\oy-blog-dev1\doc\article-moderation-acceptance.md` 的前端配合项小节（勾掉 2 条待办），提交该文档到 oy-blog dev1。

---

## 自审记录

**Spec 覆盖检查：**
- 异步 spec §5.4 前端配合项 ①（verdict 提示）→ Task 4 Step 1；②（列表轮询+状态展示）→ Task 2/3/4
- 同步 spec §12 风险"管理端审核 UI 归属"→ Task 1（后端端点早已就绪）
- §11 验收清单前端相关项 → Task 5 冒烟清单

**已裁决的偏差（实现者按此执行）：**
- `useCreatorList` 闭包 status 无法响应式切换 → CreatorReviewing.vue 不复用 composable，直接写响应式列表逻辑（裁决在 Task 3 Step 3 注释内）
- 管理端仓库在 master → 建 feat-moderation 分支提交不 push（Ruling，同 BlogAgent 先例）
- 博客前端无轮询先例 → 自写 setInterval + onUnmounted 清理模式，不引第三方轮询库
