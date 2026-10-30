<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getCurrentUser } from '@/services/ojApi'

const router = useRouter()

const user = ref(null)
const loading = ref(true)
const pageError = ref('')
const activeTab = ref('overview')

const tabs = [
  { key: 'overview', label: '概览' },
  { key: 'submissions', label: '我的提交' },
  { key: 'achievements', label: '成就' },
]

const displayName = computed(() => user.value?.nickname || user.value?.username || '未登录用户')
const avatarLetter = computed(() => (displayName.value || 'U').slice(0, 1).toUpperCase())
const isAdmin = computed(() => user.value?.role === 'ADMIN')
const roleText = computed(() => (isAdmin.value ? '管理员' : '普通用户'))
const joinDate = computed(() => formatDate(user.value?.createTime))
const statusText = computed(() => (user.value?.status === 1 ? '正常' : '已禁用'))

function requestErrorMessage(error) {
  return error instanceof TypeError ? '无法连接服务器，请检查后端服务。' : error.message || '请求失败，请稍后重试。'
}

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

async function loadUser() {
  loading.value = true
  pageError.value = ''
  try {
    user.value = await getCurrentUser()
  } catch (error) {
    pageError.value = requestErrorMessage(error)
  } finally {
    loading.value = false
  }
}

function goProblems() {
  router.push({ name: 'problemset' })
}

function goSubmissions() {
  router.push({ name: 'submissions' })
}

onMounted(loadUser)
</script>

