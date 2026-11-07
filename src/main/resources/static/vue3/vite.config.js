import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
  ],
  base: '/',
  server: {
    proxy: {
      '/problems': 'http://localhost:8080',
      '/problem-sets': 'http://localhost:8080',
      '/submissions': 'http://localhost:8080',
      '/users': 'http://localhost:8080',
      '/login': 'http://localhost:8080',
      '/logout': 'http://localhost:8080',
      // 题解评论区：/solutions（详情/改/删/赞）和 /comments（删/赞）
      // ⚠️ 漏了这两条，dev 模式下这些请求会 404 —— 因为 Vite 不会自动代理
      '/solutions': 'http://localhost:8080',
      '/comments': 'http://localhost:8080',
      // 手机号验证码：/auth/sms/code
      '/auth': 'http://localhost:8080',
    },
  },
  build: {
    outDir: '../',
    emptyOutDir: false,
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
