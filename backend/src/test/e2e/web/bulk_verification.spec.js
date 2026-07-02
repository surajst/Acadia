const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

async function createHomeworkTask(page, title) {
  await page.goto('/web/teacher/tasks');
  await page.fill('#taskTitle', title);
  await page.waitForFunction(() => {
    const s = document.getElementById('subjectType');
    return s && s.options.length > 0 && s.options[0].value !== '';
  }, { timeout: 10000 });
  await page.selectOption('#taskType', 'HOMEWORK');
  await page.selectOption('#standard', '6');
  await page.fill('#xpReward', '30');
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  await page.fill('#dueDate', tomorrow.toISOString().split('T')[0]);
  await page.click('button:has-text("Assign Task")');
  await expect(page.locator('#toast-msg')).toHaveText('Task created successfully!', { timeout: 10000 });
}

async function submitTask(page, title) {
  await page.goto('/web/student/portal');
  await page.locator('[data-tab="challenges"]').first().click();
  const card = page.locator('#scholastic-tasks-container .bg-slate-900\\/40', { hasText: title });
  await expect(card).toBeVisible({ timeout: 15000 });
  await card.locator('button:has-text("Submit Completion")').click();
  await expect(page.locator('#taskSubmissionModal')).toBeVisible();
  await page.fill('#taskSubmissionModal textarea[name="proofOfWorkNotes"]', 'Done: ' + title);
  await page.click('#taskSubmissionModal button[type="submit"]');
  await page.waitForURL('**/web/student/portal?success=true', { timeout: 30000 });
}

test.describe('Sprint 5: Bulk verification queue', () => {
  test.setTimeout(180000);

  test('Teacher bulk-approves multiple milestone submissions with checkboxes', async ({ page }) => {
    await page.goto('/test/reset');
    const suffix = Date.now();
    const titleA = `Bulk Task A ${suffix}`;
    const titleB = `Bulk Task B ${suffix}`;

    // Teacher creates two tasks
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');
    await createHomeworkTask(page, titleA);
    await createHomeworkTask(page, titleB);

    // Student submits both
    await page.context().clearCookies();
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');
    await submitTask(page, titleA);
    await submitTask(page, titleB);

    // Teacher bulk-approves both from the dashboard
    await page.context().clearCookies();
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/teacher/dashboard');

    const rowA = page.locator('div[id^="task-row-"]', { hasText: titleA });
    const rowB = page.locator('div[id^="task-row-"]', { hasText: titleB });
    await expect(rowA).toBeVisible();
    await expect(rowB).toBeVisible();

    // Pending-count badge reflects the queue
    const badgeText = await page.locator('h2:has-text("Verification Queue") span').first().innerText();
    expect(parseInt(badgeText, 10)).toBeGreaterThanOrEqual(2);

    await rowA.locator('.milestone-checkbox').check();
    await rowB.locator('.milestone-checkbox').check();

    page.once('dialog', dialog => dialog.accept());
    await Promise.all([
      page.waitForURL('**/web/teacher/dashboard**', { timeout: 60000 }),
      page.click('button:has-text("Approve Selected")'),
    ]);
    await page.waitForLoadState('domcontentloaded');

    // Both rows are gone after the bulk approve + reload
    await expect(page.locator('div[id^="task-row-"]', { hasText: titleA })).toHaveCount(0);
    await expect(page.locator('div[id^="task-row-"]', { hasText: titleB })).toHaveCount(0);
  });

});
