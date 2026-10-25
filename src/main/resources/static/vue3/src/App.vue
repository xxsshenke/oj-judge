<script setup>
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

const route = useRoute()
const isSubmissions = computed(() => route.name === 'submissions')
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/problems"><div class="brand-mark">OJ</div><div><strong>Judge desk</strong><span>Java online judge</span></div></RouterLink>
      <nav class="topbar-actions"><span class="topbar-meta"><span class="live-dot"></span><span>Java 21 · Online judge</span></span><RouterLink class="topbar-button" :to="isSubmissions ? '/problems' : '/submissions'">{{ isSubmissions ? '返回题目' : '提交记录' }}</RouterLink></nav>
    </header>
    <RouterView />
  </div>
</template>
