import { auth, clearAuth } from './auth'

/**
 * 401 的统一处理。
 *
 * 后端拦截器返回 401 表示「没登录或登录过期」。
 * 这里清掉前端登录态，并通知外层弹出登录框。
 * 用回调而不是直接 import 弹窗组件，避免循环依赖。
 */
let onUnauthorized = null

export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

function handleUnauthorized() {
  clearAuth()
  if (onUnauthorized) onUnauthorized()
}

async function request(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }

  // 已登录就自动带上 token
  if (auth.token) {
    headers.Authorization = 'Bearer ' + auth.token
  }

  let response
  try {
    response = await fetch(path, { ...options, headers })
  } catch {
    throw new Error('无法连接服务器，请检查后端服务。')
  }

  // 401 单独处理：清登录态 + 触发登录弹窗
  if (response.status === 401) {
    handleUnauthorized()
    const error = new Error('请先登录')
    error.unauthorized = true
    throw error
  }

  if (!response.ok) {
    if (response.status >= 500) throw new Error('无法连接服务器，请检查后端服务。')
    throw new Error('服务器请求失败（' + response.status + '）')
  }

  let body
  try {
    body = await response.json()
  } catch {
    throw new Error('服务器返回了无效响应。')
  }
  if (body.code === 0) throw new Error(body.msg || '请求失败')
  return body.data
}

/* ---------- 题目 ---------- */

export async function listProblems() {
  return request('/problems')
}

export async function getProblem(id) {
  return request('/problems/' + id)
}

export async function listSubmissions(problemId) {
  return await request('/submissions?problemId=' + problemId) || []
}

export async function submitSolution(problemId, code) {
  return request('/submissions', { method: 'POST', body: JSON.stringify({ problemId, code }) })
}

export async function getProblemSetOverview() {
  return request('/problem-sets/overview')
}

export async function listProblemSetProblems({ keyword, difficulty, tag } = {}) {
  const params = new URLSearchParams()
  if (keyword) params.set('keyword', keyword)
  if (difficulty) params.set('difficulty', difficulty)
  if (tag) params.set('tag', tag)
  const query = params.toString()
  return (await request('/problem-sets/problems' + (query ? '?' + query : ''))) || []
}

/* ---------- 用户 ---------- */

export async function getCurrentUser() {
  return request('/users/current')
}

/* ---------- 登录 ---------- */

export async function login(username, password) {
  return request('/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export async function logout() {
  try {
    await request('/logout', { method: 'POST' })
  } catch {
    // 即使后端退出失败，前端也要清状态，否则用户会卡在退不出去
  }
}
