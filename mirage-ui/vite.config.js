import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:9080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 2000,
    rollupOptions: {
      output: {
        // 拆分 vendor：element-plus / icons / vue 全家桶 / pinyin 各自独立长缓存 chunk，
        // 首屏(工作台)不再背全量，业务改动也不让用户重下 vendor。
        manualChunks: {
          'vendor-vue': ['vue', 'vue-router', 'pinia'],
          'vendor-element': ['element-plus'],
          'vendor-icons': ['@element-plus/icons-vue'],
          'vendor-pinyin': ['pinyin-pro']
        }
      }
    }
  }
})
