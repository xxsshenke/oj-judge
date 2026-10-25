<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ProblemSidebar from '@/components/ProblemSidebar.vue'
import CodeEditor from '@/components/CodeEditor.vue'
import { difficultyText, starterCode } from '@/constants/problem'
import { listProblems, listSubmissions, submitSolution } from '@/services/ojApi'

const route = useRoute()
const router = useRouter()
const problems = ref([])
const selectedProblem = ref(null)
const search = ref('')
const code = ref('')
const submissions = ref([])
const connected = ref(false)
const submitting = ref(false)

async function loadProblems() {
  const result = await listProblems()
  problems.value = result.data
  connected.value = result.connected

  const requestedId = Number(route.query.problemId)
  const initialProblem = problems.value.find((problem) => problem.id === requestedId) || problems.value[0]
  if (initialProblem) await selectProblem(initialProblem)
}

async function selectProblem(problem) {
  selectedProblem.value = problem
  code.value = starterCode(problem)
  submissions.value = await listSubmissions(problem.id)
}

async function submitCode() {
  if (!code.value.trim()) {
    ElMessage.warning('\\u8BF7\\u5148\\u5199\\u5165\\u4EE3\\u7801')
    return
  }

  submitting.value = true
  try {
    const result = await submitSolution(selectedProblem.value.id, code.value)
    ElMessage.success(result.connected ? '\\u63D0\\u4EA4\\u6210\\u529F' : '\\u5DF2\\u52A0\\u5165\\u6F14\\u793A\\u8BC4\\u6D4B\\u961F\\u5217')
    router.push({ name: 'submissions', query: { problemId: selectedProblem.value.id } })
  } finally {
    submitting.value = false
  }
}

function openSubmissions() {
  router.push({ name: 'submissions', query: { problemId: selectedProblem.value?.id } })
}

onMounted(loadProblems)
</script>

<template>
  <main class="workspace">
    <ProblemSidebar v-model:search="search" :problems="problems" :selected-id="selectedProblem?.id" @select="selectProblem" @refresh="loadProblems" />

    <section class="content-panel">
      <div class="content-head">
        <div>
          <div class="crumb">&#x9898;&#x76EE; / <span>{{ selectedProblem?.title || '&#x52A0;&#x8F7D;&#x4E2D;' }}</span></div>
          <h2>{{ selectedProblem?.title || '&#x6B63;&#x5728;&#x8BFB;&#x53D6;&#x9898;&#x76EE;' }}</h2>
          <p>&#x9898;&#x76EE; #{{ selectedProblem?.id || '-' }} · {{ selectedProblem?.judgeMode || 'ACM' }} &#x8BC4;&#x6D4B;</p>
        </div>
        <div class="problem-tags"><el-tag type="warning" effect="light">{{ difficultyText(selectedProblem?.difficulty) }}</el-tag><el-tag effect="plain">{{ selectedProblem?.judgeMode || '-' }}</el-tag></div>
      </div>

      <div class="content-grid">
        <article class="statement-card">
          <div class="section-title"><span class="section-index">01</span><h3>&#x9898;&#x76EE;&#x63CF;&#x8FF0;</h3></div>
          <p class="description">{{ selectedProblem?.description || '&#x52A0;&#x8F7D;&#x4E2D;...' }}</p>
          <div class="io-grid"><div><div class="io-label">&#x8F93;&#x5165;</div><pre>{{ selectedProblem?.input || '-' }}</pre></div><div><div class="io-label">&#x671F;&#x671B;&#x8F93;&#x51FA;</div><pre>{{ selectedProblem?.expected || '&#x7531;&#x8BC4;&#x6D4B;&#x673A;&#x5224;&#x65AD;' }}</pre></div></div>
        </article>
        <CodeEditor v-model="code" :submitting="submitting" @submit="submitCode" @view-submissions="openSubmissions" />
      </div>
    </section>
  </main>
</template>
