<script setup>
/**
 * 题库页（默认首页）。
 *
 * 数据分两路，互不阻塞：
 *   overview  → 题目总数、难度分布、完成情况、标签计数、每日一题
 *   problems  → 当前筛选条件下的列表（后端返回全量，前端按 15 条分页）
 *
 * 筛选条件变化只重新请求列表，不重新请求概览，
 * 因为概览统计的是整个题库，不应该跟着筛选条件变化。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProblem, getProblemSetOverview, listProblemSetProblems } from '@/services/ojApi'

const router = useRouter()

const overview = ref(null)
const problems = ref([])
const loading = ref(true)
const listLoading = ref(false)
const overviewError = ref('')
const listError = ref('')
const keyword = ref('')
const difficulty = ref(0)
const activeTag = ref('')
const page = ref(1)
// 后端列表接口不分页，这里按 15 条切片；总页数由筛选结果长度算出。
const pageSize = 15

const weekLabels = ['日', '一', '二', '三', '四', '五', '六']
const today = new Date()
const todayDate = today.getDate()
const monthLabel = `${today.getFullYear()}.${String(today.getMonth() + 1).padStart(2, '0')}`

let searchTimer = null
// 请求版本号：输入搜索时可能连续发出多个请求，只有最后一次的结果允许写入，
// 否则慢的旧响应会覆盖新响应，出现“输入框是新词、列表是旧结果”的错位。
let requestVersion = 0

const pageError = computed(() => overviewError.value || listError.value)

const difficultyTabs = computed(() => {
  const data = overview.value
  return [
    { value: 0, label: '全部题目', count: data?.total ?? 0 },
    { value: 1, label: '简单', count: data?.easyCount ?? 0, tone: 'easy' },
    { value: 2, label: '中等', count: data?.mediumCount ?? 0, tone: 'medium' },
    { value: 3, label: '困难', count: data?.hardCount ?? 0, tone: 'hard' },
  ]
})

const daily = computed(() => overview.value?.daily || null)

const progress = computed(() => {
  const data = overview.value
  if (!data) return []
  return [
    { label: '简单', solved: data.easySolved ?? 0, total: data.easyCount ?? 0, tone: 'easy' },
    { label: '中等', solved: data.mediumSolved ?? 0, total: data.mediumCount ?? 0, tone: 'medium' },
    { label: '困难', solved: data.hardSolved ?? 0, total: data.hardCount ?? 0, tone: 'hard' },
  ]
})

// 总完成率：已通过题数 / 总题数，四舍五入成整数百分比，用于进度卡的大字和进度条。
const overallRate = computed(() => {
  const data = overview.value
  if (!data || !data.total) return 0
  return Math.round((data.solvedCount / data.total) * 100)
})

const totalPages = computed(() => Math.max(1, Math.ceil(problems.value.length / pageSize)))

const pagedProblems = computed(() => {
  const start = (page.value - 1) * pageSize
  return problems.value.slice(start, start + pageSize)
})

// 日历只负责展示当前月的日期网格并高亮今天，不承载打卡/提交记录。
const calendarCells = computed(() => {
  const firstWeekday = new Date(today.getFullYear(), today.getMonth(), 1).getDay()
  const daysInMonth = new Date(today.getFullYear(), today.getMonth() + 1, 0).getDate()
  const cells = []
  for (let i = 0; i < firstWeekday; i += 1) cells.push(null)
  for (let day = 1; day <= daysInMonth; day += 1) cells.push(day)
  return cells
})

const difficultyLabel = (value) => ({ 1: '简单', 2: '中等', 3: '困难' }[value] || '基础')
const difficultyTone = (value) => ({ 1: 'easy', 2: 'medium', 3: 'hard' }[value] || 'basic')

function requestErrorMessage(error) {
  return error instanceof TypeError ? '无法连接服务器，请检查后端服务。' : error.message || '请求失败，请稍后重试。'
}

/**
 * 列表状态三态，优先级：已通过 > 尝试过 > 未开始。
 * solved / attempted 都来自后端聚合，同一题只要出现过 AC 就永远算“已通过”。
 */
function statusOf(item) {
  if (item.solved) return 'solved'
  if (item.attempted) return 'attempted'
  return 'todo'
}

