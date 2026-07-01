const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

test.describe('ACADIA Role Authentication & Isolation Specs', () => {

  test.beforeEach(async ({ page }) => {
    // Reset database state to guarantee baseline roster
    await page.goto('/test/reset');
  });

  test('TC-01/02/03: Successfully authenticate Admin, Parent, and Student roles', async ({ page }) => {
    // Admin login
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
    await expect(page.locator('body')).not.toContainText('Forbidden');
    await page.context().clearCookies();

    // Parent login
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');
    await expect(page.locator('body')).not.toContainText('Forbidden');
    await page.context().clearCookies();

    // Student login
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');
    await expect(page.locator('body')).not.toContainText('Forbidden');
  });

  test('TC-04: Enforce role isolation - Parent is forbidden from accessing Admin views', async ({ page }) => {
    // Login as Parent
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');

    // Attempt to access Admin Broadcast center
    const response = await page.goto('/web/admin');
    
    // Assert HTTP status is 403 Forbidden
    expect(response.status()).toBe(403);
  });

});
