<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SubmissionTable from '@/components/SubmissionTable.vue'
import { listProblems, listSubmissionPage } from '@/services/ojApi'

const route = useRoute()
const router = useRouter()

const problem = ref(null)
const records = ref([])
const hasLoaded = ref(false)
const pageError = ref('')

// 分页状态：以服务端返回的为准，本地只做"用户想翻到哪一页"的意图传递
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

const pages = computed(() => (pageSize.value > 0 ? Math.ceil(total.value / pageSize.value) : 0))

let timer = null
let loadVersion = 0
let pollInFlight = false

function stopPolling() {
  if (timer !== null) window.clearInterval(timer)
  timer = null
}

function requestErrorMessage(error) {
  return error instanceof TypeError ? '无法连接服务器，请检查后端服务。' : error.message || '请求失败，请稍后重试。'
}

function isTerminal(item) {
  return item.status === 'DONE' || item.status === 'SE'
}

/** 当前页还有未出结果的提交时才轮询；全部结束就停，避免空转打接口。 */
function startPolling(problemId, version) {
  stopPolling()
  if (!records.value.length || records.value.every(isTerminal)) return

  timer = window.setInterval(async () => {
    if (pollInFlight) return
    pollInFlight = true
    try {
      const page = await listSubmissionPage(problemId, { pageNum: pageNum.value, pageSize: pageSize.value })
      if (version !== loadVersion || problem.value?.id !== problemId) return
      records.value = page.records || []
      total.value = page.total || 0
      pageError.value = ''
      // 服务端可能因为 total 变小把页码回退，跟着它走
      if (page.pageNum && page.pageNum !== pageNum.value) pageNum.value = page.pageNum
      if (!records.value.length || records.value.every(isTerminal)) stopPolling()
    } catch (error) {
      if (version === loadVersion) pageError.value = requestErrorMessage(error)
    } finally {
      pollInFlight = false
    }
  }, 1000)
}

/**
 * 拉取当前页数据。
 * @param {boolean} resetToFirst 是否强制回到第 1 页（切换题目时用）
 */
async function loadPage({ resetToFirst = false } = {}) {
  const version = ++loadVersion
  stopPolling()
  pageError.value = ''
  hasLoaded.value = false
  if (resetToFirst) pageNum.value = 1

  const problemId = problem.value?.id
  if (!problemId) return

  try {
    const page = await listSubmissionPage(problemId, { pageNum: pageNum.value, pageSize: pageSize.value })
    if (version !== loadVersion) return
    records.value = page.records || []
    total.value = page.total || 0
    if (page.pageNum) pageNum.value = page.pageNum
    if (page.pageSize) pageSize.value = page.pageSize
    hasLoaded.value = true
    startPolling(problemId, version)
  } catch (error) {
    if (version === loadVersion) pageError.value = requestErrorMessage(error)
  }
}

async function loadData() {
  const version = ++loadVersion
  stopPolling()
  pageError.value = ''
  hasLoaded.value = false
  records.value = []
  total.value = 0

  // 从 URL 恢复页码，支持刷新/分享链接直接定位到第 N 页
  const urlPage = Number(route.query.pageNum)
  pageNum.value = Number.isFinite(urlPage) && urlPage >= 1 ? Math.floor(urlPage) : 1

  try {
    const problemId = Number(route.query.problemId)
    const problems = await listProblems()
    if (version !== loadVersion) return
    problem.value = problems.find((item) => item.id === problemId) || problems[0] || null
    if (problem.value) await loadPage()
  } catch (error) {
    if (version === loadVersion) pageError.value = requestErrorMessage(error)
  }
}

function handlePageChange(next) {
  pageNum.value = next
  // 页码写进 URL：分享链接、刷新、浏览器后退都能回到同一页
  router.replace({ name: 'submissions', query: { ...route.query, pageNum: String(next) } })
  loadPage()
}

function handleSizeChange(next) {
  pageSize.value = next
  router.replace({ name: 'submissions', query: { ...route.query, pageNum: '1' } })
  loadPage({ resetToFirst: true })
}

function backToProblem() {
  router.push({ name: 'problem', query: { problemId: problem.value?.id } })
}

watch(() => route.query.problemId, loadData, { immediate: true })
onUnmounted(() => {
  loadVersion += 1
  stopPolling()
})
</script>

<template>
  <main class="submissions-page">
    <div class="page-head">
      <div class="head-text">
        <div class="head-top">
          <button class="back-link" type="button" @click="backToProblem">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M15 5l-7 7 7 7" />
            </svg>
            返回题目
          </button>
          <span class="crumb-sep">/</span>
          <span class="crumb">{{ problem?.title || '加载中…' }}</span>
        </div>
        <h1>提交记录</h1>
        <p>当前题目的评测结果，按提交时间倒序排列，每页 {{ pageSize }} 条。</p>
      </div>
      <div class="page-actions">
        <el-button :loading="!hasLoaded && !!problem" @click="loadPage()">刷新</el-button>
        <el-button type="success" @click="backToProblem">再次提交</el-button>
      </div>
    </div>

    <el-alert v-if="pageError" :title="pageError" type="error" show-icon :closable="false" class="request-error" />

    <section class="records-card">
      <div class="records-head">
        <h2>{{ problem?.title || '评测记录' }}</h2>
        <div class="records-meta">
          <span v-if="problem?.judgeMode" class="mode-chip">{{ problem.judgeMode }} 评测</span>
          <span class="record-count">
            共 {{ total }} 条<template v-if="pages > 1"> · 第 {{ pageNum }} / {{ pages }} 页</template>
          </span>
        </div>
      </div>

      <SubmissionTable v-if="hasLoaded" :submissions="records" :judge-mode="problem?.judgeMode || ''" />

      <div v-if="total > 0" class="pager">
        <el-pagination
          background
          layout="sizes, prev, pager, next, jumper"
          :current-page="pageNum"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          :pager-count="7"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>
  </main>
</template>

<style scoped>
.submissions-page {
  max-width: 1120px;
  margin: 0 auto;
  padding: 30px 24px 64px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--line);
}

.head-text {
  min-width: 0;
}

.head-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 12px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-size: 12px;
}

.back-link:hover {
  color: var(--accent-strong);
}

.crumb-sep {
  color: var(--ink-5);
}

.crumb {
  overflow: hidden;
  color: var(--ink-4);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-head h1 {
  margin: 0 0 6px;
  font-size: 25px;
  font-weight: 650;
  letter-spacing: -0.4px;
  color: var(--ink);
}

.page-head p {
  margin: 0;
  color: var(--ink-3);
  font-size: 13px;
}

.page-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
  padding-bottom: 2px;
}

.request-error {
  margin-top: 18px;
}

.records-card {
  margin-top: 20px;
  padding: 18px 20px 6px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.records-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line-soft);
}

.records-head h2 {
  margin: 0;
  overflow: hidden;
  font-size: 14.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.records-meta {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
}

.mode-chip {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: var(--r-pill);
  background: var(--surface-2);
  color: var(--ink-3);
  font-size: 11.5px;
}

.record-count {
  color: var(--ink-4);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 0 12px;
  border-top: 1px solid var(--line-soft);
}

@media (max-width: 720px) {
  .submissions-page {
    padding: 22px 16px 48px;
  }

  .page-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 14px;
  }

  .records-card {
    padding: 16px 14px 4px;
  }

  .pager {
    justify-content: center;
  }
}
</style>
