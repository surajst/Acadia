const { test, expect } = require('@playwright/test');
const path = require('path');

test('Capture Screenshots', async ({ page }) => {
    // 1. Login
    await page.goto('/login');
    await page.fill('#username', 'teacher@greenwood.com');
    await page.fill('#password', 'PilotLaunchSecure2026!');
    await page.click('button[type="submit"]');
    await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });

    // 2. Dashboard Screenshot
    await page.goto('/web/teacher/dashboard');
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('h2:has-text("Syllabus Verification Queue")')).toBeVisible({ timeout: 30000 });
    await page.screenshot({ path: path.join(__dirname, 'teacher_dashboard.png'), fullPage: false });

    // 3. Tasks Page Screenshot
    await page.goto('/web/teacher/tasks');
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('#studentNameSearch')).toBeVisible({ timeout: 30000 });
    await page.screenshot({ path: path.join(__dirname, 'teacher_tasks.png'), fullPage: false });

    // 4. Autocomplete Dropdown Screenshot
    await page.click('input[name="assignedToClass"][value="false"]');
    await page.fill('#studentNameSearch', 'arj');
    await page.waitForSelector('li[data-id]'); // Wait for dropdown results
    await page.screenshot({ path: path.join(__dirname, 'teacher_tasks_autocomplete.png'), fullPage: false });
});
