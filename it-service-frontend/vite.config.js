import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  esbuild: {
    jsx: 'automatic',
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('recharts')) return 'recharts';
        },
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    css: false,
    setupFiles: ['./src/test/setup.js'],
  },
  server: {
    port: 3000,
    proxy: {
      '/api/v1/ws': {
        target: 'ws://localhost:8081',
        ws: true,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/v1\/ws/, '/ws'),
      },
      '/api/v1': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
