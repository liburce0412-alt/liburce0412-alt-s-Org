import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  outputDir: '../../design/visual-tests/admin-artifacts',
  use: { baseURL: 'http://127.0.0.1:4173', colorScheme: 'light' },
  webServer: { command: 'npm run dev -- --host 127.0.0.1', url: 'http://127.0.0.1:4173', reuseExistingServer: true },
})
