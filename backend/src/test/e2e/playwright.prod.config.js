// Points the smoke run at a deployed environment. Separate from the local web
// config so a stray `npx playwright test` can never aim local specs -- several
// of which call /test/reset -- at production.
const { devices } = require('@playwright/test');

module.exports = {
  testDir: './prod',
  timeout: 90000,
  expect: { timeout: 20000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'line',
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL,
    trace: 'off',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
};
