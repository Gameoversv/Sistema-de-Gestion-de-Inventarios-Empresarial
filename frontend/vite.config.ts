import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/products': { target: 'http://localhost:8080', changeOrigin: true },
      '/categories': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      // `all` incluye los archivos que ningun test importa. Sin el, v8 solo mide
      // lo que los tests cargan y el informe daba 100 % sobre 14 sentencias de un
      // unico componente, ocultando que el resto del frontend no tiene pruebas.
      all: true,
      // json-summary alimenta el badge de cobertura del README; lcov queda para
      // SonarCloud cuando se conecte (Q-1).
      reporter: ['text-summary', 'html', 'json-summary', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'node_modules/',
        'src/test/',
        'src/**/*.d.ts',
        'src/main.tsx',
        'src/vite-env.d.ts',
      ],
    },
  },
})
