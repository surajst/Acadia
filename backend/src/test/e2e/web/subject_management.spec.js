const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

test.describe('Sprint 1: Data-driven Subject Catalog', () => {

  test('Admin creates, renames, and deactivates a subject; active list reflects it', async ({ page }) => {
    const suffix = Date.now();
    const code = `TEST_SUBJECT_${suffix}`;
    const displayName = `Test Subject ${suffix}`;
    const renamedName = `Test Subject Renamed ${suffix}`;

    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/admin/subjects');

    // Create
    await page.fill('#newCode', code);
    await page.fill('#newDisplayName', displayName);
    await page.click('button:has-text("Add Subject")');
    const row = page.locator('tr', { hasText: code });
    await expect(row).toBeVisible({ timeout: 10000 });
    await expect(row).toContainText(displayName);
    await expect(row).toContainText('Active');

    // Confirm it shows up in the active-only API used by other pages
    let activeCodes = await page.evaluate(() => fetch('/api/subjects').then(r => r.json()).then(list => list.map(s => s.code)));
    expect(activeCodes).toContain(code);

    // Rename via the prompt() dialog
    page.once('dialog', dialog => dialog.accept(renamedName));
    await row.locator('button:has-text("Rename")').click();
    await expect(page.locator('tr', { hasText: code })).toContainText(renamedName, { timeout: 10000 });

    // Deactivate
    const renamedRow = page.locator('tr', { hasText: code });
    await renamedRow.locator('button:has-text("Deactivate")').click();
    await expect(renamedRow).toContainText('Inactive', { timeout: 10000 });

    // Inactive subject drops out of the active-only list but stays in the full admin list
    activeCodes = await page.evaluate(() => fetch('/api/subjects').then(r => r.json()).then(list => list.map(s => s.code)));
    expect(activeCodes).not.toContain(code);

    const allCodes = await page.evaluate(() => fetch('/api/admin/subjects').then(r => r.json()).then(list => list.map(s => s.code)));
    expect(allCodes).toContain(code);
  });

  test('Teacher task creation subject dropdown is populated from /api/subjects, not hardcoded', async ({ page }) => {
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/teacher/tasks');

    await page.waitForFunction(() => {
      const select = document.getElementById('subjectType');
      return select && select.options.length > 0 && select.options[0].value !== '';
    }, { timeout: 10000 });

    const apiCodes = await page.evaluate(() => fetch('/api/subjects').then(r => r.json()).then(list => list.map(s => s.code)));
    const optionValues = await page.locator('#subjectType option').evaluateAll(opts => opts.map(o => o.value));

    expect(optionValues.length).toBe(apiCodes.length);
    for (const code of apiCodes) {
      expect(optionValues).toContain(code);
    }
  });

});
