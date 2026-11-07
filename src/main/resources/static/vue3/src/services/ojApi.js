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

/**
 * 提交记录分页查询。
 *
 * 后端返回的是分页对象，不是数组：
 *   { records, total, pageNum, pageSize, pages, hasNext, hasPre }
 * 这里做一层兜底，保证后端异常返回时前端拿到的是结构完整、可直接渲染的对象，
 * 而不是 undefined 报错。
 */
export async function listSubmissionPage(problemId, { pageNum = 1, pageSize = 20 } = {}) {
  const params = new URLSearchParams({
    problemId: String(problemId),
    pageNum: String(pageNum),
    pageSize: String(pageSize),
  })
  const page = await request('/submissions?' + params.toString())
  return (
    page || {
      records: [],
      total: 0,
      pageNum,
      pageSize,
      pages: 0,
      hasNext: false,
      hasPre: false,
    }
  )
}

/**
 * 兼容旧调用：题目页只关心"最新一批提交"，用于结果卡片和判题轮询。
 * 内部只取第一页，让调用方继续按数组使用。
 */
export async function listSubmissions(problemId) {
  const page = await listSubmissionPage(problemId, { pageNum: 1, pageSize: 20 })
  return page.records || []
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

/* ---------- 题解 ---------- */

const EMPTY_PAGE = (pageNum, pageSize) => ({
  records: [],
  total: 0,
  pageNum,
  pageSize,
  pages: 0,
  hasNext: false,
  hasPre: false,
})

/** 某题下的题解列表。sort: 'new' | 'hot' */
export async function listSolutionPage(problemId, { pageNum = 1, pageSize = 10, sort = 'new' } = {}) {
  const params = new URLSearchParams({
    pageNum: String(pageNum),
    pageSize: String(pageSize),
    sort,
  })
  const page = await request('/problems/' + problemId + '/solutions?' + params.toString())
  return page || EMPTY_PAGE(pageNum, pageSize)
}

export async function getSolution(id) {
  return request('/solutions/' + id)
}

export async function createSolution(problemId, title, content) {
  return request('/problems/' + problemId + '/solutions', {
    method: 'POST',
    body: JSON.stringify({ title, content }),
  })
}

export async function updateSolution(id, title, content) {
  return request('/solutions/' + id, {
    method: 'PUT',
    body: JSON.stringify({ title, content }),
  })
}

export async function deleteSolution(id) {
  return request('/solutions/' + id, { method: 'DELETE' })
}

/* ---------- 评论 ---------- */

export async function listCommentPage(solutionId, { pageNum = 1, pageSize = 10 } = {}) {
  const params = new URLSearchParams({
    pageNum: String(pageNum),
    pageSize: String(pageSize),
  })
  const page = await request('/solutions/' + solutionId + '/comments?' + params.toString())
  return page || EMPTY_PAGE(pageNum, pageSize)
}

/** parentId 为 null 时发主评论，否则回复那条评论 */
export async function createComment(solutionId, content, parentId = null) {
  return request('/solutions/' + solutionId + '/comments', {
    method: 'POST',
    body: JSON.stringify({ content, parentId }),
  })
}

export async function deleteComment(id) {
  return request('/comments/' + id, { method: 'DELETE' })
}

/* ---------- 点赞 ---------- */

/**
 * 点赞 / 取消。
 *
 * 刻意用 POST（赞）和 DELETE（取消）两个方法，而不是一个 POST 翻转：
 * toggle 在并发下会翻来翻去 —— 用户手抖点两次、或请求超时重试一次，
 * 状态就反了。这两个方法各自幂等，重试安全。
 */
export async function likeSolution(id, liked) {
  return request('/solutions/' + id + '/like', { method: liked ? 'POST' : 'DELETE' })
}

export async function likeComment(id, liked) {
  return request('/comments/' + id + '/like', { method: liked ? 'POST' : 'DELETE' })
}

/* ---------- 登录 ---------- */

export async function login(username, password) {
  return request('/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

/**
 * 获取验证码。
 *
 * 当前后端是内部实现，响应里带 smsCode 明文，由前端弹窗显示。
 * 接真实短信通道后会把这个字段删掉，这里的返回值就只剩 expireSeconds。
 */
export async function sendSmsCode(phone) {
  return request('/auth/sms/code', {
    method: 'POST',
    body: JSON.stringify({ phone }),
  })
}

/**
 * 手机号 + 验证码登录。手机号没注册时后端会自动建号。
 *
 * 所以前端不需要区分"注册"和"登录"两套流程 —— 一个框就够了。
 * nickname 只在首次建号时生效，已注册用户传了也不会覆盖。
 */
export async function loginBySms(phone, code, nickname) {
  return request('/login/sms', {
    method: 'POST',
    body: JSON.stringify({ phone, code, nickname: nickname || null }),
  })
}

export async function logout() {
  try {
    await request('/logout', { method: 'POST' })
  } catch {
    // 即使后端退出失败，前端也要清状态，否则用户会卡在退不出去
  }
}
