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
      <div>
        <button class="back-button" type="button" @click="backToProblem">&#x2190; &#x8FD4;&#x56DE;&#x9898;&#x76EE;</button>
        <div class="crumb">&#x63D0;&#x4EA4;&#x8BB0;&#x5F55; / <span>{{ problem?.title || '&#x52A0;&#x8F7D;&#x4E2D;' }}</span></div>
        <h2>&#x63D0;&#x4EA4;&#x8BB0;&#x5F55;</h2>
        <p>&#x5F53;&#x524D;&#x9898;&#x76EE;&#x7684;&#x6240;&#x6709;&#x8BC4;&#x6D4B;&#x7ED3;&#x679C;</p>
      </div>
      <div class="page-actions"><el-button @click="loadData">&#x5237;&#x65B0;</el-button><el-button type="success" @click="backToProblem">&#x518D;&#x6B21;&#x63D0;&#x4EA4;</el-button></div>
    </div>

    <el-alert v-if="pageError" :title="pageError" type="error" show-icon :closable="false" class="request-error" />
    <section class="submissions-card submissions-page-card">
      <div class="submissions-head"><div><div class="section-title compact"><span class="section-index">01</span><h3>{{ problem?.title || 'Submission history' }}</h3></div><p>GET /submissions?problemId={{ problem?.id || '-' }}</p></div><span class="record-count">{{ submissions.length }} records</span></div>
      <SubmissionTable v-if="hasLoadedSubmissions" :submissions="submissions" :judge-mode="problem?.judgeMode || ''" />
    </section>
  </main>
</template>

<style scoped>
.request-error {
  margin-top: 18px;
}
</style>
