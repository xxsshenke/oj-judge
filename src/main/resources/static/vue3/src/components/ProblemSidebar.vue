<script setup>
import { computed } from 'vue'
import { difficultyText } from '@/constants/problem'

const props = defineProps({
  problems: { type: Array, default: () => [] },
  selectedId: { type: Number, default: null },
})

const search = defineModel('search', { default: '' })
const emit = defineEmits(['select', 'refresh'])
const filteredProblems = computed(() => props.problems.filter((problem) => problem.title.toLowerCase().includes(search.value.toLowerCase())))
</script>

<template>
  <aside class="problem-panel">
    <div class="panel-heading">
      <div><span class="eyebrow">Problem set</span><h1>&#x9898;&#x76EE;&#x96C6;</h1></div>
      <el-button text class="icon-button" title="&#x5237;&#x65B0;&#x9898;&#x76EE;" @click="emit('refresh')">&#x21BB;</el-button>
    </div>
    <label class="search-box"><span>&#x2315;</span><input v-model="search" type="search" placeholder="&#x641C;&#x7D22;&#x9898;&#x76EE;"></label>
    <div class="problem-list">
      <button v-for="(problem, index) in filteredProblems" :key="problem.id" class="problem-item" :class="{ active: selectedId === problem.id }" type="button" @click="emit('select', problem)">
        <span class="problem-number">{{ String(index + 1).padStart(2, '0') }}</span>
        <span class="problem-name">{{ problem.title }}</span>
        <span class="problem-item-meta"><span><i class="mini-difficulty"></i>{{ difficultyText(problem.difficulty) }}</span><span>{{ problem.judgeMode || 'ACM' }}</span></span>
      </button>
    </div>
    <div class="panel-foot"><span>{{ problems.length }} &#x9053;&#x9898;&#x76EE;</span><span class="shortcut">Ctrl K</span></div>
  </aside>
</template>
