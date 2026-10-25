const lineBreak = String.fromCharCode(10)

const demoProblems = [
  {
    id: 1,
    title: '两数之和',
    description: '读取两个整数，输出它们的和。每个整数占一行。',
    input: ['3', '5'].join(lineBreak),
    expected: '8',
    difficulty: 1,
    judgeMode: 'ACM',
  },
  {
    id: 2,
    title: '回文字符串',
    description: '判断给定字符串是否为回文。忽略大小写，若是输出 YES，否则输出 NO。',
    input: 'Level',
    expected: 'YES',
    difficulty: 2,
    judgeMode: 'ACM',
  },
  {
    id: 3,
    title: '区间求和',
    description: '给定一个整数数组和多个查询，输出每个闭区间内所有元素的和。',
    input: ['5', '1 2 3 4 5', '1 3'].join(lineBreak),
    expected: '9',
    difficulty: 2,
    judgeMode: 'OI',
  },
]

const demoSubmissions = new Map()

async function request(path, options) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })

  if (!response.ok) throw new Error('API request failed: ' + response.status)

  const body = await response.json()
  if (body.code === 0) throw new Error(body.msg || '请求失败')
  return body.data
}

export async function listProblems() {
  try {
    return { data: await request('/problems'), connected: true }
  } catch {
    return { data: demoProblems, connected: false }
  }
}

export async function listSubmissions(problemId) {
  try {
    return await request('/submissions?problemId=' + problemId) || []
  } catch {
    return demoSubmissions.get(problemId) || []
  }
}

export async function submitSolution(problemId, code) {
  try {
    return { data: await request('/submissions', { method: 'POST', body: JSON.stringify({ problemId, code }) }), connected: true }
  } catch {
    const submission = { id: Date.now(), problemId, status: 'PENDING', verdict: null, score: null, timeCost: null, createTime: new Date().toISOString() }
    demoSubmissions.set(problemId, [submission, ...(demoSubmissions.get(problemId) || [])])
    window.setTimeout(() => Object.assign(submission, { status: 'DONE', verdict: 'AC', score: 100, timeCost: 12 }), 1600)
    return { data: submission, connected: false }
  }
}
