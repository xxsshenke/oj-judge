<script setup>
import { computed, nextTick, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import LikeButton from '@/components/LikeButton.vue'
import { createComment, deleteComment, likeComment } from '@/services/ojApi'
import { auth } from '@/services/auth'

/**
 * 单条评论 / 回复。
 *
 * 两层结构：顶层评论（depth=0）渲染 replies，回复（depth=1）不再递归。
 * 回复回复时也作为 depth=1 渲染，只是文案上显示「回复 @某人」。
 */
const props = defineProps({
  comment: { type: Object, required: true },
  depth: { type: Number, default: 0 },
  onRequireLogin: { type: Function, default: null },
})

const emit = defineEmits(['created', 'removed'])

const replyOpen = ref(false)
const replyText = ref('')
const replyBusy = ref(false)
const replyInput = ref(null)

const isRoot = computed(() => props.depth === 0)
const deleted = computed(() => !!props.comment.deleted)
const replies = computed(() => props.comment.replies || [])
const replyCount = computed(() => props.comment.replyCount || 0)
const truncated = computed(() => !!props.comment.repliesTruncated)

function requireLogin() {
  if (props.onRequireLogin) props.onRequireLogin()
  else ElMessage.info('请先登录')
  return false
}

function canOperate() {
  return !!auth.token
}

async function openReply() {
  if (!canOperate()) return requireLogin()
  replyOpen.value = !replyOpen.value
  if (replyOpen.value) {
    await nextTick()
    replyInput.value?.focus()
  }
}

async function submitReply() {
  const content = replyText.value.trim()
  if (!content) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  replyBusy.value = true
  try {
    const created = await createComment(props.comment.solutionId, content, props.comment.id)
    replyText.value = ''
    replyOpen.value = false
    ElMessage.success('回复成功')
    // 交给父组件决定怎么合并（是插到 replies 里还是整页刷新）
    emit('created', created)
  } catch (error) {
    if (!error.unauthorized) ElMessage.error(error.message || '回复失败')
  } finally {
    replyBusy.value = false
  }
}

async function remove() {
  try {
    await ElMessageBox.confirm('确定删除这条评论吗？', '删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteComment(props.comment.id)
    ElMessage.success('已删除')
    emit('removed', props.comment.id)
  } catch (error) {
    if (!error.unauthorized) ElMessage.error(error.message || '删除失败')
  }
}

function handleLike(next) {
  if (!canOperate()) {
    requireLogin()
    // 抛错让 LikeButton 回滚乐观状态
    return Promise.reject(Object.assign(new Error('请先登录'), { unauthorized: true }))
  }
  return likeComment(props.comment.id, next)
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<template>
  <div class="comment" :class="{ 'is-reply': !isRoot, 'is-deleted': deleted }">
    <div class="avatar" :title="comment.nickname">{{ (comment.nickname || '?').slice(0, 1) }}</div>

    <div class="body">
      <div class="head">
        <span class="name">{{ comment.nickname }}</span>
        <template v-if="comment.replyToNickname">
          <span class="reply-arrow">›</span>
          <span class="reply-to">@{{ comment.replyToNickname }}</span>
        </template>
        <span class="time">{{ formatTime(comment.createTime) }}</span>
      </div>

      <!-- 已删评论只显示占位，不渲染内容（内容后端也已置空，双重保险） -->
      <p v-if="deleted" class="text text-deleted">该评论已删除</p>
      <p v-else class="text">{{ comment.content }}</p>

      <div class="actions">
        <LikeButton
          :liked="!!comment.liked"
          :count="comment.likeCount || 0"
          :handler="handleLike"
          :on-require-login="onRequireLogin"
        />
        <button v-if="!deleted" class="link-btn" type="button" @click="openReply">
          {{ replyOpen ? '取消' : '回复' }}
        </button>
        <button v-if="comment.mine && !deleted" class="link-btn danger" type="button" @click="remove">
          删除
        </button>
      </div>

      <div v-if="replyOpen" class="reply-box">
        <!-- el-input 的 ref 指向组件实例，focus 要用 .focus()（Element Plus 有暴露） -->
        <el-input
          ref="replyInput"
          v-model="replyText"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
          :placeholder="`回复 @${comment.nickname}`"
        />
        <div class="reply-actions">
          <el-button size="small" :loading="replyBusy" type="primary" @click="submitReply">发送</el-button>
        </div>
      </div>

      <!-- 回复列表：只在顶层渲染，且不再往里递归 -->
      <div v-if="isRoot && replies.length" class="replies">
        <CommentItem
          v-for="reply in replies"
          :key="reply.id"
          :comment="reply"
          :depth="1"
          :on-require-login="onRequireLogin"
          @created="emit('created', $event)"
          @removed="emit('removed', $event)"
        />
        <div v-if="truncated" class="truncated-tip">
          共 {{ replyCount }} 条回复，仅显示前 {{ replies.length }} 条
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.comment {
  display: flex;
  gap: 10px;
  padding: 14px 0;
  border-top: 1px solid var(--line-soft);
}

.comment:first-child {
  border-top: 0;
}

.comment.is-reply {
  padding: 10px 0 10px 6px;
  border-top: 0;
}

.avatar {
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--accent-soft, #e8f1ff);
  color: var(--accent);
  font-size: 13px;
  font-weight: 600;
  line-height: 30px;
  text-align: center;
  text-transform: uppercase;
}

.is-reply .avatar {
  width: 24px;
  height: 24px;
  font-size: 11px;
  line-height: 24px;
}

.body {
  flex: 1 1 auto;
  min-width: 0;
}

.head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  font-size: 12.5px;
}

.name {
  font-weight: 600;
  color: var(--ink);
}

.reply-arrow {
  color: var(--ink-5);
}

.reply-to {
  color: var(--accent);
}

.time {
  margin-left: auto;
  color: var(--ink-5);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
}

.text {
  margin: 0;
  color: var(--ink-2);
  font-size: 13.5px;
  line-height: 1.65;
  /* 保留用户输入的换行 —— 一期不做 Markdown，靠这个显示段落 */
  white-space: pre-wrap;
  word-break: break-word;
}

.text-deleted {
  color: var(--ink-5);
  font-style: italic;
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.link-btn {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink-4);
  font-size: 12.5px;
  cursor: pointer;
}

.link-btn:hover {
  color: var(--accent);
}

.link-btn.danger:hover {
  color: #d4380d;
}

.reply-box {
  margin-top: 10px;
}

.reply-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.replies {
  margin-top: 6px;
  padding-left: 12px;
  border-left: 2px solid var(--line-soft);
}

.truncated-tip {
  padding: 8px 0 2px 6px;
  color: var(--ink-5);
  font-size: 12px;
}
</style>
