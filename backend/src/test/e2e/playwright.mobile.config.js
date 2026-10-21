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
  testDir: './mobile',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:8081',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'Mobile Chrome',
      use: { ...devices['Pixel 5'] },
    },
  ],
});
