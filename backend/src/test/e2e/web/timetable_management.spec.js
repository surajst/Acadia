const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

test.describe('Sprint 5: Timetable management', () => {

  test('Admin adds and deletes a timetable period via the UI', async ({ page }) => {
    const suffix = Date.now();
    const room = `Room-${suffix}`;

    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/admin/timetable');

    // Dropdowns populate (class sections, teachers, subjects)
    await page.waitForFunction(() => {
      const cs = document.getElementById('classSectionId');
      const t = document.getElementById('teacherId');
      const s = document.getElementById('subjectName');
      return cs && cs.options.length > 0 && t && t.options.length > 0 && t.value !== ''
          && s && s.options.length > 0;
    }, { timeout: 10000 });

    // Add a period with a unique room number
    await page.selectOption('#dayOfWeek', 'FRI');
    await page.fill('#periodNumber', '8');
    await page.fill('#startTime', '15:00');
    await page.fill('#endTime', '15:45');
    await page.fill('#roomNumber', room);
    await page.click('#addPeriodForm button[type="submit"]');

    const row = page.locator('#timetableRows tr', { hasText: room });
    await expect(row).toBeVisible({ timeout: 10000 });
    await expect(row).toContainText('FRI');
    await expect(row).toContainText('15:00–15:45');

    // Delete it again
    page.once('dialog', dialog => dialog.accept());
    await row.locator('button:has-text("Delete")').click();
    await expect(page.locator('#timetableRows tr', { hasText: room })).toHaveCount(0, { timeout: 10000 });
  });

  test('Teacher is blocked from timetable management; TEACHER dashboard shows Today\'s Schedule widget', async ({ page }) => {
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

    // Access boundary: page and API both 403
    const pageResp = await page.goto('/web/admin/timetable');
    expect(pageResp.status()).toBe(403);
    const apiResp = await page.request.get('/api/admin/timetable');
    expect(apiResp.status()).toBe(403);

    // Today's Schedule widget renders on the teacher dashboard (with periods or the empty message)
    await page.goto('/web/teacher/dashboard');
    await expect(page.locator('#todaySchedulePanel')).toBeVisible();
    await page.waitForFunction(() => {
      const rows = document.getElementById('todayScheduleRows');
      return rows && !rows.textContent.includes('Loading schedule');
    }, { timeout: 10000 });
    const content = await page.locator('#todayScheduleRows').innerText();
    expect(content.length).toBeGreaterThan(0);
  });

});
