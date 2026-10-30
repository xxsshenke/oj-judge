<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CodeEditor from '@/components/CodeEditor.vue'
import { starterCode } from '@/constants/problem'
import { listProblems, listSubmissions, submitSolution } from '@/services/ojApi'

const route = useRoute()
const router = useRouter()

const problems = ref([])
const selectedProblem = ref(null)
const code = ref('')
const submissions = ref([])
const pageError = ref('')
const submitting = ref(false)
const judging = ref(false)
const listOpen = ref(false)
const search = ref('')
const descOpen = ref(true)
const editorRef = ref(null)

let pollTimer = null
let pollStartedAt = 0
let draftTimer = null

const filteredProblems = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return problems.value
  return problems.value.filter((problem) => problem.title.toLowerCase().includes(keyword))
})

const description = computed(() => (selectedProblem.value?.description || '').trim())

/**
 * 题面分段：description 是唯一数据源，里面用“输入格式：/输出格式：/数据范围：/样例：/说明：”
 * 这些标题把内容分成若干段。页面把输入/输出段落抽到独立卡片，正文只保留题意和说明，
 * 避免同一段文字既出现在正文、又出现在卡片里。
 */
const SECTION_RULES = [
  { key: 'input', pattern: /^输入格式\s*[:：]\s*$/ },
  { key: 'output', pattern: /^输出格式\s*[:：]\s*$/ },
  { key: 'range', pattern: /^数据范围\s*[:：]\s*$/ },
  { key: 'sample', pattern: /^(样例|示例)\s*[:：]\s*$/ },
  { key: 'note', pattern: /^(说明|提示)\s*[:：]\s*$/ },
]

function splitDescription(text) {
  const buckets = { body: [], input: [], output: [], range: [], sample: [], note: [] }
  let current = 'body'
  for (const line of (text || '').split('\n')) {
    const rule = SECTION_RULES.find((item) => item.pattern.test(line.trim()))
    if (rule) {
      current = rule.key
      continue
    }
    buckets[current].push(line)
  }
  const tidy = (lines) => lines.join('\n').replace(/\n{3,}/g, '\n\n').trim()
  return {
    body: tidy(buckets.body),
    input: tidy(buckets.input),
    output: tidy(buckets.output),
    range: tidy(buckets.range),
    note: tidy(buckets.note),
  }
}

const descriptionSections = computed(() => splitDescription(description.value))

// 正文只保留题意与说明；样例交给下方“示例”卡片，避免重复。
const descriptionBody = computed(() => {
  const { body, note } = descriptionSections.value
  return [body, note].filter(Boolean).join('\n\n')
})

// 输入描述 = 输入格式 + 数据范围（数据范围属于输入约束，放在一起更符合阅读顺序）。
const inputDescription = computed(() => {
  const { input, range } = descriptionSections.value
  return [input, range].filter(Boolean).join('\n\n')
})

const outputDescription = computed(() => descriptionSections.value.output)
const sample = computed(() => ({
  input: selectedProblem.value?.input ?? '',
  output: selectedProblem.value?.expected ?? '',
}))
const hasSample = computed(() => Boolean(sample.value.input || sample.value.output))
const sampleInput = computed(() => selectedProblem.value?.input || '')
const latestSubmission = computed(() => submissions.value[0] || null)

const difficultyLabel = (difficulty) => ({ 1: '简单', 2: '中等', 3: '困难' }[difficulty] || '基础')
const difficultyTone = (difficulty) => ({ 1: 'easy', 2: 'medium', 3: 'hard' }[difficulty] || 'basic')

function requestErrorMessage(error) {
  return error instanceof TypeError ? '无法连接服务器，请检查后端服务。' : error.message || '请求失败，请稍后重试。'
}

function sortSubmissions(list) {
  return [...(list || [])].sort((a, b) => (b.id ?? 0) - (a.id ?? 0))
}

function isTerminal(submission) {
  return !submission || submission.status === 'DONE' || submission.status === 'SE'
}

function draftKey(problemId) {
  return `oj:draft:${problemId}`
}

function readDraft(problemId) {
  try {
    return localStorage.getItem(draftKey(problemId))
  } catch {
    return null
  }
}