function statusLabel(item) {
  if (item.solved) return '已通过'
  if (item.attempted) return '尝试过'
  return '未开始'
}

function passRateText(item) {
  if (!item.submitCount) return '—'
  return `${Number(item.passRate || 0).toFixed(1)}%`
}

function tagLine(tags) {
  return (tags || '')
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean)
    .slice(0, 3)
    .join(' · ')
}

async function loadOverview() {
  overviewError.value = ''
  try {
    overview.value = await getProblemSetOverview()
  } catch (error) {
    overviewError.value = requestErrorMessage(error)
  }
}

/**
 * 加载列表。
 * 成功时用请求版本号挡掉过期响应；失败时清空列表并给出提示，
 * 避免旧数据留在页面上让人误以为筛选生效了。
 */
async function loadProblems() {
  const version = ++requestVersion
  listLoading.value = true
  listError.value = ''
  try {
    const list = await listProblemSetProblems({
      keyword: keyword.value.trim(),
      difficulty: difficulty.value || undefined,
      tag: activeTag.value || undefined,
    })
    if (version !== requestVersion) return
    problems.value = list || []
    page.value = Math.min(page.value, Math.max(1, Math.ceil(problems.value.length / pageSize)))
  } catch (error) {
    if (version !== requestVersion) return
    problems.value = []
    page.value = 1
    listError.value = requestErrorMessage(error)
  } finally {
    if (version === requestVersion) listLoading.value = false
  }
}

async function loadAll() {
  loading.value = true
  await Promise.all([loadOverview(), loadProblems()])
  loading.value = false
}

function openProblem(item) {
  if (!item) return
  router.push({ name: 'problem', query: { problemId: item.id } })
}

function pickDifficulty(value) {
  if (difficulty.value === value) return
  difficulty.value = value
}

function toggleTag(name) {
  activeTag.value = activeTag.value === name ? '' : name
  page.value = 1
  loadProblems()
}

function clearFilters() {
  keyword.value = ''
  difficulty.value = 0
  activeTag.value = ''
  page.value = 1
  loadProblems()
}

/**
 * 搜索框回车：
 *   - 纯数字（允许带 #）→ 当作题号，先调详情接口确认存在，再跳解题页，
 *     不存在就提示而不是把用户带到一个空白页；
 *   - 非数字 → 如果模糊搜索结果刚好只有一条，直接打开它，省一次点击。
 */
async function submitSearch() {
  const raw = keyword.value.trim().replace(/^#/, '')
  if (!/^\d+$/.test(raw)) {
    if (problems.value.length === 1) openProblem(problems.value[0])
    return
  }
  const id = Number(raw)
  try {
    await getProblem(id)
    router.push({ name: 'problem', query: { problemId: id } })
  } catch (error) {
    ElMessage.warning(error.message || `题号 ${id} 不存在`)
  }
}

function goPage(delta) {
  page.value = Math.min(totalPages.value, Math.max(1, page.value + delta))
}

watch(difficulty, () => {
  page.value = 1
  loadProblems()
})

// 输入即筛选，但做 260ms 防抖：既避免每敲一个字符打一次接口，
// 也避免中文输入法还在拼写时就触发请求。
watch(keyword, () => {
  if (searchTimer !== null) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    page.value = 1
    loadProblems()
  }, 260)
})

onMounted(loadAll)

onBeforeUnmount(() => {
  // 离开页面时清掉未触发的防抖，并把版本号推进，
  // 让仍在途中的请求回来时被判定为“过期响应”，不再写入已卸载组件的状态。
  if (searchTimer !== null) window.clearTimeout(searchTimer)
  requestVersion += 1
})
</script>

