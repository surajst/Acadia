const { test, expect } = require('@playwright/test');

/**
 * The app shell and the things it replaced.
 *
 * These assert behaviour a person would notice, not markup: that the rail is
 * present and knows where you are, that a teacher is offered their own
 * navigation rather than an admin's, that the density toggle changes the
 * roster, and that recognising a child records what they did.
 *
 * The two role-gating tests are the ones worth keeping when this file is
 * eventually trimmed: the rail lost whole groups once already, because it read
 * a model attribute that only some controllers set.
 */

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'),
    { timeout: 90000 });
}

test.describe.configure({ mode: 'serial' });

test.describe('App shell', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
  });

  test('every converted page carries the rail', async ({ page }) => {
    for (const path of ['/web/admin/dashboard', '/web/admin/fees', '/web/admin/audit-log']) {
      await page.goto(path);
      await expect(page.locator('.shell-side')).toBeVisible();
      await expect(page.locator('.shell-topbar h1')).not.toBeEmpty();
      await expect(page.locator('text=Something went wrong')).not.toBeVisible();
    }
  });

  test('the rail marks the page you are on', async ({ page }) => {
    await page.goto('/web/admin/audit-log');
    await expect(page.locator('.navlink.active')).toHaveText(/Audit Log/);

    await page.goto('/web/admin/fees');
    await expect(page.locator('.navlink.active')).toHaveText(/Fees/);
  });

  test('a sub-page still lights up its section', async ({ page }) => {
    // Prefix matching, not equality: without it the rail claims you are
    // nowhere the moment you open a report.
    await page.goto('/web/admin/fees/collections');
    await expect(page.locator('.navlink.active')).toHaveText(/Fees/);
  });

  test('an admin is offered governance, and the logout form comes with it', async ({ page }) => {
    await page.goto('/web/admin/dashboard');
    await expect(page.locator('.shell-grouplabel', { hasText: 'Governance' })).toBeVisible();
    await expect(page.locator('.shell-user form[action*="logout"] button')).toBeVisible();
  });

  test('the density toggle changes the roster', async ({ page }) => {
    await page.goto('/web/admin/dashboard');
    const card = page.locator('#rosterCard');

    await expect(card).not.toHaveClass(/dense/);
    await page.click('.segmented button[data-density="compact"]');
    await expect(card).toHaveClass(/dense/);

    // It is a reading preference, so it survives a reload.
    await page.reload();
    await expect(page.locator('#rosterCard')).toHaveClass(/dense/);

    await page.click('.segmented button[data-density="comfortable"]');
    await expect(page.locator('#rosterCard')).not.toHaveClass(/dense/);
  });
});

test.describe('Teacher navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');
  });

  test('a teacher gets their own classroom nav, not an admin\'s', async ({ page }) => {
    await page.goto('/web/teacher/dashboard');
    await expect(page.locator('.shell-grouplabel', { hasText: 'My classroom' })).toBeVisible();
    await expect(page.locator('.shell-grouplabel', { hasText: 'Governance' })).toHaveCount(0);
    await expect(page.locator('.navlink', { hasText: 'Daily Attendance' })).toBeVisible();
  });
});

test.describe('Recognition', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
  });

  test('awarding a child records what they did and moves their XP', async ({ page }) => {
    await page.goto('/web/admin/dashboard');
    await page.locator('.roster-row a:has-text("Open")').first().click();
    await page.waitForURL(/\/web\/teacher\/student\//);

    const before = Number((await page.locator('[data-recognition] .badge--brand').innerText()).replace(/\D/g, ''));

    await page.locator('[data-award-form] [data-badge-option] input[value="KIND_HANDS"]').check();
    await page.fill('[data-award-form] input[name="reason"]', 'Helped tidy the paint pots');
    await page.click('[data-award-form] button[type="submit"]');
    await page.waitForURL(/\/web\/teacher\/student\//);

    // The reason is the point of the feature, so assert the words, not a count.
    await expect(page.locator('[data-award-row]').first()).toContainText('Helped tidy the paint pots');
    await expect(page.locator('[data-award-row]').first()).toContainText('Kind Hands');

    const after = Number((await page.locator('[data-recognition] .badge--brand').innerText()).replace(/\D/g, ''));
    expect(after).toBe(before + 10);
  });

  test('an unknown badge is refused rather than recorded', async ({ page }) => {
    await page.goto('/web/admin/dashboard');
    await page.locator('.roster-row a:has-text("Open")').first().click();
    await page.waitForURL(/\/web\/teacher\/student\//);
    const id = page.url().split('/').pop();

    const res = await page.evaluate(async (studentId) => {
      const body = new URLSearchParams({ badgeCode: 'NOT_A_BADGE', reason: 'x' });
      const r2 = await fetch(`/web/teacher/student/${studentId}/award`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body.toString(),
      });
      return { status: r2.status, text: (await r2.text()).slice(0, 4000) };
    }, id);

    expect(res.status).toBeLessThan(500);
    expect(res.text).toContain('Unknown award');
  });
});
