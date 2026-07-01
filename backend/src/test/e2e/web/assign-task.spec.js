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

    // 3. Navigate to Parent Dashboard
    await page.goto('/web/parent/portal');

    // 4. Fill out the Assign Task Form (direct on page)
    const uniqueTitle = 'Clean the Backyard: ' + Math.random().toString(36).substring(7);
    await page.fill('#taskDescription', uniqueTitle);
    await page.fill('#xpBounty', '250');
    
    // The studentId is hidden and dynamically set, no need to select from dropdown
    console.log(`Assigning task to default linked student`);

    // Submit the form
    await page.click('button:has-text("Assign Task")');

    // 6. Verify page reload / modal dismissal
    await page.waitForURL(/.*success=task_assigned.*/, { timeout: 90000 });
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
