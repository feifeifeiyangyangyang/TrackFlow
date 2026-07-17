import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({ plugins: [vue()], server: { host: '127.0.0.1', port: 8001, proxy: { '/api': 'http://127.0.0.1:8002' } }, test: { environment: 'jsdom' } })