<template>
  <main class="set-page">
    <div class="set-container">
      <header class="set-head">
        <div class="head-copy">
          <span class="eyebrow">Problem Set</span>
          <h1>题库</h1>
          <p v-if="overview">
            共 {{ overview.total }} 道题 · 已通过 {{ overview.solvedCount }} 道 · 覆盖 {{ overview.tags.length }} 个标签
          </p>
          <p v-else>挑一道题，开始今天的练习。</p>
        </div>
        <div v-if="overview" class="head-stats">
          <div class="head-stat">
            <span>已通过</span>
            <strong>{{ overview.solvedCount }}</strong>
          </div>
          <span class="stat-divider"></span>
          <div class="head-stat">
            <span>题目总数</span>
            <strong>{{ overview.total }}</strong>
          </div>
        </div>
      </header>

      <div v-if="pageError" class="error-banner">
        <span>{{ pageError }}</span>
        <button type="button" @click="loadAll">重试</button>
      </div>

      <div class="set-layout">
        <div class="set-main">
          <!-- 标签条：单选筛选，再次点击同一标签即取消；计数来自后端概览 -->
          <div v-if="overview?.tags?.length" class="tag-strip">
            <span class="strip-label">标签</span>
            <button
              v-for="tag in overview.tags"
              :key="tag.name"
              class="tag-chip"
              :class="{ active: activeTag === tag.name }"
              type="button"
              @click="toggleTag(tag.name)"
            >
              {{ tag.name }}<span>{{ tag.count }}</span>
            </button>
          </div>

          <div class="list-toolbar">
            <div class="tabs" role="tablist" aria-label="难度筛选">
              <button
                v-for="tab in difficultyTabs"
                :key="tab.value"
                class="tab"
                :class="{ active: difficulty === tab.value }"
                type="button"
                role="tab"
                :aria-selected="difficulty === tab.value"
                @click="pickDifficulty(tab.value)"
              >
                {{ tab.label }}<span>{{ tab.count }}</span>
              </button>
            </div>

            <label class="search-field">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="6.5" />
                <path d="M16 16l4 4" />
              </svg>
              <input
                v-model="keyword"
                type="search"
                placeholder="搜索题目，输入题号回车直达"
                aria-label="搜索题目，输入题号回车直达"
                @keydown.enter.prevent="submitSearch"
              />
              <button v-if="keyword" class="search-clear" type="button" aria-label="清空搜索" @click="keyword = ''">×</button>
            </label>
          </div>

          <section class="list-card">
            <div class="list-head">
              <span>状态</span>
              <span>题目</span>
              <span class="col-rate">通过率</span>
              <span class="col-diff">难度</span>
            </div>

            <div v-if="loading" class="skeleton">
              <div v-for="index in 7" :key="index" class="skeleton-row"></div>
            </div>

            <div v-else-if="!problems.length" class="list-empty">
              <p>没有找到匹配的题目</p>
              <button type="button" @click="clearFilters">清除筛选条件</button>
            </div>

            <div v-else class="list-body" :class="{ 'is-updating': listLoading }">
              <button
                v-for="item in pagedProblems"
                :key="item.id"
                class="problem-row"
                type="button"
                @click="openProblem(item)"
              >
                <span class="row-status" :class="'is-' + statusOf(item)" :title="statusLabel(item)">
                  <svg v-if="item.solved" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.1">
                    <circle cx="12" cy="12" r="9" />
                    <path d="M8 12.2l2.6 2.6L16 9.4" />
                  </svg>
                  <span v-else-if="item.attempted" class="status-dot"></span>
                  <span v-else class="status-ring"></span>
                </span>
                <span class="row-title"><span class="row-index">{{ item.id }}.</span>{{ item.title }}</span>
                <span class="row-rate">{{ passRateText(item) }}</span>
                <span class="row-diff" :class="'tone-' + difficultyTone(item.difficulty)">{{ difficultyLabel(item.difficulty) }}</span>
              </button>
            </div>

            <!-- 分页是纯前端切片：列表数据来自后端一次返回的筛选结果 -->
            <footer class="list-foot">
              <span>共 {{ problems.length }} 道题</span>
              <div v-if="totalPages > 1" class="pager">
                <button type="button" :disabled="page === 1" @click="goPage(-1)">上一页</button>
                <span>{{ page }} / {{ totalPages }}</span>
                <button type="button" :disabled="page === totalPages" @click="goPage(1)">下一页</button>
              </div>
            </footer>
          </section>
        </div>

        <aside class="set-rail">
          <section v-if="daily" class="rail-card daily-card">
            <header class="rail-head">
              <h2>每日一题</h2>
              <span>{{ monthLabel }}</span>
            </header>

            <button class="daily-body" type="button" @click="openProblem(daily)">
              <div class="daily-meta">
                <span class="daily-no">No. {{ daily.id }}</span>
                <span class="row-diff" :class="'tone-' + difficultyTone(daily.difficulty)">{{ difficultyLabel(daily.difficulty) }}</span>
              </div>
              <h3>{{ daily.title }}</h3>
              <p>{{ tagLine(daily.tags) || '今日推荐' }}</p>
              <span class="daily-go">
                开始解题
                <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M5 12h13" />
                  <path d="M13 6l6 6-6 6" />
                </svg>
              </span>
            </button>

            <div class="calendar">
              <div class="calendar-week">
                <span v-for="week in weekLabels" :key="week">{{ week }}</span>
              </div>
              <div class="calendar-grid">
                <span
                  v-for="(day, index) in calendarCells"
                  :key="index"
                  class="calendar-day"
                  :class="{ empty: !day, today: day === todayDate }"
                >{{ day || '' }}</span>
              </div>
            </div>
          </section>

          <!-- 我的进度：全部来自概览接口，不随后面列表的筛选条件变化 -->
          <section v-if="overview" class="rail-card progress-card">
            <header class="rail-head">
              <h2>我的进度</h2>
              <span>{{ overallRate }}%</span>
            </header>
            <div class="progress-total">
              <strong>{{ overview.solvedCount }}</strong>
              <span>/ {{ overview.total }} 题已通过</span>
            </div>
            <div class="progress-bar"><span :style="{ width: overallRate + '%' }"></span></div>
            <ul class="progress-list">
              <li v-for="item in progress" :key="item.label">
                <span class="progress-label" :class="'text-' + item.tone">{{ item.label }}</span>
                <div class="progress-track">
                  <span
                    :class="'fill-' + item.tone"
                    :style="{ width: item.total ? Math.round((item.solved / item.total) * 100) + '%' : '0%' }"
                  ></span>
                </div>
                <span class="progress-num">{{ item.solved }}/{{ item.total }}</span>
              </li>
            </ul>
          </section>

          <section class="rail-card tip-card">
            <h2>怎么开始</h2>
            <p>点击任意题目进入解题页；在搜索框输入题号并回车，可直接跳转到对应题目。</p>
            <div class="shortcut">
              <kbd>Ctrl</kbd><span>+</span><kbd>Enter</kbd>
              <em>提交代码</em>
            </div>
          </section>
        </aside>
      </div>
    </div>
  </main>
