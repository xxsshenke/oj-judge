<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SubmissionTable from '@/components/SubmissionTable.vue'
import { listProblems, listSubmissions } from '@/services/ojApi'

const route = useRoute()
const router = useRouter()
const problem = ref(null)
const submissions = ref([])

async function loadData() {
  const problemId = Number(route.query.problemId)
  const result = await listProblems()
  problem.value = result.data.find((item) => item.id === problemId) || result.data[0] || null

  if (problem.value) {
    submissions.value = await listSubmissions(problem.value.id)
  }
}

function backToProblem() {
  router.push({ name: 'problem', query: { problemId: problem.value?.id } })
}

onMounted(loadData)
watch(() => route.query.problemId, loadData)
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

    <section class="submissions-card submissions-page-card">
      <div class="submissions-head"><div><div class="section-title compact"><span class="section-index">01</span><h3>{{ problem?.title || 'Submission history' }}</h3></div><p>GET /submissions?problemId={{ problem?.id || '-' }}</p></div><span class="record-count">{{ submissions.length }} records</span></div>
      <SubmissionTable :submissions="submissions" />
    </section>
  </main>
</template>
