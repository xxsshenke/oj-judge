<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { isLoggedIn } from '@/services/auth'

/**
 * 点赞按钮（题解 / 评论通用）。
 *
 * 交互要点：**乐观更新**
 *   点击瞬间就变色、数字变化，不等后端返回。
 *   不加的话，网络稍慢用户就会连点好几次（以为没生效），然后被限流挡掉。
 *
 * 两个字段的权威来源不同，必须分开处理：
 *   liked     —— 以点击瞬间为准（用户看得见的就是结果）
 *   likeCount —— 以后端返回为准（前端自己 +/-1 在并发下会算错）
 */
const props = defineProps({
  liked: { type: Boolean, default: false },
  count: { type: Number, default: 0 },
  /** 实际发请求的函数，签名为 (下一个 liked 值) => Promise<{liked, likeCount}> */
  handler: { type: Function, required: true },
  /** 未登录时点了要弹登录框 */
  onRequireLogin: { type: Function, default: null },
})

const emit = defineEmits(['change'])

const busy = ref(false)
// 本地乐观状态：props 是父组件的数据，不能直接改，所以先同步到本地再覆盖
const localLiked = ref(props.liked)
const localCount = ref(props.count)

// props 变化（父组件刷新数据）时同步回来。
// 用 watch 而不是 computed：乐观更新期间需要"本地值暂时盖住 props"。
watch(
  () => [props.liked, props.count],
  ([nextLiked, nextCount]) => {
    localLiked.value = nextLiked
    localCount.value = nextCount
  },
)

const tone = computed(() => (localLiked.value ? 'liked' : 'plain'))

async function toggle() {
  if (busy.value) return

  if (!isLoggedIn()) {
    if (props.onRequireLogin) props.onRequireLogin()
    else ElMessage.info('请先登录')
    return
  }

  const next = !localLiked.value
  const snapshot = { liked: localLiked.value, count: localCount.value }

  // ① 乐观更新
  localLiked.value = next
  localCount.value = Math.max(snapshot.count + (next ? 1 : -1), 0)
  busy.value = true

  try {
    const result = await props.handler(next)
    // ② 用后端返回值纠正（别人同时点赞时，前端算的数字是错的）
    if (result) {
      localLiked.value = !!result.liked
      localCount.value = Number(result.likeCount) || 0
      emit('change', { liked: localLiked.value, likeCount: localCount.value })
    }
  } catch (error) {
    // ③ 失败回滚
    localLiked.value = snapshot.liked
    localCount.value = snapshot.count
    if (!error.unauthorized) ElMessage.error(error.message || '操作失败')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <button
    class="like-btn"
    :class="tone"
    type="button"
    :disabled="busy"
    :title="localLiked ? '取消点赞' : '点赞'"
    @click.stop="toggle"
  >
    <svg viewBox="0 0 24 24" width="15" height="15" :fill="localLiked ? 'currentColor' : 'none'"
         stroke="currentColor" stroke-width="1.9" stroke-linejoin="round">
      <path d="M7 10.5v9H4.8a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1H7Z" />
      <path d="M7 10.5l3.6-7a2.2 2.2 0 0 1 2.9 2.5l-.8 3.5h5.1a2 2 0 0 1 2 2.4l-1.4 6.6a2 2 0 0 1-2 1.6H7" />
    </svg>
    <span>{{ localCount }}</span>
  </button>
</template>

<style scoped>
.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: transparent;
  color: var(--ink-3);
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.like-btn:hover:not(:disabled) {
  color: var(--accent);
  border-color: var(--accent);
}

.like-btn.liked {
  color: #d4380d;
  border-color: #ffbb96;
  background: #fff2e8;
}

.like-btn:disabled {
  cursor: default;
  opacity: 0.65;
}
</style>