</template>

<style scoped>
.set-page {
  min-height: calc(100vh - 52px);
  background: var(--canvas);
}

.set-container {
  max-width: 1180px;
  margin: 0 auto;
  padding: 30px 24px 64px;
}

.set-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--line);
}

.head-copy {
  min-width: 0;
}

.eyebrow {
  color: var(--ink-4);
  font: 600 10.5px/1 var(--font-mono);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.head-copy h1 {
  margin: 9px 0 6px;
  color: var(--ink);
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.6px;
}

.head-copy p {
  color: var(--ink-4);
  font-size: 12.5px;
}

.head-stats {
  display: flex;
  align-items: center;
  gap: 18px;
  padding-bottom: 3px;
}

.head-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.head-stat span {
  color: var(--ink-4);
  font-size: 11.5px;
}

.head-stat strong {
  color: var(--ink);
  font: 600 20px/1.15 var(--font-mono);
  font-variant-numeric: tabular-nums;
}

.stat-divider {
  width: 1px;
  height: 30px;
  background: var(--line);
}

.error-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
  padding: 9px 14px;
  border: 1px solid var(--danger-line);
  border-radius: var(--r-md);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 12.5px;
}

.error-banner button {
  padding: 3px 10px;
  border: 1px solid var(--danger-line);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--danger);
  font-size: 11.5px;
}

.set-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 292px;
  gap: 24px;
  align-items: start;
  margin-top: 20px;
}

.set-main {
  min-width: 0;
}

.tag-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 2px 0 14px;
}

.strip-label {
  margin-right: 4px;
  color: var(--ink-4);
  font-size: 11.5px;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  background: var(--surface);
  color: var(--ink-3);
  font-size: 12px;
  transition: border-color 0.15s ease, background 0.15s ease, color 0.15s ease;
}

