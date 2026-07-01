import { test, expect } from '@playwright/test';

async function login(page: any, username: string, password: string) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL((url: URL) => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

test.describe('ACADIA Role Clearance Specs', () => {

  test('Test: Administrator can view Student Profile successfully', async ({ page }) => {
    // 1. Reset database state to guarantee baseline roster
    await page.goto('/test/reset');

    // 2. Login as Administrator
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');

    // 3. Navigate to the Unified Dashboard
    await page.goto('/web/admin/dashboard');

    // 4. Click the "View Profile Data" button for the first student
    const viewProfileBtn = page.locator('a:has-text("View Profile Data")').first();
    await expect(viewProfileBtn).toBeVisible();
    await viewProfileBtn.click();

    // 5. Verify the URL is `/web/teacher/student/{id}` and we are not met with 403 Forbidden
    await page.waitForURL((url: URL) => url.pathname.includes('/web/teacher/student/'));
    
    // Assert page content (e.g., student name header or cards are visible, and NO Whitelabel Error Page)
    await expect(page.locator('body')).not.toContainText('Forbidden');
    await expect(page.locator('body')).not.toContainText('403');
    await expect(page.locator('h3:has-text("Individual Attendance")')).toBeVisible();
    await expect(page.locator('h3:has-text("Parent Trust & Home Integration")')).toBeVisible();
  });

});
