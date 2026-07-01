const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });
}

test.describe('Mobile Responsiveness Specs', () => {
  test.use({ viewport: { width: 390, height: 844 }, baseURL: 'http://localhost:8080' });

  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
  });

  test('Assert mobile responsiveness on Parent Dashboard', async ({ page }) => {
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/parent/portal');
    
    // Assert no horizontal overflow
    const bodyWidth = await page.evaluate(() => document.body.scrollWidth);
    const windowWidth = await page.evaluate(() => window.innerWidth);
    expect(bodyWidth).toBeLessThanOrEqual(windowWidth);

    // Assert key elements visible (first name only on mobile)
    await expect(page.locator('text=Assign Task').first()).toBeVisible();
  });

  test('Assert mobile responsiveness on Student Portal', async ({ page }) => {
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/student/portal');
    await page.locator('#mobile-nav-challenges').click();
    await page.waitForLoadState('networkidle');

    // Assert no horizontal overflow
    const bodyWidth = await page.evaluate(() => document.body.scrollWidth);
    const windowWidth = await page.evaluate(() => window.innerWidth);
    expect(bodyWidth).toBeLessThanOrEqual(windowWidth);

    // Assert key elements visible
    await expect(page.locator('text=Scholastic Challenges').first()).toBeVisible();
  });

});