.tag-chip span {
  color: var(--ink-5);
  font: 10.5px/1 var(--font-mono);
}

.tag-chip:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.tag-chip.active {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.tag-chip.active span {
  color: var(--accent);
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
}

.tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 11px;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-3);
  font-size: 12.5px;
  white-space: nowrap;
  transition: background 0.15s ease, color 0.15s ease;
}

.tab span {
  color: var(--ink-5);
  font: 10px/1 var(--font-mono);
}

.tab:hover {
  background: var(--surface-2);
  color: var(--ink);
}

.tab.active {
  background: var(--ink);
  color: #ffffff;
}

.tab.active span {
  color: rgba(255, 255, 255, 0.62);
}

.search-field {
  display: flex;
  flex: 0 1 290px;
  align-items: center;
  gap: 7px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.search-field:focus-within {
  border-color: var(--accent-line);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.search-field svg {
  flex: none;
  color: var(--ink-5);
}

.search-field input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 12.5px;
}

.search-field input::placeholder {
  color: var(--ink-5);
}

.search-field input::-webkit-search-cancel-button {
  display: none;
}

.search-clear {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--ink-4);
  font-size: 13px;
  line-height: 1;
}

.search-clear:hover {
  background: var(--line);
  color: var(--ink-2);
}

.list-card {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: var(--surface);
}

.list-head,
.problem-row {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) 84px 66px;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
}

.list-head {
  height: 34px;
  border-bottom: 1px solid var(--line);
  background: var(--surface-2);
  color: var(--ink-4);
  font-size: 11px;
  letter-spacing: 0.02em;
}

.col-rate,
.col-diff {
  text-align: right;
}

.problem-row {
  width: 100%;
  height: 46px;
  border: 0;
  border-bottom: 1px solid var(--line-soft);
  background: var(--surface);
  text-align: left;
  transition: background 0.12s ease;
}

.problem-row:last-child {
  border-bottom: 0;
}

.problem-row:hover {
  background: var(--surface-2);
}

.problem-row:focus-visible {
  outline-offset: -2px;
}

.row-status {
  display: grid;
  place-items: center;
  width: 20px;
  color: var(--ink-5);
}

.row-status.is-solved {
  color: var(--accent);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--warn);
  box-shadow: 0 0 0 3px var(--warn-soft);
}

.status-ring {
  width: 9px;
  height: 9px;
  border: 1.5px solid #cfd6dc;
  border-radius: 50%;
}

.row-title {
  overflow: hidden;
  color: var(--ink);
  font-size: 13.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-index {
  margin-right: 6px;
  color: var(--ink-4);
  font: 12px/1 var(--font-mono);
}

.row-rate {
  text-align: right;
  color: var(--ink-4);
  font: 12px/1 var(--font-mono);
  font-variant-numeric: tabular-nums;
}

.row-diff {
  justify-self: end;
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: var(--r-pill);
  font-size: 11.5px;
  line-height: 1;
  white-space: nowrap;
}

.tone-easy {
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.tone-medium {
  background: var(--warn-soft);
  color: var(--warn);
}

.tone-hard {
  background: var(--danger-soft);
  color: var(--danger);
}

.list-body.is-updating {
  opacity: 0.55;
  transition: opacity 0.15s ease;
}

.list-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 280px;
  color: var(--ink-4);
  font-size: 12.5px;
}

.list-empty button {
  height: 28px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--accent-strong);
  font-size: 12px;
}

