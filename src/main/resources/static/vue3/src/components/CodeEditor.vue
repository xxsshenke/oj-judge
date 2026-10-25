<script setup>
import { computed } from 'vue'

const code = defineModel({ default: '' })
defineProps({ submitting: { type: Boolean, default: false } })
const emit = defineEmits(['submit', 'view-submissions'])

const lineNumbers = computed(() => {
  const lineCount = Math.max(1, code.value.split(/\r?\n/).length)
  return Array.from({ length: lineCount }, (_, index) => index + 1).join(String.fromCharCode(10))
})
</script>

<template>
  <article class="editor-card">
    <div class="editor-topline"><div class="section-title compact"><span class="section-index">02</span><h3>&#x63D0;&#x4EA4;&#x4EE3;&#x7801;</h3></div><span class="language-pill">Java 21</span></div>
    <div class="editor-wrap"><div class="line-numbers">{{ lineNumbers }}</div><textarea v-model="code" spellcheck="false" aria-label="Java code editor"></textarea></div>
    <div class="editor-actions"><span class="hint">&#x4EE3;&#x7801;&#x4F1A;&#x5728;&#x9694;&#x79BB;&#x5BB9;&#x5668;&#x4E2D;&#x7F16;&#x8BD1;&#x548C;&#x8FD0;&#x884C;</span><div class="editor-buttons"><el-button plain @click="emit('view-submissions')">&#x67E5;&#x770B;&#x63D0;&#x4EA4;&#x8BB0;&#x5F55;</el-button><el-button type="success" :loading="submitting" @click="emit('submit')">&#x63D0;&#x4EA4;&#x8BC4;&#x6D4B; &#x2197;</el-button></div></div>
  </article>
</template>
