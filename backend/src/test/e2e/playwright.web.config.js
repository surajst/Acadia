const { defineConfig, devices } = require('@playwright/test');

// Seeded accounts sign in with DEMO_ADMIN_PASSWORD, which the backend under
// test must have been started with too. There is no default in source: the
// repository was public. Fail here, once, rather than as a login timeout in
// every test.
if (!process.env.DEMO_ADMIN_PASSWORD) {
  throw new Error('Set DEMO_ADMIN_PASSWORD to the password the backend was started with '
    + '(CI sets a throwaway value in .github/workflows/ci.yml).');
}

module.exports = defineConfig({
  testDir: './web',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: 'html',
  use: {
    // Overridable so the suite can be pointed at a server on another port —
    // e.g. when 8080 is already taken by a local dev instance.
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:8080',
    trace: 'on-first-retry',
    actionTimeout: 30000,
  },
  timeout: 60000,
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
