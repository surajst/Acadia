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
    // 1. Login as Parent (Ramesh)
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');

    // 2. Navigate to the Parent Dashboard (the /portal route now redirects here)
    await page.goto('/web/parent/dashboard');

    // 3. The "Assign Home Task" form lives in the hidden Profile tab panel.
    //    Click the Profile tab to reveal it before interacting.
    await page.locator('[data-tab="profile"]').first().click();
    await expect(page.locator('form[action="/web/parent/assign-task"]')).toBeVisible();

    // 5. Fill out the Assign Task Form (child selection is now required)
    const uniqueTitle = 'Backyard Raking Task: ' + Math.random().toString(36).substring(7);
    const taskChild = page.locator('#profile-assignStudentId option', { hasText: 'Arjun' }).first();
    await page.selectOption('#profile-assignStudentId', await taskChild.getAttribute('value'));
    await page.fill('#profile-taskTitle', uniqueTitle);
    await page.fill('#profile-xpBounty', '200');

    // 6. Submit the form and verify the success toast
    await page.locator('#assignTaskForm-profile button[type="submit"]').click();
    await page.waitForLoadState('domcontentloaded');
    await page.waitForURL(url => url.pathname.includes('/web/parent/dashboard'), { timeout: 90000 });
    await expect(page.locator('#toast')).toContainText('Home task assigned successfully!');

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

    // 11. Locate OUR specific parent-assigned quest card on the Home Quest Board
    //     (avoid claiming an unrelated seeded task via a bare .first()) and Mark Done.
    //     The claim control is now a POST form button (reskin).
    const questCard = page.locator('div.rounded-2xl', { hasText: uniqueTitle })
      .filter({ has: page.locator('button:has-text("Mark Done")') });
    await expect(questCard).toBeVisible();
    await questCard.locator('button:has-text("Mark Done")').click();

    // 12. Verify OUR quest now shows the Awaiting Approval status.
    await page.waitForLoadState('domcontentloaded');
    await page.waitForURL(url => url.pathname.includes('/web/student/portal'), { timeout: 90000 });
    await page.locator('[data-tab="challenges"]').first().click();
    const awaitingCard = page.locator('div.rounded-2xl', { hasText: uniqueTitle });
    await expect(awaitingCard.getByText('AWAITING APPROVAL')).toBeVisible({ timeout: 90000 });

    // 13. Logout
    await page.context().clearCookies();

    // 14. Login as Parent (Ramesh)
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');

    // 15. Navigate to the Parent Dashboard
    await page.goto('/web/parent/dashboard');
    await page.waitForLoadState('domcontentloaded');

    // 16. The Approval Queue lives in the hidden Quests tab panel.
    //     Click the Quests tab to reveal it, then approve the awaiting quest.
    await page.locator('[data-tab="quests"]').first().click();
    const questsPanel = page.locator('#tab-quests');
    // Our claimed quest must be sitting in the parent's Approval Queue.
    await expect(questsPanel.getByText(uniqueTitle).first()).toBeVisible({ timeout: 90000 });

    // Approval is an AJAX POST; wait for it to complete successfully.
    const approveBtn = page.locator('.approve-quest-btn').first();
    await expect(approveBtn).toBeVisible();
    const [approveResp] = await Promise.all([
      page.waitForResponse(r => r.url().includes('/api/parent/approve-quest/') && r.request().method() === 'POST'),
      approveBtn.click(),
    ]);
    expect(approveResp.ok()).toBeTruthy();

    // 17. Verify the quest left the Approval Queue (status is now APPROVED).
    await page.goto('/web/parent/dashboard');
    await page.waitForLoadState('domcontentloaded');
    await page.locator('[data-tab="quests"]').first().click();
    await expect(page.locator('#tab-quests').getByText(uniqueTitle)).toHaveCount(0);
  });

});
