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
    
    // Assert the profile actually rendered rather than an error page.
    //
    // Deliberately not a substring search for "403" over the whole body: the
    // class dropdown lists every section in the school, and another spec names
    // one Audit-Grade-<epoch millis>. When those digits happened to contain
    // 403, this test failed for no reason at all. A status code is checked as
    // a status code.
    const response = await page.reload();
    expect(response?.status(), 'admin may open a student profile').toBe(200);

    await expect(page.locator('body')).not.toContainText('Whitelabel Error Page');
    await expect(page.locator('h3:has-text("Attendance")')).toBeVisible();
    await expect(page.locator('h3:has-text("Guardian & Home")')).toBeVisible();
  });

});
