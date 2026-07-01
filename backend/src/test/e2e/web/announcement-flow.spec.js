const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
    console.log('Executing line 4: ' + "await page.goto('/login');");
  await page.goto('/login');
    console.log('Executing line 6: ' + "await page.fill('#username', username);");
  await page.fill('#username', username);
    console.log('Executing line 8: ' + "await page.fill('#password', password);");
  await page.fill('#password', password);
    console.log('Executing line 10: ' + "await page.click('button[type=\"submit\"]');");
  await page.click('button[type="submit"]');
    console.log('Executing line 12: ' + "await page.waitForLoadState('networkidle');");
  await page.waitForLoadState('networkidle');
    console.log('Executing line 14: ' + "await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });");
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });
}

test.describe('Multi-Role Announcement Workflow E2E Test', () => {

  test('TC-05/06/07/20: Publish announcement as admin and verify receipt on parent dashboard', async ({ page }) => {
    // 1. Reset database state to guarantee baseline roster
    console.log('Executing line 22: ' + "await page.goto('/test/reset');");
    await page.goto('/test/reset');

    // 2. Login as Admin
    console.log('Executing line 26: ' + "await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');");
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');

    // 3. Navigate to Admin Broadcast Center
    console.log('Executing line 30: ' + "await page.goto('/web/admin');");
    await page.goto('/web/admin');

    // 4. Fill out the announcement form
    const uniqueTitle = 'Monsoon Advisory: ' + Math.random().toString(36).substring(7);
    console.log('Executing line 35: ' + "await page.fill('#title', uniqueTitle);");
    await page.fill('#title', uniqueTitle);
    console.log('Executing line 37: ' + "await page.selectOption('#targetGrade', 'Grade 6');");
    await page.selectOption('#targetGrade', 'Grade 6');
    console.log('Executing line 39: ' + "await page.fill('#content', 'Heavy rains are expected. Greenwood High will remain closed tomorrow.');");
    await page.fill('#content', 'Heavy rains are expected. Greenwood High will remain closed tomorrow.');

    // 5. Submit form
    console.log('Executing line 43: ' + "await page.click('button:has-text(\"Broadcast Notice\")');");
    await page.click('button:has-text("Broadcast Notice")');
    console.log('Executing line 45: ' + "await page.waitForLoadState('networkidle');");
    await page.waitForLoadState('networkidle');

    // 6. Verify success redirect
    console.log('Executing line 49: ' + "await page.waitForURL(url => url.pathname.includes('/web/admin/dashboard'), { timeout: 90000 });");
    await page.waitForURL(url => url.pathname.includes('/web/admin/dashboard'), { timeout: 90000 });

    // 7. Logout to clear session
    console.log('Executing line 53: ' + "await page.context().clearCookies();");
    await page.context().clearCookies();

    // 8. Login as Parent (Ramesh)
    await page.context().clearCookies();

    // 8. Login as Parent (Ramesh)
    console.log('Executing line 57: ' + "await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');");
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');

    // 9. Navigate to Parent Dashboard
    console.log('Executing line 61: ' + "await page.goto('/web/parent/dashboard');");
    await page.goto('/web/parent/dashboard');
    console.log('Executing line 63: ' + "await page.locator('[data-tab=\"feed\"]').first().click();");
    await page.locator('[data-tab="feed"]').first().click();
    console.log('Executing line 65: ' + "await page.waitForLoadState('networkidle');");
    await page.waitForLoadState('networkidle');

    // 10. Assert announcement details are displayed
    const announcementTitle = page.locator(`h4:has-text("${uniqueTitle}")`).first();
    console.log('Executing line 70: ' + "await page.waitForLoadState('networkidle');");
    await page.waitForLoadState('networkidle');
    console.log('Executing line 72: ' + "await expect(announcementTitle).toBeVisible({ timeout: 90000 });");
    await expect(announcementTitle).toBeVisible({ timeout: 90000 });

    // The content is hidden by default in an accordion. Expand it first.
    console.log('Executing line 76: ' + "await announcementTitle.click();");
    await announcementTitle.click();

    const announcementContent = page.locator('p:has-text("Heavy rains are expected. Greenwood High will remain closed tomorrow.")').first();
    console.log('Executing line 80: ' + "await expect(announcementContent).toBeVisible();");
    await expect(announcementContent).toBeVisible();

    const announcementGrade = page.locator('span:has-text("Grade 6")').first();
    console.log('Executing line 84: ' + "await expect(announcementGrade).toBeVisible();");
    await expect(announcementGrade).toBeVisible();
  });

});
