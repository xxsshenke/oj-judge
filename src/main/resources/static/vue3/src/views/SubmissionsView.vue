<script setup>
import { onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SubmissionTable from '@/components/SubmissionTable.vue'
import { listProblems, listSubmissions } from '@/services/ojApi'

const route = useRoute()
const router = useRouter()
const problem = ref(null)
const submissions = ref([])
const hasLoadedSubmissions = ref(false)
const pageError = ref('')
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

function startPolling(problemId, version) {
  stopPolling()
  if (!submissions.value.length || submissions.value.every(isTerminal)) return

  timer = window.setInterval(async () => {
    if (pollInFlight) return
    pollInFlight = true
    try {
      const updated = await listSubmissions(problemId)
      if (version !== loadVersion || problem.value?.id !== problemId) return
      submissions.value = updated
      pageError.value = ''
      if (!updated.length || updated.every(isTerminal)) stopPolling()
    } catch (error) {
      if (version === loadVersion) pageError.value = requestErrorMessage(error)
    } finally {
      pollInFlight = false
    }
  }, 1000)
}

async function loadData() {
  const version = ++loadVersion
  stopPolling()
  pageError.value = ''
  hasLoadedSubmissions.value = false
  submissions.value = []
  try {
    const problemId = Number(route.query.problemId)
    const problems = await listProblems()
    if (version !== loadVersion) return
    problem.value = problems.find((item) => item.id === problemId) || problems[0] || null

    if (problem.value) {
      const records = await listSubmissions(problem.value.id)
      if (version !== loadVersion) return
      submissions.value = records
      hasLoadedSubmissions.value = true
      startPolling(problem.value.id, version)
    }
  } catch (error) {
    if (version === loadVersion) pageError.value = requestErrorMessage(error)
  }
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
        <p>当前题目的全部评测结果，按提交时间倒序排列。</p>
      </div>
      <div class="page-actions">
        <el-button @click="loadData">刷新</el-button>
        <el-button type="success" @click="backToProblem">再次提交</el-button>
      </div>
    </div>

    <el-alert v-if="pageError" :title="pageError" type="error" show-icon :closable="false" class="request-error" />

    <section class="records-card">
      <div class="records-head">
        <h2>{{ problem?.title || '评测记录' }}</h2>
        <div class="records-meta">
          <span v-if="problem?.judgeMode" class="mode-chip">{{ problem.judgeMode }} 评测</span>
          <span class="record-count">共 {{ submissions.length }} 条</span>
        </div>
      </div>
      <SubmissionTable v-if="hasLoadedSubmissions" :submissions="submissions" :judge-mode="problem?.judgeMode || ''" />
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
}
</style>
