import { reactive } from 'vue'

/**
 * 登录状态。
 *
 * token 存 localStorage：刷新页面后仍然是登录态。
 * 但真正的有效性由后端决定 —— 前端只是"拿着 token 试一下"，
 * 401 了就清掉并弹登录框。
 */
const TOKEN_KEY = 'oj.token'
const USER_KEY = 'oj.user'

export const auth = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: readUser(),
})

function readUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function isLoggedIn() {
  return !!auth.token
}

export function setAuth(token, user) {
  auth.token = token
  auth.user = user
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user || null))
}

export function clearAuth() {
  auth.token = ''
  auth.user = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