<template>
  <main class="profile-page">
    <header class="profile-topbar">
      <div class="topbar-inner">
        <div class="topbar-left">
          <button class="back-button" type="button" title="返回题目列表" aria-label="返回题目列表" @click="goProblems">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.9">
              <path d="M15 5l-7 7 7 7" />
            </svg>
          </button>
          <span class="topbar-title">个人主页</span>
        </div>
        <div class="topbar-right">
          <button class="header-action" type="button" @click="goProblems">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8">
              <rect x="4" y="4" width="7" height="7" rx="1" />
              <rect x="13" y="4" width="7" height="7" rx="1" />
              <rect x="4" y="13" width="7" height="7" rx="1" />
              <rect x="13" y="13" width="7" height="7" rx="1" />
            </svg>
            题目列表
          </button>
          <button class="header-action" type="button" @click="goSubmissions">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M8 6h12" />
              <path d="M8 12h12" />
              <path d="M8 18h12" />
              <circle cx="4" cy="6" r="1" fill="currentColor" />
              <circle cx="4" cy="12" r="1" fill="currentColor" />
              <circle cx="4" cy="18" r="1" fill="currentColor" />
            </svg>
            我的提交
          </button>
          <span class="avatar-mini">{{ avatarLetter }}</span>
        </div>
      </div>
    </header>

    <div class="profile-container">
      <div v-if="pageError" class="error-banner">
        <span>{{ pageError }}</span>
        <button type="button" @click="loadUser">重试</button>
      </div>

      <section class="profile-hero">
        <div class="hero-banner">
          <div class="banner-glow"></div>
        </div>

        <div class="hero-main">
          <div class="avatar-xl">{{ avatarLetter }}</div>
          <div class="hero-identity">
            <div class="name-line">
              <h1>{{ loading ? '加载中…' : displayName }}</h1>
              <span class="role-chip" :class="{ 'is-admin': isAdmin }">{{ roleText }}</span>
            </div>
            <p class="handle">
              <span>@{{ user?.username || '—' }}</span>
              <span class="info-sep"></span>
              <span>编号 {{ user?.id || '—' }}</span>
            </p>
            <p class="bio">{{ user?.bio || '这个人很懒，什么都没有留下' }}</p>
            <div class="meta-line">
              <span>
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path d="M3 10l9-6 9 6" />
                  <path d="M5 10v9h14v-9" />
                </svg>
                {{ user?.school || '未填写学校' }}
              </span>
              <span>
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8">
                  <rect x="4" y="5" width="16" height="16" rx="2" />
                  <path d="M8 3v4M16 3v4M4 10h16" />
                </svg>
                注册于 {{ joinDate }}
              </span>
              <span>
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8">
                  <rect x="3" y="5" width="18" height="14" rx="2" />
                  <path d="M3 7l9 6 9-6" />
                </svg>
                {{ user?.email || '未绑定邮箱' }}
              </span>
            </div>
          </div>
          <button class="edit-button" type="button" disabled title="登录功能完成后开放">编辑资料</button>
        </div>

        <div class="hero-stats">
          <div class="stat">
            <b>{{ loading ? '—' : user?.rating ?? '—' }}</b>
            <span>Rating</span>
          </div>
          <div class="stat">
            <b>{{ loading ? '—' : user?.solvedCount ?? 0 }}</b>
            <span>通过题目</span>
          </div>
          <div class="stat">
            <b>{{ loading ? '—' : user?.submitCount ?? 0 }}</b>
            <span>提交次数</span>
          </div>
          <div class="stat">
            <b>—</b>
            <span>全站排名</span>
          </div>
        </div>
      </section>

      <nav class="profile-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
        <span class="tabs-note">登录功能开发中，部分数据为预留展示</span>
      </nav>

      <div v-if="activeTab === 'overview'" class="profile-grid">
        <section class="panel rating-panel">
          <div class="panel-head">
            <h2>Rating 趋势</h2>
            <span>当前 {{ user?.rating ?? '—' }} 分</span>
          </div>
          <div class="chart-wrap">
            <svg class="rating-chart" viewBox="0 0 640 220" preserveAspectRatio="none" aria-label="Rating 趋势占位图">
              <g class="chart-grid">
                <line x1="56" y1="30" x2="612" y2="30" />
                <line x1="56" y1="75" x2="612" y2="75" />
                <line x1="56" y1="120" x2="612" y2="120" />
                <line x1="56" y1="165" x2="612" y2="165" />
                <line x1="56" y1="210" x2="612" y2="210" />
              </g>
              <g class="chart-labels">
                <text x="44" y="34" text-anchor="end">1300</text>
                <text x="44" y="79" text-anchor="end">1200</text>
                <text x="44" y="124" text-anchor="end">1100</text>
                <text x="44" y="169" text-anchor="end">1000</text>
                <text x="44" y="214" text-anchor="end">900</text>
              </g>
              <line class="chart-baseline" x1="56" y1="165" x2="588" y2="165" />
              <circle class="chart-dot-halo" cx="588" cy="165" r="9" />
              <circle class="chart-dot" cx="588" cy="165" r="4" />
              <text class="chart-tag" x="588" y="150" text-anchor="middle">初始 1000</text>
            </svg>
            <p class="chart-note">暂无 Rating 变化记录，参加 Rating 赛后这里会自动生成曲线。</p>
          </div>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h2>做题情况</h2>
          </div>
          <div class="solved-summary">
            <b>{{ user?.solvedCount ?? 0 }}</b>
            <div>
              <strong>已通过题目</strong>
              <p>共提交 {{ user?.submitCount ?? 0 }} 次</p>
            </div>
          </div>
          <div class="empty-block">
            <span>暂无做题分布数据</span>
            <p>通过题目后会在这里展示难度分布与通过率。</p>
          </div>
          <p class="panel-note">通过率与题目分布依赖个人提交统计接口，登录功能上线后补齐。</p>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h2>资料信息</h2>
          </div>
          <dl class="info-list">
            <div><dt>用户编号</dt><dd>{{ user?.id ?? '—' }}</dd></div>
            <div><dt>用户名</dt><dd>{{ user?.username || '—' }}</dd></div>
            <div><dt>昵称</dt><dd>{{ user?.nickname || '—' }}</dd></div>
            <div><dt>用户类型</dt><dd>{{ roleText }}</dd></div>
            <div><dt>账号状态</dt><dd>{{ statusText }}</dd></div>
            <div><dt>注册时间</dt><dd>{{ joinDate }}</dd></div>
            <div><dt>学校</dt><dd>{{ user?.school || '未填写' }}</dd></div>
            <div><dt>邮箱</dt><dd>{{ user?.email || '未绑定' }}</dd></div>
          </dl>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h2>成就徽章</h2>
            <span>1 / 6</span>
          </div>
          <div class="badge-grid">
            <div class="badge earned">
              <span class="badge-icon">★</span>
              <b>初来乍到</b>
              <small>注册账号</small>
            </div>
            <div class="badge locked">
              <span class="badge-icon">✓</span>
              <b>首题通过</b>
              <small>通过 1 道题</small>
            </div>
            <div class="badge locked">
              <span class="badge-icon">✦</span>
              <b>小试牛刀</b>
              <small>通过 10 道题</small>
            </div>
            <div class="badge locked">
              <span class="badge-icon">◆</span>
              <b>百题斩</b>
              <small>通过 100 道题</small>
            </div>
            <div class="badge locked">
              <span class="badge-icon">↻</span>
              <b>连续打卡</b>
              <small>连续 7 天提交</small>
            </div>
            <div class="badge locked">
              <span class="badge-icon">⚑</span>
              <b>竞赛新星</b>
              <small>参加 1 场 Rating 赛</small>
            </div>
          </div>
          <p class="panel-note">成就系统为预留模块，暂未开放。</p>
        </section>
      </div>

      <section v-else-if="activeTab === 'submissions'" class="panel placeholder-panel">
        <span class="placeholder-icon">
          <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="1.6">
            <path d="M7 3h10a2 2 0 0 1 2 2v16l-7-4-7 4V5a2 2 0 0 1 2-2z" />
          </svg>
        </span>
        <h2>我的提交</h2>
        <p>目前提交记录仍按题目维度查询；个人全部提交需要等登录功能与提交表的 user_id 字段接入后展示。</p>
        <div class="placeholder-actions">
          <el-button type="success" plain @click="goSubmissions">查看提交记录</el-button>
          <el-button plain @click="goProblems">去刷题</el-button>
        </div>
      </section>

      <section v-else class="panel placeholder-panel">
        <span class="placeholder-icon">
          <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="1.6">
            <circle cx="12" cy="9" r="5" />
            <path d="M8.5 13.5L7 21l5-2.5L17 21l-1.5-7.5" />
          </svg>
        </span>
        <h2>成就系统</h2>
        <p>成就、连续打卡与比赛勋章将在比赛功能完成后统一接入，当前保留展示位置。</p>
        <div class="placeholder-actions">
          <el-button plain @click="activeTab = 'overview'">返回概览</el-button>
        </div>
      </section>

      <footer class="profile-foot">Online Judge · 账号信息将随登录功能逐步完善</footer>
    </div>
  </main>
