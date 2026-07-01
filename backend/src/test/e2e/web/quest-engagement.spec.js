const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForLoadState('domcontentloaded');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });
}

test.describe('ACADIA Parent-Kid Quest Engagement Flow', () => {

  test.beforeEach(async ({ page }) => {
    // Reset database state to guarantee baseline roster
    await page.goto('/test/reset');
  });

  test('TC-08/09/10/11/12/13/14: Assign task, claim quest, and approve quest loop', async ({ page }) => {
    // Handle dialog alerts for parent task assignment
    page.on('dialog', async dialog => {
      expect(dialog.message()).toContain('Quest successfully assigned!');
      await dialog.accept();
    });

    // 1. Login as Parent (Ramesh)
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');

    // 2. Navigate to Parent Dashboard
    await page.goto('/web/parent/portal');

    // 3. Verify Assign Task Form is visible
    await expect(page.locator('form[action="/web/parent/assign-task"]')).toBeVisible();

    // 5. Fill out the Assign Task Form directly on page
    const uniqueTitle = 'Backyard Raking Task: ' + Math.random().toString(36).substring(7);
    await page.fill('#taskDescription', uniqueTitle);
    await page.fill('#xpBounty', '200');

    // 6. Submit the modal form
    await page.click('button:has-text("Assign Task")');
    await page.waitForTimeout(1000);
    await page.waitForLoadState('domcontentloaded');

    // 8. Logout
    await page.context().clearCookies();

    // 9. Login as Student (Arjun)
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');

    // 10. Navigate to Student Portal
    await page.goto('/web/student/portal');
    await page.locator('[data-tab="challenges"]').first().click();
    // Wait for the scholastic tasks container to finish loading (JS fetch)
    await page.waitForFunction(() => {
      const c = document.getElementById('scholastic-tasks-container');
      return c && !c.innerHTML.includes('Loading tasks...');
    }, { timeout: 30000 });

    // 11. Assert task is visible and click Mark Done
    const questCard = page.locator(`div:has-text("${uniqueTitle}")`).first();
    await expect(questCard).toBeVisible();

    const markDoneBtn = page.locator(`a[href*="/web/student/quest/"][href$="/claim"]`).first();
    await expect(markDoneBtn).toBeVisible();
    await markDoneBtn.click();

    // 12. Verify status updates to Awaiting Approval
    await page.waitForLoadState('domcontentloaded');
    await page.waitForURL(url => url.pathname.includes('/web/student/portal'), { timeout: 90000 });
    await page.locator('[data-tab="challenges"]').first().click();
    await page.waitForFunction(() => {
      const c = document.getElementById('scholastic-tasks-container');
      return c && !c.innerHTML.includes('Loading tasks...');
    }, { timeout: 30000 });
    await expect(page.locator('text=Awaiting Parent Approval').first()).toBeVisible({ timeout: 90000 });

    // 13. Logout
    await page.context().clearCookies();

    // 14. Login as Parent (Ramesh)
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');

    // 15. Navigate to Parent Dashboard
    await page.goto('/web/parent/portal');

    // 16. Approve the quest in the Approval Queue
    await page.waitForLoadState('domcontentloaded');
    const approveBtn = page.locator('a:has-text("✓ Approve & Grant XP")').first();
    await expect(approveBtn).toBeVisible({ timeout: 90000 });
    await approveBtn.click();

    // 17. Verify status changes to Verified / Approved (via toast or navigation)
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('#toast')).toBeVisible();
  });

});
