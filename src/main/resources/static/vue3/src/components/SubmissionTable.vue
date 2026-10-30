<script setup>
import { formatTime, verdictText, verdictTone } from '@/constants/problem'

defineProps({
  submissions: { type: Array, default: () => [] },
  judgeMode: { type: String, default: '' },
})
</script>

<template>
  <el-table v-if="submissions.length" :data="submissions" size="small" class="submission-table">
    <el-table-column label="结果" width="130">
      <template #default="scope">
        <span class="verdict" :class="'tone-' + verdictTone(scope.row.verdict)">{{ verdictText(scope.row.verdict) }}</span>
      </template>
    </el-table-column>
    <el-table-column v-if="judgeMode === 'OI'" label="得分" width="90">
      <template #default="scope">{{ scope.row.score == null ? '-' : scope.row.score }}</template>
    </el-table-column>
    <el-table-column label="耗时" width="130">
      <template #default="scope">
        <span class="num">{{ scope.row.timeCost == null ? '-' : scope.row.timeCost + ' ms' }}</span>
      </template>
    </el-table-column>
    <el-table-column label="信息" min-width="220" show-overflow-tooltip>
      <template #default="scope">
        <span class="msg">{{ scope.row.message || '' }}</span>
      </template>
    </el-table-column>
    <el-table-column label="提交时间" width="150" align="right">
      <template #default="scope">
        <span class="num">{{ formatTime(scope.row.createTime) }}</span>
      </template>
    </el-table-column>
  </el-table>
  <div v-else class="empty-state">
    <span>还没有提交记录</span>
    <p>写好第一份解法并提交后，这里会按时间倒序列出每次评测结果。</p>
  </div>
</template>

<style scoped>
.submission-table {
  width: 100%;
  --el-table-border-color: var(--line-soft);
  --el-table-header-bg-color: var(--surface-2);
  --el-table-row-hover-bg-color: var(--surface-2);
}

:deep(th.el-table__cell) {
  padding: 9px 0;
  background: var(--surface-2);
  color: var(--ink-3);
  font-size: 12px;
  font-weight: 600;
}

:deep(td.el-table__cell) {
  padding: 10px 0;
  border-bottom: 1px solid var(--line-soft);
  color: var(--ink-2);
  font-size: 13px;
}

:deep(.el-table__inner-wrapper::before) {
  display: none;
}

:deep(.el-table__row:last-child td.el-table__cell) {
  border-bottom: 0;
}

.verdict {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: var(--r-sm);
  font-size: 12.5px;
  font-weight: 600;
}

.tone-ac {
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.tone-bad {
  background: var(--danger-soft);
  color: var(--danger);
}

.tone-warn {
  background: var(--warn-soft);
  color: var(--warn);
}

.tone-neutral {
  background: var(--surface-2);
  color: var(--ink-3);
}

.num {
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
}

.msg {
  color: var(--ink-3);
  font-size: 12.5px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 46px 16px 40px;
  color: var(--ink-3);
  font-size: 13px;
}

.empty-state p {
  color: var(--ink-4);
  font-size: 12px;
}
</style>