</template>

<style scoped>
.profile-page {
  min-height: 100vh;
  background: var(--canvas);
  color: var(--ink);
}

.profile-topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  height: 52px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.topbar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1120px;
  height: 100%;
  margin: 0 auto;
  padding: 0 24px;
}

.topbar-left,
.topbar-right {
  display: flex;
  align-items: center;
  gap: 2px;
}

.back-button {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  margin-right: 6px;
  padding: 0;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-3);
  transition: background 0.15s ease, color 0.15s ease;
}

.back-button:hover {
  background: var(--surface-2);
  color: var(--ink);
}

.topbar-title {
  color: var(--ink);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.1px;
}

.header-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 10px;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-3);
  font-size: 12.5px;
  transition: background 0.15s ease, color 0.15s ease;
}

.header-action:hover {
  background: var(--surface-2);
  color: var(--ink);
}

.avatar-mini {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  margin-left: 8px;
  border-radius: 50%;
  background: linear-gradient(150deg, #414c58, #222a32);
  color: #ffffff;
  font-size: 12.5px;
  font-weight: 600;
}

.profile-container {
  max-width: 1120px;
  margin: 0 auto;
  padding: 22px 24px 64px;
}

.error-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding: 10px 14px;
  border: 1px solid var(--danger-line);
  border-radius: var(--r-md);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 12.5px;
}

