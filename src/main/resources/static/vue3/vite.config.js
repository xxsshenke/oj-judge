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
      '/submissions': 'http://localhost:8080',
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
