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
