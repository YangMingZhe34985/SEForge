import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, fileURLToPath(new URL('.', import.meta.url)), '')
  const proxy = env.SEFORGE_API_PROXY
    ? {
        '/api/v1': {
          target: env.SEFORGE_API_PROXY,
          changeOrigin: true,
        },
      }
    : undefined

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: { proxy },
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            'vendor-vue': ['vue', 'vue-router', 'pinia'],
            'vendor-element-plus': ['element-plus', '@element-plus/icons-vue'],
            'vendor-http': ['axios'],
          },
        },
      },
    },
    test: {
      include: ['src/**/*.spec.ts'],
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: true,
    },
  }
})