function clearDraft(problemId) {
  try {
    localStorage.removeItem(draftKey(problemId))
  } catch {
    /* localStorage 不可用时忽略 */
  }
}

async function loadProblems() {
  pageError.value = ''
  try {
    const list = (await listProblems()) || []
    problems.value = list
    const requestedId = Number(route.query.problemId)
    const initial = list.find((problem) => problem.id === requestedId) || list[0]
    if (initial) await selectProblem(initial, false)
  } catch (error) {
    problems.value = []
    selectedProblem.value = null
    submissions.value = []
    pageError.value = requestErrorMessage(error)
  }
}

async function selectProblem(problem, updateQuery = true) {
  if (!problem) return
  stopPolling()
  selectedProblem.value = problem
  code.value = readDraft(problem.id) ?? starterCode(problem)
  descOpen.value = true
  pageError.value = ''
  if (updateQuery) router.replace({ name: 'problem', query: { problemId: problem.id } })
  await refreshSubmissions()
}

async function refreshSubmissions() {
  const problem = selectedProblem.value
  if (!problem) return
  try {
    const list = await listSubmissions(problem.id)
    if (selectedProblem.value?.id !== problem.id) return
    submissions.value = sortSubmissions(list)
    if (latestSubmission.value && !isTerminal(latestSubmission.value) && pollTimer === null) startPolling()
  } catch (error) {
    pageError.value = requestErrorMessage(error)
  }
}

function pickProblem(problem) {
  listOpen.value = false
  if (problem.id === selectedProblem.value?.id) return
  search.value = ''
  selectProblem(problem)
}

function stopPolling() {
  if (pollTimer !== null) window.clearInterval(pollTimer)
  pollTimer = null
  judging.value = false
}

function startPolling() {
  stopPolling()
  const problemId = selectedProblem.value?.id
  if (!problemId) return
  if (latestSubmission.value && isTerminal(latestSubmission.value)) return

  judging.value = true
  pollStartedAt = Date.now()
  pollTimer = window.setInterval(async () => {
    if (selectedProblem.value?.id !== problemId) {
      stopPolling()
      return
    }
    try {
      const list = await listSubmissions(problemId)
      if (selectedProblem.value?.id !== problemId) return
      submissions.value = sortSubmissions(list)
      if (isTerminal(latestSubmission.value) || Date.now() - pollStartedAt > 90000) stopPolling()
    } catch {
      stopPolling()
    }
  }, 1200)
}

async function submitCode() {
  const problem = selectedProblem.value
  if (!problem) return
  if (!code.value.trim()) {
    ElMessage.warning('请先编写代码')
    return
  }

  submitting.value = true
  try {
    await submitSolution(problem.id, code.value)
    ElMessage.success('提交成功，正在评测')
    await refreshSubmissions()
    editorRef.value?.showResult()
    startPolling()
  } catch (error) {
    pageError.value = requestErrorMessage(error)
    ElMessage.error(pageError.value)
  } finally {
    submitting.value = false
  }
}

function resetCode() {
  const problem = selectedProblem.value
  if (!problem) return
  clearDraft(problem.id)
  code.value = starterCode(problem)
}

function openSubmissions() {
  router.push({ name: 'submissions', query: { problemId: selectedProblem.value?.id } })
}

function openProfile() {
  router.push({ name: 'user' })
}

function goProblemSet() {
  router.push({ name: 'problemset' })
}