.error-banner button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--danger);
  font-size: 12.5px;
  font-weight: 600;
  text-decoration: underline;
}

/* 横幅：只保留一个光源，去掉网格叠加，避免常见的「科技感」装饰 */
.profile-hero {
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow-raise);
}

.hero-banner {
  position: relative;
  height: 116px;
  background: linear-gradient(116deg, #14324a 0%, #1c4759 52%, #2a6367 100%);
}

.banner-glow {
  position: absolute;
  top: -150px;
  right: 6%;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(94, 234, 212, 0.24), transparent 70%);
}

.hero-main {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: flex-start;
  gap: 20px;
  padding: 0 26px 20px;
}

.avatar-xl {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 92px;
  height: 92px;
  margin-top: -44px;
  border: 4px solid var(--surface);
  border-radius: 50%;
  background: linear-gradient(150deg, #46525f, #232b33);
  color: #ffffff;
  font-size: 34px;
  font-weight: 600;
  box-shadow: 0 8px 20px rgba(15, 30, 45, 0.22);
}

.hero-identity {
  flex: 1;
  min-width: 0;
  padding-top: 16px;
}

.name-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.name-line h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 650;
  letter-spacing: -0.3px;
}

.role-chip {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: var(--r-pill);
  background: var(--surface-2);
  color: var(--ink-3);
  font-size: 11.5px;
}

.role-chip.is-admin {
  background: var(--warn-soft);
  color: var(--warn);
}

.handle {
  display: flex;
  align-items: center;
  margin-top: 6px;
  color: var(--ink-4);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.info-sep {
  display: inline-block;
  width: 1px;
  height: 10px;
  margin: 0 9px;
  background: var(--line);
}

.bio {
  margin-top: 10px;
  color: var(--ink-2);
  font-size: 13px;
  line-height: 1.7;
}

.meta-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
  color: var(--ink-4);
  font-size: 12px;
}

.meta-line span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.edit-button {
  flex: 0 0 auto;
  height: 30px;
  margin-top: 18px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--ink-4);
  font-size: 12.5px;
  cursor: not-allowed;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-top: 1px solid var(--line-soft);
  background: var(--surface-2);
}

.stat {
  padding: 14px 0 16px 26px;
  border-left: 1px solid var(--line-soft);
}

.stat:first-child {
  border-left: 0;
}

.stat b {
  display: block;
  color: var(--ink);
  font-size: 22px;
  font-weight: 650;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.3px;
}

.stat span {
  display: block;
  margin-top: 4px;
  color: var(--ink-4);
  font-size: 11.5px;
}

.profile-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  margin: 18px 0;
  padding: 0 6px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.profile-tabs button {
  position: relative;
  height: 46px;
  padding: 0 16px;
  border: 0;
  background: transparent;
  color: var(--ink-3);
  font-size: 13.5px;
  transition: color 0.15s ease;
}

.profile-tabs button:hover {
  color: var(--ink);
}

.profile-tabs button.active {
  color: var(--accent-strong);
  font-weight: 600;
}

.profile-tabs button.active::after {
  content: '';
  position: absolute;
  right: 12px;
  bottom: 0;
  left: 12px;
  height: 2px;
  border-radius: 2px;
  background: var(--accent);
}

.tabs-note {
  margin-left: auto;
  padding-right: 12px;
  color: var(--ink-4);
  font-size: 11.5px;
}

.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.75fr) minmax(0, 1fr);
  align-items: start;
  gap: 16px;
}

