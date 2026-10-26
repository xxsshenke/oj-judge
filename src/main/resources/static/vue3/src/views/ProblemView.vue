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
const pageError = ref('')
const submitting = ref(false)

async function loadProblems() {
  pageError.value = ''
  try {
    problems.value = await listProblems() || []
    const requestedId = Number(route.query.problemId)
    const initialProblem = problems.value.find((problem) => problem.id === requestedId) || problems.value[0]
    if (initialProblem) await selectProblem(initialProblem)
  } catch (error) {
    problems.value = []
    selectedProblem.value = null
    submissions.value = []
    code.value = ''
    pageError.value = error instanceof TypeError ? '无法连接服务器，请检查后端服务。' : error.message || '请求失败，请稍后重试。'
  }
}

async function selectProblem(problem) {
  selectedProblem.value = problem
  code.value = starterCode(problem)
  pageError.value = ''
  try {
    submissions.value = await listSubmissions(problem.id)
  } catch (error) {
    pageError.value = error instanceof TypeError ? '无法连接服务器，请检查后端服务。' : error.message || '请求失败，请稍后重试。'
  }
}

async function submitCode() {
  if (!code.value.trim()) {
    ElMessage.warning('请先写入代码')
    return
  }

  submitting.value = true
  try {
    await submitSolution(selectedProblem.value.id, code.value)
    pageError.value = ''
    ElMessage.success('提交成功')
    router.push({ name: 'submissions', query: { problemId: selectedProblem.value.id } })
  } catch (error) {
    pageError.value = error instanceof TypeError ? '无法连接服务器，请检查后端服务。' : error.message || '提交失败，请稍后重试。'
    ElMessage.error(pageError.value)
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
      <el-alert v-if="pageError" :title="pageError" type="error" show-icon :closable="false" class="request-error" />
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
          <p class="io-note">提交后由隔离容器编译运行，与标准答案比对。</p>
        </article>
        <CodeEditor v-model="code" :submitting="submitting" @submit="submitCode" @view-submissions="openSubmissions" />
      </div>
    </section>
  </main>
</template>

<style scoped>
.request-error {
  margin-bottom: 18px;
}

.io-note {
  margin: 0;
  padding: 12px 14px;
  border-left: 3px solid #c8ddcf;
  background: #f0f5f1;
  color: #75817d;
  font-size: 12px;
  line-height: 1.7;
}
</style>
