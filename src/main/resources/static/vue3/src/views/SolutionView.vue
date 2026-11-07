<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import CommentItem from '@/components/CommentItem.vue'
import LikeButton from '@/components/LikeButton.vue'
import LoginDialog from '@/components/LoginDialog.vue'
import {
  createComment,
  createSolution,
  deleteSolution,
  getProblem,
  getSolution,
  likeSolution,
  listCommentPage,
  listSolutionPage,
  updateSolution,
} from '@/services/ojApi'
import { auth } from '@/services/auth'

const route = useRoute()
const router = useRouter()

const problemId = computed(() => Number(route.query.problemId) || null)
const solutionId = computed(() => Number(route.query.id) || null)

const problem = ref(null)
const solutions = ref([])
const current = ref(null)          // 当前打开的题解详情
const comments = ref([])

const loading = ref(false)
const pageError = ref('')
const sort = ref('new')

// 题解列表分页
const solPageNum = ref(1)
const solPageSize = ref(10)
const solTotal = ref(0)
const solPages = computed(() => (solPageSize.value > 0 ? Math.ceil(solTotal.value / solPageSize.value) : 0))

// 评论分页
const cmtPageNum = ref(1)
const cmtPageSize = ref(10)
const cmtTotal = ref(0)
const cmtPages = computed(() => (cmtPageSize.value > 0 ? Math.ceil(cmtTotal.value / cmtPageSize.value) : 0))

// 编辑器
const editorOpen = ref(false)
const editing = ref(false)
const formTitle = ref('')
const formContent = ref('')
const saving = ref(false)

// 新评论
const newComment = ref('')
const posting = ref(false)

const loginVisible = ref(false)
let unauthorizedHandlerBound = false

function openLogin() {
  loginVisible.value = true
}

function errText(error) {
  return error instanceof TypeError ? '无法连接服务器，请检查后端服务。' : error.message || '请求失败'
}

/* ---------------- 加载 ---------------- */

async function loadProblem() {
  if (!problemId.value) return
  try {
    problem.value = await getProblem(problemId.value)
  } catch {
    // 题目信息只用于标题展示，失败不阻塞页面
  }
}

async function loadSolutions() {
  if (!problemId.value) return
  loading.value = true
  try {
    const page = await listSolutionPage(problemId.value, {
      pageNum: solPageNum.value,
      pageSize: solPageSize.value,
      sort: sort.value,
    })
    solutions.value = page.records || []
    solTotal.value = page.total || 0
    if (page.pageNum && page.pageNum !== solPageNum.value) solPageNum.value = page.pageNum
    pageError.value = ''

    // 没指定 id 时自动打开第一篇，省一次点击
    if (!solutionId.value && solutions.value.length) {
      await openSolution(solutions.value[0].id, { replaceQuery: true })
    }
  } catch (error) {
    pageError.value = errText(error)
  } finally {
    loading.value = false
  }
}

async function loadComments() {
  if (!current.value) return
  try {
    const page = await listCommentPage(current.value.id, {
      pageNum: cmtPageNum.value,
      pageSize: cmtPageSize.value,
    })
    comments.value = page.records || []
    cmtTotal.value = page.total || 0
    if (page.pageNum && page.pageNum !== cmtPageNum.value) cmtPageNum.value = page.pageNum
  } catch (error) {
    ElMessage.error(errText(error))
  }
}

async function openSolution(id, { replaceQuery = false } = {}) {
  try {
    current.value = await getSolution(id)
    cmtPageNum.value = 1
    await loadComments()
    if (replaceQuery) {
      router.replace({ name: 'solution', query: { problemId: problemId.value, id } })
    }
  } catch (error) {
    ElMessage.error(errText(error))
  }
}

/* ---------------- 题解编辑 ---------------- */

function openEditor(solution = null) {
  if (!auth.token) return openLogin()
  editing.value = !!solution
  formTitle.value = solution?.title || ''
  formContent.value = solution?.content || ''
  editorOpen.value = true
}

