const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

test.describe('Parent-Kid Engagement Flow: Assign Task', () => {

  test('Parent assigns task to child and child sees it in student portal', async ({ page }) => {
    // 1. Reset database state
    await page.goto('/test/reset');

    // 2. Login as Parent
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');

    // 3. Navigate to Parent Dashboard (the /portal route now redirects here)
    await page.goto('/web/parent/dashboard');

    // 4. The Assign Home Task form lives in the hidden Profile tab panel.
    await page.locator('[data-tab="profile"]').first().click();
    const uniqueTitle = 'Clean the Backyard: ' + Math.random().toString(36).substring(7);
    // Child selection is now required.
    const taskChild = page.locator('#profile-assignStudentId option', { hasText: 'Arjun' }).first();
    await page.selectOption('#profile-assignStudentId', await taskChild.getAttribute('value'));
    await page.fill('#profile-taskTitle', uniqueTitle);
    await page.fill('#profile-xpBounty', '250');

    // Submit the form
    await page.locator('#assignTaskForm-profile button[type="submit"]').click();

    // 6. Verify page reload back to the dashboard
    await page.waitForURL(url => url.pathname.includes('/web/parent/dashboard'), { timeout: 90000 });
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(500);

    // 7. Logout
    await page.context().clearCookies();

    // 8. Log in as matching student
    const studentUsername = 'arjun@gmail.com';
    await login(page, studentUsername, 'PilotLaunchSecure2026!');

    // 9. Navigate to student portal dashboard tab
    await page.goto('/web/student/portal');
    await page.locator('[data-tab="challenges"]').first().click();
    // Wait for the scholastic tasks container to finish loading (JS fetch)
    await page.waitForFunction(() => {
      const c = document.getElementById('scholastic-tasks-container');
      return c && !c.innerHTML.includes('Loading tasks...');
    }, { timeout: 30000 });

    // 10. Assert that the assigned task is visible
    const taskTitleElement = page.locator(`h4:has-text("${uniqueTitle}")`).first();
    await expect(taskTitleElement).toBeVisible();
  });

});
