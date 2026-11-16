import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 5173,
    proxy: {
      // 后端默认跑在 18080，避免与宿主注入的 SERVER_PORT 冲突
      '/api': { target: 'http://127.0.0.1:18080', changeOrigin: true }
    }
  },
  build: {
    // 直接产出到 Spring Boot 的静态目录，mvn package 时一并打进 jar
    outDir: '../src/main/resources/static',
    emptyOutDir: true
  }
})
