import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) =>
{
  // 高德 JS API Key 只从本地环境读取，不写入仓库。
  // 优先 web/.env.local 的 VITE_AMAP_JS_API_KEY，其次兼容进程环境变量 AMAP_JS_API_KEY。
  const rootDir = fileURLToPath(new URL('.', import.meta.url))
  const env = loadEnv(mode, rootDir, ['VITE_', 'AMAP_JS_API_KEY'])
  const amapJsApiKey = (env.VITE_AMAP_JS_API_KEY || process.env.AMAP_JS_API_KEY || env.AMAP_JS_API_KEY || '').trim()

  return {
    envDir: rootDir,
    plugins: [vue()],
    define: {
      'import.meta.env.VITE_AMAP_JS_API_KEY': JSON.stringify(amapJsApiKey)
    },
    resolve: {
      // 只匹配 "@/..."，避免把 "@fontsource/..." 等 scoped 包当成别名。
      alias: [
        {
          find: /^@\//,
          replacement: fileURLToPath(new URL('./src/', import.meta.url))
        }
      ]
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
  }
})
