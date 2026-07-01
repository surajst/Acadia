const { test, expect } = require('@playwright/test');

test.describe('Mobile Screenshots', () => {
  test.use({ viewport: { width: 390, height: 844 }, baseURL: 'http://localhost:8080' });

  test('Capture Mobile Screenshots', async ({ page }) => {
    // Login as Parent
    await page.goto('/login');
    await page.fill('#username', 'ramesh@gmail.com');
    await page.fill('#password', 'PilotLaunchSecure2026!');
    await page.click('button[type="submit"]');
    await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });

    await page.goto('/web/parent/portal');
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: 'mobile-parent-dashboard.png', fullPage: true });

    await page.context().clearCookies();

    // Login as Student
    await page.goto('/login');
    await page.fill('#username', 'arjun@gmail.com');
    await page.fill('#password', 'PilotLaunchSecure2026!');
    await page.click('button[type="submit"]');
    await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });

    await page.goto('/web/student/portal');
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: 'mobile-student-dashboard.png', fullPage: true });
  });
});
