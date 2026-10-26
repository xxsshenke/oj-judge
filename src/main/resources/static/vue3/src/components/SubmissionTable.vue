<script setup>
import { formatTime } from '@/constants/problem'

defineProps({
  submissions: { type: Array, default: () => [] },
  judgeMode: { type: String, default: '' },
})
const statusType = (status) => status === 'DONE' ? 'success' : 'warning'
</script>

<template>
  <el-table v-if="submissions.length" :data="submissions" size="small" class="submission-table">
    <el-table-column label="Status" width="120"><template #default="scope"><el-tag size="small" :type="statusType(scope.row.status)">{{ scope.row.status || '-' }}</el-tag></template></el-table-column>
    <el-table-column label="Verdict"><template #default="scope"><span :class="'verdict-' + String(scope.row.verdict || '').toLowerCase()">{{ scope.row.verdict || 'Waiting' }}</span></template></el-table-column>
    <el-table-column v-if="judgeMode === 'OI'" label="Score" width="100"><template #default="scope">{{ scope.row.score == null ? '-' : scope.row.score }}</template></el-table-column>
    <el-table-column label="Time" width="110"><template #default="scope">{{ scope.row.timeCost == null ? '-' : scope.row.timeCost + ' ms' }}</template></el-table-column>
    <el-table-column label="Created"><template #default="scope">{{ formatTime(scope.row.createTime) }}</template></el-table-column>
  </el-table>
  <div v-else class="empty-state">&#x8FD8;&#x6CA1;&#x6709;&#x63D0;&#x4EA4;&#xFF0C;&#x5199;&#x4E0B;&#x4F60;&#x7684;&#x7B2C;&#x4E00;&#x4E2A;&#x89E3;&#x6CD5;&#x5427;&#x3002;</div>
</template>
