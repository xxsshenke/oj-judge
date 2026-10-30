export const difficultyText = (difficulty) => ({
  1: 'easy',
  2: 'medium',
  3: 'hard',
}[difficulty] || 'basic')

export const starterCode = (problem) => [
  'import java.util.*;',
  '',
  'public class Main {',
  '    public static void main(String[] args) {',
  '        Scanner scanner = new Scanner(System.in);',
  '        // implement solution for ' + problem.title,
  '    }',
  '}',
].join(String.fromCharCode(10))

export const verdictText = (verdict) => ({
  AC: '通过',
  WA: '答案错误',
  TLE: '运行超时',
  MLE: '内存超限',
  RE: '运行错误',
  CE: '编译错误',
  SE: '系统错误',
  PE: '格式错误',
}[verdict] || verdict || '等待评测')

export const verdictTone = (verdict) => ({
  AC: 'ac',
  WA: 'bad',
  RE: 'bad',
  CE: 'bad',
  TLE: 'warn',
  MLE: 'warn',
  PE: 'warn',
}[verdict] || 'neutral')

export const formatTime = (value) => {
  if (!value) return 'just now'

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value

  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
