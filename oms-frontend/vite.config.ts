import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 3000,
    // 允许通过 ngrok 等隧道域名访问（外网穿透时 Host 为 xxx.ngrok-free.app）
    allowedHosts: true,
    // 外网用 ngrok 只暴露 3000，/api 与 /uploads 经此代理到本机 8080，合同等文件链接走后端
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
