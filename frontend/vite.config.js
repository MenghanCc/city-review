import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  build: {
    // 打包输出到 Spring Boot static 目录
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true
  },
  server: {
    // 开发时代理 API 请求到后端 8081
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