.panel {
  padding: 18px 20px 20px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.panel-head h2 {
  margin: 0;
  font-size: 14.5px;
  font-weight: 600;
}

.panel-head span {
  color: var(--ink-4);
  font-size: 11.5px;
}

.chart-wrap {
  position: relative;
}

.rating-chart {
  display: block;
  width: 100%;
  height: 226px;
}

.chart-grid line {
  stroke: var(--line-soft);
  stroke-width: 1;
}

.chart-labels text {
  fill: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 11px;
}

.chart-baseline {
  stroke: var(--line);
  stroke-width: 1.4;
  stroke-dasharray: 5 5;
}

.chart-dot {
  fill: var(--accent);
}

.chart-dot-halo {
  fill: rgba(26, 127, 75, 0.14);
}

.chart-tag {
  fill: var(--accent-strong);
  font-size: 11px;
}

.chart-note {
  margin-top: -8px;
  color: var(--ink-4);
  font-size: 11.5px;
  text-align: center;
}

.solved-summary {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 0 18px;
}

.solved-summary b {
  color: var(--accent);
  font-size: 38px;
  font-weight: 650;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.6px;
}

.solved-summary strong {
  display: block;
  font-size: 13px;
  font-weight: 600;
}

.solved-summary p {
  margin-top: 4px;
  color: var(--ink-4);
  font-size: 12px;
}

.empty-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 22px 12px;
  border: 1px dashed var(--line);
  border-radius: var(--r-md);
  background: var(--surface-2);
  color: var(--ink-3);
  font-size: 12.5px;
}

.empty-block p {
  color: var(--ink-4);
  font-size: 11.5px;
}

.panel-note {
  margin-top: 12px;
  color: var(--ink-4);
  font-size: 11.5px;
  line-height: 1.6;
}

.info-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 24px;
}

.info-list > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--line-soft);
  font-size: 12.5px;
}

.info-list dt {
  color: var(--ink-4);
}

.info-list dd {
  overflow: hidden;
  margin: 0;
  color: var(--ink);
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.badge-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 10px 6px;
  border: 1px solid var(--line-soft);
  border-radius: var(--r-md);
  background: var(--surface-2);
  text-align: center;
}

.badge-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  margin-bottom: 2px;
  border-radius: 50%;
  background: #eef1f4;
  color: var(--ink-5);
  font-size: 14px;
}

.badge b {
  color: var(--ink-3);
  font-size: 11.5px;
  font-weight: 600;
}

.badge small {
  color: var(--ink-4);
  font-size: 10.5px;
}

.badge.earned {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.badge.earned .badge-icon {
  background: #d9efe1;
  color: var(--accent);
}

.badge.earned b {
  color: var(--accent-strong);
}

.badge.locked {
  opacity: 0.86;
}

.placeholder-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 56px 24px;
  text-align: center;
}

.placeholder-icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin-bottom: 14px;
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--ink-4);
}

.placeholder-panel h2 {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
}

.placeholder-panel p {
  max-width: 54ch;
  margin: 0;
  color: var(--ink-4);
  font-size: 12.5px;
  line-height: 1.8;
}

.placeholder-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

.profile-foot {
  padding: 26px 4px 0;
  color: var(--ink-4);
  font-size: 11.5px;
  text-align: center;
}

@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .hero-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stat:nth-child(3) {
    border-left: 0;
    border-top: 1px solid var(--line-soft);
  }

  .stat:nth-child(4) {
    border-top: 1px solid var(--line-soft);
  }

  .tabs-note {
    display: none;
  }

  .edit-button {
    display: none;
  }
}

@media (max-width: 640px) {
  .topbar-inner,
  .profile-container {
    padding-right: 16px;
    padding-left: 16px;
  }

  .hero-main {
    flex-wrap: wrap;
    padding: 0 18px 16px;
  }

  .avatar-xl {
    width: 76px;
    height: 76px;
    margin-top: -38px;
    font-size: 28px;
  }

  .hero-identity {
    flex-basis: calc(100% - 96px);
    padding-top: 12px;
  }

  .name-line h1 {
    font-size: 18px;
  }

  .info-list {
    grid-template-columns: minmax(0, 1fr);
  }

  .badge-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
