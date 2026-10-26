async function request(path, options) {
  let response
  try {
    response = await fetch(path, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    })
  } catch {
    throw new Error('无法连接服务器，请检查后端服务。')
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

export async function listProblems() {
  return request('/problems')
}

export async function listSubmissions(problemId) {
  return await request('/submissions?problemId=' + problemId) || []
}

export async function submitSolution(problemId, code) {
  return request('/submissions', { method: 'POST', body: JSON.stringify({ problemId, code }) })
}