async function saveSolution() {
  const title = formTitle.value.trim()
  const content = formContent.value.trim()
  if (!title) return ElMessage.warning('标题不能为空')
  if (!content) return ElMessage.warning('内容不能为空')

  saving.value = true
  try {
    const saved = editing.value
      ? await updateSolution(current.value.id, title, content)
      : await createSolution(problemId.value, title, content)
    editorOpen.value = false
    ElMessage.success(editing.value ? '已保存' : '发布成功')
    // 列表要刷新（新题解会出现在最前），当前打开的也要跟着更新
    await loadSolutions()
    await openSolution(saved.id, { replaceQuery: true })
  } catch (error) {
    if (!error.unauthorized) ElMessage.error(errText(error))
  } finally {
    saving.value = false
  }
}

async function removeSolution() {
  try {
    await ElMessageBox.confirm('删除后该题解及其全部评论将不可访问，确定吗？', '删除题解', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteSolution(current.value.id)
    ElMessage.success('已删除')
    current.value = null
    comments.value = []
    router.replace({ name: 'solution', query: { problemId: problemId.value } })
    await loadSolutions()
  } catch (error) {
    if (!error.unauthorized) ElMessage.error(errText(error))
  }
}

/* ---------------- 评论 ---------------- */

function handleLike(next) {
  if (!auth.token) {
    openLogin()
    return Promise.reject(Object.assign(new Error('请先登录'), { unauthorized: true }))
  }
  return likeSolution(current.value.id, next)
}

async function postComment() {
  const content = newComment.value.trim()
  if (!content) return ElMessage.warning('评论内容不能为空')
  if (!auth.token) return openLogin()

  posting.value = true
  try {
    await createComment(current.value.id, content, null)
    newComment.value = ''
    ElMessage.success('评论成功')
    cmtPageNum.value = 1
    await loadComments()
    // 题解上的"评论数"变了，同步一下详情
    current.value = await getSolution(current.value.id)
  } catch (error) {
    if (!error.unauthorized) ElMessage.error(errText(error))
  } finally {
    posting.value = false
  }
}

/** 新回复插到对应主评论下，不整页刷新 —— 但分页/计数要同步 */
async function onReplyCreated(reply) {
  const root = comments.value.find((c) => c.id === (reply.rootId ?? reply.parentId))
  if (root) {
    root.replies = [...(root.replies || []), reply]
    root.replyCount = (root.replyCount || 0) + 1
  } else {
    await loadComments()
  }
  current.value = await getSolution(current.value.id)
}

async function onCommentRemoved() {
  await loadComments()
  current.value = await getSolution(current.value.id)
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/* ---------------- 分页 ---------------- */

// 抽成具名函数而不是模板里的内联箭头：模板里写 `solPageNum = p` 改的是 ref，
// 在模板上下文里能生效但可读性差，也不利于以后加埋点
function changeSolPage(page) {
  solPageNum.value = page
  loadSolutions()
}

function changeCmtPage(page) {
  cmtPageNum.value = page
  loadComments()
}

/* ---------------- 路由联动 ---------------- */

watch(
  () => route.query.id,
  async (next) => {
    const id = Number(next) || null
    if (!id) {
      current.value = null
      comments.value = []
      return
    }
    if (current.value?.id !== id) await openSolution(id)
  },
)

watch(sort, async () => {
  solPageNum.value = 1
  await loadSolutions()
})

onMounted(async () => {
  if (!problemId.value) {
    pageError.value = '缺少题目 ID，请从题目页进入题解区。'
    return
  }
  await loadProblem()
  await loadSolutions()
  if (solutionId.value && current.value?.id !== solutionId.value) {
    await openSolution(solutionId.value)
  }
})

// LoginDialog 在登录成功后要重新拉数据（liked / mine 都会变）
function onLoggedIn() {
  loginVisible.value = false
  loadSolutions()
  if (current.value) openSolution(current.value.id)
}
</script>

<template>
  <main class="solution-page">
    <div class="page-head">
      <div class="head-text">
        <div class="head-top">
          <button class="back-link" type="button" @click="router.push({ name: 'problem', query: { problemId } })">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M15 5l-7 7 7 7" />
            </svg>
            返回题目
          </button>
          <span class="crumb-sep">/</span>
          <span class="crumb">{{ problem?.title || '题解' }}</span>
        </div>
        <h1>题解</h1>
        <p>这道题的思路分享与讨论。共 {{ solTotal }} 篇题解。</p>
      </div>
      <div class="page-actions">
        <el-button type="primary" @click="openEditor()">写题解</el-button>
      </div>
    </div>

    <el-alert v-if="pageError" :title="pageError" type="error" show-icon :closable="false" class="page-error" />

    <div v-else class="layout">
      <!-- 左：题解列表 -->
      <aside class="list-pane">
        <div class="list-head">
          <!-- Element Plus 2.6+ 用 value 而不是 label 表示选中值（label 已废弃） -->
          <el-radio-group v-model="sort" size="small">
            <el-radio-button value="new">最新</el-radio-button>
            <el-radio-button value="hot">最热</el-radio-button>
          </el-radio-group>
        </div>

        <div v-loading="loading" class="list-body">
          <p v-if="!loading && !solutions.length" class="empty">还没有题解，来写第一篇吧。</p>

          <button
            v-for="item in solutions"
            :key="item.id"
            class="sol-card"
            :class="{ active: current?.id === item.id }"
            type="button"
            @click="openSolution(item.id)"
          >
            <div class="sol-title">{{ item.title }}</div>
            <div class="sol-excerpt">{{ item.excerpt }}</div>
            <div class="sol-meta">
              <span class="author">{{ item.nickname }}</span>
              <span class="dot">·</span>
              <span>{{ item.likeCount }} 赞</span>
              <span class="dot">·</span>
              <span>{{ item.commentCount }} 评论</span>
              <span v-if="item.mine" class="mine-chip">我的</span>
            </div>
          </button>
        </div>

        <div v-if="solTotal > solPageSize" class="pager">
          <!-- el-pagination 的 current-page / page-size 在新版本里是 currentPage / pageSize（v-model 风格） -->
          <el-pagination
            small
            background
            layout="prev, pager, next"
            :current-page="solPageNum"
            :page-size="solPageSize"
            :total="solTotal"
            @current-change="changeSolPage"
          />
        </div>
      </aside>

      <!-- 右：题解详情 + 评论 -->
      <section class="detail-pane">
        <template v-if="current">
          <article class="sol-detail">
            <h2 class="detail-title">{{ current.title }}</h2>
            <div class="detail-meta">
              <span class="author">{{ current.nickname }}</span>
              <span class="dot">·</span>
              <span>{{ formatTime(current.createTime) }}</span>
              <span v-if="current.updateTime !== current.createTime" class="edited">已编辑</span>
              <span class="spacer"></span>
              <template v-if="current.mine">
                <button class="link-btn" type="button" @click="openEditor(current)">编辑</button>
                <button class="link-btn danger" type="button" @click="removeSolution">删除</button>
              </template>
            </div>

            <pre class="detail-content">{{ current.content }}</pre>

            <div class="detail-actions">
              <LikeButton
                :liked="!!current.liked"
                :count="current.likeCount || 0"
                :handler="handleLike"
                :on-require-login="openLogin"
                @change="(v) => { current.liked = v.liked; current.likeCount = v.likeCount }"
              />
              <span class="comment-count">{{ current.commentCount }} 条评论</span>
            </div>
          </article>

          <!-- 评论区 -->
          <section class="comment-section">
            <h3 class="section-title">评论</h3>

            <div class="editor">
              <el-input
                v-model="newComment"
                type="textarea"
                :rows="3"
                maxlength="500"
                show-word-limit
                placeholder="说点什么…（登录后可发表评论）"
              />
              <div class="editor-actions">
                <el-button type="primary" size="small" :loading="posting" @click="postComment">发表评论</el-button>
              </div>
            </div>

            <p v-if="!comments.length" class="empty">还没有评论。</p>

            <div class="comment-list">
              <CommentItem
                v-for="item in comments"
                :key="item.id"
                :comment="item"
                :depth="0"
                :on-require-login="openLogin"
                @created="onReplyCreated"
                @removed="onCommentRemoved"
              />
            </div>

            <div v-if="cmtPages > 1" class="pager">
              <el-pagination
                background
                layout="prev, pager, next"
                :current-page="cmtPageNum"
                :page-size="cmtPageSize"
                :total="cmtTotal"
                @current-change="changeCmtPage"
              />
            </div>
          </section>
        </template>

        <p v-else class="empty big">从左侧选一篇题解，或点右上角"写题解"。</p>
      </section>
    </div>

    <!-- 编辑器 -->
    <el-dialog v-model="editorOpen" :title="editing ? '编辑题解' : '写题解'" width="720px" top="6vh">
      <el-input v-model="formTitle" maxlength="100" show-word-limit placeholder="标题，例如：双指针 O(n) 解法" />
      <el-input
        v-model="formContent"
        class="content-input"
        type="textarea"
        :rows="16"
        maxlength="10000"
        show-word-limit
        placeholder="写下你的思路。支持换行，暂不支持 Markdown。"
      />
      <template #footer>
        <el-button @click="editorOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSolution">保存</el-button>
      </template>
    </el-dialog>

    <LoginDialog v-model="loginVisible" @success="onLoggedIn" />
  </main>
</template>

<style scoped>
.solution-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 30px 24px 64px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--line);
}

