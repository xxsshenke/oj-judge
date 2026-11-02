<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import LoginDialog from '@/components/LoginDialog.vue'
import { auth, clearAuth, isLoggedIn } from '@/services/auth'
import { logout as logoutApi, setUnauthorizedHandler } from '@/services/ojApi'

const route = useRoute()
const isSubmissions = computed(() => route.name === 'submissions')
const isFullBleed = computed(() => route.name === 'problem' || route.name === 'user')

const loginVisible = ref(false)

function openLogin() {
  loginVisible.value = true
}

// 任何请求拿到 401 都会走这里 —— 自动弹登录框
onMounted(() => {
  setUnauthorizedHandler(() => {
    if (!loginVisible.value) {
      loginVisible.value = true
    }
  })
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '退出', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await logoutApi()
  clearAuth()
  ElMessage.success('已退出登录')
}
</script>

<template>
  <div class="app-shell">
    <header v-if="!isFullBleed" class="app-bar">
      <RouterLink class="brand" to="/problemset">
        <span class="brand-mark">OJ</span>
        <span class="brand-text">
          <strong>Judge Desk</strong>
          <small>Java 21 · Online Judge</small>
        </span>
      </RouterLink>
      <nav class="app-nav">
        <RouterLink class="nav-link" to="/problemset">题库</RouterLink>
        <RouterLink v-if="!isSubmissions" class="nav-link" to="/submissions">提交记录</RouterLink>

        <!-- 未登录：一个登录按钮 -->
        <button v-if="!isLoggedIn()" class="login-btn" type="button" @click="openLogin">
          登录
        </button>

        <!-- 已登录：用户名 + 头像 + 退出 -->
        <template v-else>
          <span class="nav-user">{{ auth.user?.nickname || auth.user?.username }}</span>
          <RouterLink class="nav-avatar" to="/user" title="个人主页" aria-label="个人主页">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.9">
              <circle cx="12" cy="8.5" r="3.4" />
              <path d="M5.5 19.5a6.5 6.5 0 0 1 13 0" />
            </svg>
          </RouterLink>
          <button class="logout-btn" type="button" title="退出登录" @click="handleLogout">
            退出
          </button>
        </template>
      </nav>
    </header>

    <RouterView />

    <LoginDialog v-model="loginVisible" />
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: var(--canvas);
}

.app-bar {
  position: sticky;
  top: 0;
  z-index: 30;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid var(--accent-line);
  border-radius: var(--r-sm);
  background: var(--accent-soft);
  color: var(--accent);
  font: 600 11px/1 var(--font-mono);
  letter-spacing: 0.4px;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.brand-text strong {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink);
}

.brand-text small {
  color: var(--ink-4);
  font-size: 11px;
}

.app-nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 10px;
  border-radius: var(--r-sm);
  color: var(--ink-3);
  font-size: 12.5px;
  text-decoration: none;
  transition: background 0.15s ease, color 0.15s ease;
}

.nav-link:hover {
  background: var(--surface-2);
  color: var(--ink);
}

.nav-link.router-link-active {
  color: var(--ink);
  font-weight: 600;
}

.nav-avatar {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  margin-left: 6px;
  border-radius: 50%;
  background: linear-gradient(150deg, #414c58, #222a32);
  color: #ffffff;
  text-decoration: none;
  transition: box-shadow 0.15s ease;
}

.nav-avatar:hover {
  box-shadow: 0 0 0 3px rgba(65, 76, 88, 0.12);
}

.nav-user {
  margin-left: 6px;
  font-size: 12.5px;
  color: var(--ink-3);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.login-btn {
  height: 30px;
  margin-left: 6px;
  padding: 0 14px;
  border: 1px solid var(--accent-line);
  border-radius: var(--r-sm);
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;
}

.login-btn:hover {
  background: var(--surface-2);
}

.logout-btn {
  height: 26px;
  margin-left: 4px;
  padding: 0 8px;
  border: none;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-4);
  font-size: 12px;
  cursor: pointer;
}

.logout-btn:hover {
  background: var(--surface-2);
  color: var(--ink-3);
}
</style>