async function copyText(text, label) {
  if (!text) {
    ElMessage.info(`暂无可复制的${label}`)
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制`)
  } catch {
    const helper = document.createElement('textarea')
    helper.value = text
    helper.style.position = 'fixed'
    helper.style.opacity = '0'
    document.body.appendChild(helper)
    helper.select()
    document.execCommand('copy')
    helper.remove()
    ElMessage.success(`${label}已复制`)
  }
}

watch(code, (value) => {
  const problem = selectedProblem.value
  if (!problem) return
  if (draftTimer !== null) window.clearTimeout(draftTimer)
  draftTimer = window.setTimeout(() => {
    try {
      if (value && value.trim()) localStorage.setItem(draftKey(problem.id), value)
      else localStorage.removeItem(draftKey(problem.id))
    } catch {
      /* localStorage 不可用时忽略 */
    }
  }, 350)
})

onMounted(loadProblems)

onBeforeUnmount(() => {
  stopPolling()
  if (draftTimer !== null) window.clearTimeout(draftTimer)
})
</script>

<template>
  <main class="solve-page">
    <header class="solve-header">
      <div class="header-left">
        <button class="back-button" type="button" title="返回题库" aria-label="返回题库" @click="goProblemSet">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.9">
            <path d="M15 5l-7 7 7 7" />
          </svg>
        </button>
        <span class="header-title">{{ selectedProblem?.title || '加载中…' }}</span>
        <span v-if="selectedProblem" class="difficulty-chip" :class="'tone-' + difficultyTone(selectedProblem.difficulty)">
          {{ difficultyLabel(selectedProblem.difficulty) }}
        </span>
      </div>
      <div class="header-right">
        <button class="header-action" type="button" @click="listOpen = true">
          <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8">
            <rect x="4" y="4" width="7" height="7" rx="1" />
            <rect x="13" y="4" width="7" height="7" rx="1" />
            <rect x="4" y="13" width="7" height="7" rx="1" />
            <rect x="13" y="13" width="7" height="7" rx="1" />
          </svg>
          题目列表
        </button>
        <button class="header-action" type="button" @click="openSubmissions">
          <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M8 6h12" />
            <path d="M8 12h12" />
            <path d="M8 18h12" />
            <circle cx="4" cy="6" r="1" fill="currentColor" />
            <circle cx="4" cy="12" r="1" fill="currentColor" />
            <circle cx="4" cy="18" r="1" fill="currentColor" />
          </svg>
          我的提交
        </button>
        <span class="avatar" title="个人主页" role="button" tabindex="0" aria-label="个人主页" @click="openProfile" @keydown.enter="openProfile">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.9">
            <circle cx="12" cy="8.5" r="3.4" />
            <path d="M5.5 19.5a6.5 6.5 0 0 1 13 0" />
          </svg>
        </span>
      </div>
    </header>

    <transition name="fade">
      <div v-if="pageError" class="error-banner">{{ pageError }}</div>
    </transition>

    <div class="solve-body">
      <section class="statement-pane">
        <div class="limits-strip">
          <div>
            时间限制：<b>Java 2 秒</b><span class="meta-sep"></span>空间限制：<b>256 MB</b>
          </div>
          <div class="limits-muted">
            64bit IO Format: <code>%lld</code><span class="meta-sep"></span>{{ selectedProblem?.judgeMode || 'ACM' }} 评测
          </div>
        </div>

        <div class="statement-scroll">
          <article class="statement-block">
            <div class="block-title">
              <span class="title-mark"></span>
              <h2>题目描述</h2>
              <button class="collapse-button" type="button" :title="descOpen ? '收起描述' : '展开描述'" :aria-label="descOpen ? '收起描述' : '展开描述'" @click="descOpen = !descOpen">
                <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: !descOpen }">
                  <path d="M6 9l6 6 6-6" />
                </svg>
              </button>
            </div>
            <div v-show="descOpen" class="block-content description">{{ descriptionBody || '该题目暂无描述。' }}</div>
          </article>

          <article class="statement-block">
            <div class="block-title">
              <span class="title-mark"></span>
              <h2>输入描述</h2>
            </div>
            <div class="block-content io-box">{{ inputDescription || '从标准输入读取数据。' }}</div>
          </article>

          <article class="statement-block">
            <div class="block-title">
              <span class="title-mark"></span>
              <h2>输出描述</h2>
            </div>
            <div class="block-content io-box">{{ outputDescription || '将计算结果输出到标准输出。' }}</div>
          </article>

          <article class="statement-block">
            <div class="block-title">
              <span class="title-mark"></span>
              <h2>示例</h2>
            </div>
            <div v-if="hasSample" class="sample-card">
              <div class="sample-block">
                <div class="sample-head">
                  <span>输入</span>
                  <button type="button" @click="copyText(sample.input, '输入')">
                    <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8">
                      <rect x="9" y="9" width="11" height="11" rx="2" />
                      <path d="M5 15V5a2 2 0 0 1 2-2h10" />
                    </svg>
                    复制
                  </button>
                </div>
                <pre>{{ sample.input || '（空）' }}</pre>
              </div>
              <div class="sample-block">
                <div class="sample-head">
                  <span>输出</span>
                  <button type="button" @click="copyText(sample.output, '输出')">
                    <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8">
                      <rect x="9" y="9" width="11" height="11" rx="2" />
                      <path d="M5 15V5a2 2 0 0 1 2-2h10" />
                    </svg>
                    复制
                  </button>
                </div>
                <pre>{{ sample.output || '（空）' }}</pre>
              </div>
              <p class="sample-note">示例数据来自题目配置的标准输入 / 标准输出。</p>
            </div>
            <div v-else class="sample-empty">
              <span>该题尚未配置示例</span>
              <p>补充标准输入与标准输出后，示例会自动显示在这里。</p>
            </div>
          </article>

          <div class="statement-foot">
            题目 #{{ selectedProblem?.id || '-' }}<span class="meta-sep"></span>{{ selectedProblem?.judgeMode || 'ACM' }} 评测模式<span class="meta-sep"></span>Java 21
          </div>
        </div>
      </section>

      <CodeEditor
        ref="editorRef"
        v-model="code"
        :submitting="submitting"
        :judging="judging"
        :submission="latestSubmission"
        :sample-input="sampleInput"
        :file-name="selectedProblem?.title || 'Main'"
        @submit="submitCode"
        @reset="resetCode"
        @open-submissions="openSubmissions"
      />
    </div>

    <el-drawer v-model="listOpen" :with-header="false" size="340px" class="problem-drawer">
      <div class="drawer-head">
        <h3>题目列表</h3>
        <span>共 {{ problems.length }} 道</span>
      </div>
      <label class="drawer-search">
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="6.5" />
          <path d="M16 16l4 4" />
        </svg>
        <input v-model="search" type="search" placeholder="搜索题目" />
      </label>
      <div class="drawer-list">
        <button
          v-for="problem in filteredProblems"
          :key="problem.id"
          class="drawer-item"
          :class="{ active: problem.id === selectedProblem?.id }"
          type="button"
          @click="pickProblem(problem)"
        >
          <span class="drawer-index">{{ String(problem.id).padStart(2, '0') }}</span>
          <span class="drawer-name">{{ problem.title }}</span>
          <span class="drawer-difficulty" :class="'tone-' + difficultyTone(problem.difficulty)">{{ difficultyLabel(problem.difficulty) }}</span>
        </button>
        <div v-if="!filteredProblems.length" class="drawer-empty">没有匹配的题目</div>
      </div>
    </el-drawer>
  </main>
</template>

<style scoped>
.solve-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--surface);
  color: var(--ink);
}

.solve-header {
  position: relative;
  z-index: 10;
  flex: 0 0 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 8px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.back-button {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-3);
  transition: background 0.15s ease, color 0.15s ease;
}

.back-button:hover {
  background: var(--surface-2);
  color: var(--ink);
}

.header-title {
  overflow: hidden;
  max-width: 44vw;
  color: var(--ink);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.1px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.difficulty-chip {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: var(--r-pill);
  font-size: 11.5px;
  line-height: 1;
}

.tone-easy {
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.tone-medium {
  background: var(--warn-soft);
  color: var(--warn);
}

.tone-hard {
  background: var(--danger-soft);
  color: var(--danger);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 2px;
}

.header-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 10px;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-3);
  font-size: 12.5px;
  transition: background 0.15s ease, color 0.15s ease;
}

.header-action:hover {
  background: var(--surface-2);
  color: var(--ink);
}

.avatar {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  margin-left: 8px;
  border-radius: 50%;
  background: linear-gradient(150deg, #414c58, #222a32);
  color: #ffffff;
  transition: box-shadow 0.15s ease;
}

.avatar:hover {
  box-shadow: 0 0 0 3px rgba(65, 76, 88, 0.12);
}

.error-banner {
  position: fixed;
  top: 62px;
  left: 50%;
  z-index: 100;
  max-width: min(560px, 90vw);
  padding: 9px 16px;
  transform: translateX(-50%);
  border: 1px solid var(--danger-line);
  border-radius: var(--r-md);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 12.5px;
  box-shadow: var(--shadow-float);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.solve-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.statement-pane {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--line);
  background: var(--surface);
}

.limits-strip {
  flex: 0 0 auto;
  padding: 9px 26px 10px;
  background: var(--surface-2);
  border-bottom: 1px solid var(--line-soft);
  color: var(--ink-3);
  font-size: 12.5px;
  line-height: 1.9;
}

.limits-strip b {
  color: var(--ink);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.limits-muted {
  color: var(--ink-4);
}

.limits-muted code {
  color: var(--ink-3);
  font-size: 12px;
}

.meta-sep {
  display: inline-block;
  width: 1px;
  height: 10px;
  margin: 0 9px;
  background: var(--line);
  vertical-align: middle;
}

.statement-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 26px 56px;
}

.statement-block {
  padding-top: 24px;
}

.statement-block:first-of-type {
  padding-top: 20px;
}

.block-title {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 11px;
}

.title-mark {
  width: 3px;
  height: 13px;
  border-radius: 2px;
  background: var(--accent);
}

.block-title h2 {
  margin: 0;
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.1px;
}

.collapse-button {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  margin-left: auto;
  padding: 0;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-4);
}

.collapse-button:hover {
  background: var(--surface-2);
  color: var(--ink-3);
}

.collapse-button svg {
  transition: transform 0.18s ease;
}

.collapse-button svg.rotated {
  transform: rotate(-90deg);
}

.block-content {
  color: var(--ink-2);
  font-size: 13.5px;
  line-height: 1.85;
}

.description {
  min-height: 22px;
  white-space: pre-wrap;
  word-break: break-word;
}

.io-box {
  padding: 11px 14px;
  border-left: 2px solid var(--accent-line);
  border-radius: 0 var(--r-sm) var(--r-sm) 0;
  background: var(--surface-2);
  color: var(--ink-3);
  white-space: pre-wrap;
  word-break: break-word;
}

.sample-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sample-block {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
}

.sample-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px 6px 14px;
  background: var(--surface-2);
  border-bottom: 1px solid var(--line-soft);
  color: var(--ink-4);
  font-size: 11.5px;
}

.sample-head button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 4px;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-4);
  font-size: 11.5px;
}

.sample-head button:hover {
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.sample-block pre {
  min-height: 40px;
  padding: 12px 14px;
  color: var(--ink);
  font: 12.5px/1.75 var(--font-mono);
  white-space: pre-wrap;
  word-break: break-all;
}

.sample-note {
  color: var(--ink-4);
  font-size: 11.5px;
}

.sample-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 26px 16px;
  border: 1px dashed var(--line);
  border-radius: var(--r-md);
  background: var(--surface-2);
  color: var(--ink-3);
  font-size: 12.5px;
}

.sample-empty p {
  color: var(--ink-4);
  font-size: 11.5px;
}

.statement-foot {
  margin-top: 30px;
  padding-top: 14px;
  border-top: 1px solid var(--line-soft);
  color: var(--ink-4);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
}

.drawer-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.drawer-head h3 {
  color: var(--ink);
  font-size: 15px;
  font-weight: 600;
}

.drawer-head span {
  color: var(--ink-4);
  font-size: 12px;
}

.drawer-search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  margin-bottom: 14px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  color: var(--ink-5);
}

.drawer-search:focus-within {
  border-color: var(--accent-line);
}

.drawer-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--ink);
  font-size: 12.5px;
}

.drawer-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.drawer-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid transparent;
  border-radius: var(--r-md);
  background: transparent;
  text-align: left;
  transition: background 0.15s ease;
}

.drawer-item:hover {
  background: var(--surface-2);
}

.drawer-item.active {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.drawer-index {
  width: 22px;
  color: var(--ink-5);
  font: 11.5px var(--font-mono);
  font-variant-numeric: tabular-nums;
}

.drawer-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--ink-2);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drawer-item.active .drawer-name {
  color: var(--ink);
  font-weight: 600;
}

.drawer-difficulty {
  padding: 1px 8px;
  border-radius: var(--r-pill);
  font-size: 11px;
}

.drawer-empty {
  padding: 26px 0;
  color: var(--ink-4);
  font-size: 12.5px;
  text-align: center;
}

@media (max-width: 960px) {
  .solve-page {
    height: auto;
    min-height: 100vh;
    overflow: visible;
  }

  .solve-body {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(360px, 52vh) minmax(420px, 60vh);
  }

  .statement-pane {
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .header-action {
    display: none;
  }
}
</style>