.head-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 12px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-size: 12px;
  cursor: pointer;
}

.crumb-sep {
  color: var(--ink-5);
}

.crumb {
  overflow: hidden;
  color: var(--ink-4);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-head h1 {
  margin: 0 0 6px;
  font-size: 25px;
  font-weight: 650;
  letter-spacing: -0.4px;
  color: var(--ink);
}

.page-head p {
  margin: 0;
  color: var(--ink-3);
  font-size: 13px;
}

.page-error {
  margin-top: 18px;
}

.layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 24px;
  margin-top: 20px;
  align-items: start;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
  }
}

/* 左侧列表 */
.list-pane {
  position: sticky;
  top: 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.list-head {
  padding: 12px 14px;
  border-bottom: 1px solid var(--line-soft);
}

.list-body {
  max-height: 62vh;
  overflow-y: auto;
}

.sol-card {
  display: block;
  width: 100%;
  padding: 12px 14px;
  border: 0;
  border-bottom: 1px solid var(--line-soft);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.sol-card:hover {
  background: var(--surface-2, #fafafa);
}

.sol-card.active {
  background: var(--accent-soft, #e8f1ff);
}

.sol-title {
  margin-bottom: 4px;
  overflow: hidden;
  color: var(--ink);
  font-size: 13.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sol-excerpt {
  display: -webkit-box;
  overflow: hidden;
  margin-bottom: 6px;
  color: var(--ink-4);
  font-size: 12px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.sol-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--ink-5);
  font-size: 11.5px;
}

.dot {
  color: var(--ink-5);
}

.mine-chip {
  margin-left: auto;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--accent-soft, #e8f1ff);
  color: var(--accent);
  font-size: 10.5px;
}

.pager {
  display: flex;
  justify-content: center;
  padding: 12px 0;
}

/* 右侧详情 */
.detail-pane {
  min-width: 0;
}

.sol-detail {
  padding: 22px 24px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.detail-title {
  margin: 0 0 10px;
  font-size: 20px;
  font-weight: 650;
  color: var(--ink);
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 18px;
  color: var(--ink-5);
  font-size: 12px;
}

.author {
  color: var(--ink-3);
  font-weight: 600;
}

.edited {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--surface-2, #f3f3f3);
  font-size: 10.5px;
}

.spacer {
  flex: 1;
}

.link-btn {
  padding: 0 2px;
  border: 0;
  background: transparent;
  color: var(--ink-4);
  font-size: 12px;
  cursor: pointer;
}

.link-btn:hover {
  color: var(--accent);
}

.link-btn.danger:hover {
  color: #d4380d;
}

.detail-content {
  margin: 0;
  padding: 0;
  color: var(--ink-2);
  font-family: inherit;
  font-size: 14px;
  line-height: 1.8;
  /* 保留换行；不用 v-html，一期纯文本，自然没有 XSS 面 */
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px solid var(--line-soft);
}

.comment-count {
  color: var(--ink-5);
  font-size: 12.5px;
}

/* 评论区 */
.comment-section {
  margin-top: 20px;
  padding: 20px 24px 24px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.section-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}

.editor {
  margin-bottom: 8px;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.comment-list {
  margin-top: 8px;
}

.empty {
  margin: 16px 0;
  color: var(--ink-5);
  font-size: 13px;
  text-align: center;
}

.empty.big {
  margin-top: 80px;
  font-size: 14px;
}

.content-input {
  margin-top: 12px;
}
</style>