.list-empty button:hover {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.skeleton-row {
  height: 46px;
  border-bottom: 1px solid var(--line-soft);
  background: linear-gradient(90deg, #f7f9fa 25%, #eef2f5 37%, #f7f9fa 63%);
  background-size: 400% 100%;
  animation: row-shimmer 1.4s ease infinite;
}

@keyframes row-shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

.list-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 42px;
  padding: 0 14px;
  border-top: 1px solid var(--line);
  background: var(--surface-2);
  color: var(--ink-4);
  font-size: 11.5px;
}

.pager {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pager button {
  height: 24px;
  padding: 0 8px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--ink-3);
  font-size: 11.5px;
}

.pager button:hover:not(:disabled) {
  border-color: var(--accent-line);
  color: var(--accent-strong);
}

.pager button:disabled {
  opacity: 0.45;
  cursor: default;
}

.set-rail {
  position: sticky;
  top: 68px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.rail-card {
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: var(--surface);
}

.rail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.rail-head h2 {
  color: var(--ink);
  font-size: 13px;
  font-weight: 600;
}

.rail-head span {
  color: var(--ink-4);
  font: 11px/1 var(--font-mono);
}

.daily-body {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
}

.daily-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.daily-no {
  color: var(--ink-4);
  font: 11px/1 var(--font-mono);
}

.daily-body h3 {
  margin: 9px 0 4px;
  color: var(--ink);
  font-size: 15px;
  font-weight: 600;
  transition: color 0.15s ease;
}

.daily-body p {
  color: var(--ink-4);
  font-size: 11.5px;
}

.daily-body:hover h3 {
  color: var(--accent-strong);
}

.daily-go {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 10px;
  color: var(--accent-strong);
  font-size: 12px;
}

.calendar {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--line-soft);
}

.calendar-week,
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
  text-align: center;
}

.calendar-week span {
  padding-bottom: 4px;
  color: var(--ink-5);
  font: 10px/1 var(--font-mono);
}

.calendar-day {
  display: grid;
  place-items: center;
  height: 26px;
  border-radius: 50%;
  color: var(--ink-3);
  font: 11px/1 var(--font-mono);
}

.calendar-day.empty {
  visibility: hidden;
}

.calendar-day.today {
  background: var(--ink);
  color: #ffffff;
  font-weight: 600;
}

.progress-total {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 8px;
}

.progress-total strong {
  color: var(--ink);
  font: 600 26px/1 var(--font-mono);
  font-variant-numeric: tabular-nums;
}

.progress-total span {
  color: var(--ink-4);
  font-size: 11.5px;
}

.progress-bar {
  height: 5px;
  overflow: hidden;
  border-radius: var(--r-pill);
  background: var(--surface-2);
}

.progress-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--accent);
  transition: width 0.3s ease;
}

.progress-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}

.progress-list li {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) 42px;
  align-items: center;
  gap: 8px;
}

.progress-label {
  font-size: 11.5px;
}

.text-easy {
  color: var(--accent-strong);
}

.text-medium {
  color: var(--warn);
}

.text-hard {
  color: var(--danger);
}

.progress-track {
  height: 4px;
  overflow: hidden;
  border-radius: var(--r-pill);
  background: var(--surface-2);
}

.progress-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.fill-easy {
  background: var(--accent);
}

.fill-medium {
  background: #c08a3e;
}

.fill-hard {
  background: #c25a53;
}

.progress-num {
  text-align: right;
  color: var(--ink-4);
  font: 10.5px/1 var(--font-mono);
}

.tip-card h2 {
  margin-bottom: 8px;
  color: var(--ink);
  font-size: 13px;
  font-weight: 600;
}

.tip-card p {
  color: var(--ink-4);
  font-size: 12px;
  line-height: 1.7;
}

.shortcut {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  color: var(--ink-4);
  font-size: 11px;
}

.shortcut kbd {
  padding: 2px 6px;
  border: 1px solid var(--line);
  border-bottom-width: 2px;
  border-radius: var(--r-sm);
  background: var(--surface-2);
  color: var(--ink-3);
  font: 10.5px/1.4 var(--font-mono);
}

.shortcut em {
  margin-left: auto;
  font-style: normal;
}

@media (max-width: 980px) {
  .set-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .set-rail {
    position: static;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .tip-card {
    grid-column: 1 / -1;
  }
}

@media (max-width: 720px) {
  .set-container {
    padding: 20px 14px 48px;
  }

  .set-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .head-stats {
    padding-bottom: 0;
  }

  .list-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .tabs {
    overflow-x: auto;
  }

  .search-field {
    flex-basis: auto;
    width: 100%;
  }

  .list-head,
  .problem-row {
    grid-template-columns: 32px minmax(0, 1fr) 66px 56px;
    padding: 0 10px;
  }

  .row-rate {
    font-size: 11px;
  }

  .set-rail {
    grid-template-columns: 1fr;
  }

  .tip-card {
    grid-column: auto;
  }
}
</style>
