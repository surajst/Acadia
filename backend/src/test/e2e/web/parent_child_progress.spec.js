const { test, expect } = require('@playwright/test');

test.describe('ACADIA Parent Child Progress View', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
  });

  test('Full parent child progress view test', async ({ page }) => {
    test.setTimeout(180000);

    page.on('console', msg => console.log('BROWSER CONSOLE:', msg.text()));
    page.on('pageerror', err => console.log('BROWSER PAGE ERROR:', err.message));
    page.on('response', response => {
      if (response.status() === 403) console.log(`403 FORBIDDEN URL: ${response.url()}`);
      if (response.status() === 500) console.log(`500 ERROR URL: ${response.url()}`);
    });

    // ─── Step 1: Student marks 2 Science chapters complete ───────────────────
    await page.goto('/login');
    await page.fill('input[name="username"]', 'arjun@gmail.com');
    await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
    await Promise.all([
      page.waitForURL('**/web/student/**', { timeout: 90000 }),
      page.click('button:has-text("Sign in")')
    ]);
    await page.waitForLoadState('domcontentloaded');
    await page.waitForURL('**/web/student/**', { timeout: 90000 });

    await page.goto('/web/student/portal');
    await page.waitForLoadState('domcontentloaded');
    await page.locator('[data-tab="syllabus"]').first().click();
    // Wait for syllabus JS to finish fetching and rendering subject cards
    await page.waitForFunction(() => {
      const skeleton = document.getElementById('syllabus-skeleton');
      return skeleton && skeleton.classList.contains('hidden');
    }, { timeout: 30000 });

    const scienceCard = page.locator('div[id="subject-card-SCIENCE"]');
    await expect(scienceCard).toBeVisible({ timeout: 20000 });
    await scienceCard.locator('button:has-text("Show / Hide Chapters")').click();

    await scienceCard.locator('button:has-text("Mark Complete")').first().click();
    await expect(scienceCard.locator('text=Pending').first()).toBeVisible({ timeout: 10000 });

    await scienceCard.locator('button:has-text("Mark Complete")').first().click();
    await expect(scienceCard.locator('text=Pending')).toHaveCount(2, { timeout: 10000 });

    // Logout via cookie clear (logout button is sr-only on student portal)
    await page.context().clearCookies();

    // ─── Step 2: Teacher approves both chapters ──────────────────────────────
    await page.goto('/login');
    await page.fill('input[name="username"]', 'teacher@greenwood.com');
    await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
    await Promise.all([
      page.waitForURL('**/web/teacher/dashboard*', { timeout: 90000 }),
      page.click('button:has-text("Sign in")')
    ]);
    await page.waitForLoadState('domcontentloaded');
    await page.waitForURL('**/web/teacher/**', { timeout: 90000 });

    await expect(page.locator('h2:has-text("Syllabus Verification Queue")')).toBeVisible({ timeout: 10000 });

    const approveBtns = page.locator('[id^="progress-row-"] button:has-text("Approve")');
    await expect(approveBtns).toHaveCount(2, { timeout: 10000 });

    page.on('dialog', dialog => dialog.accept());
    await approveBtns.first().click();
    await expect(approveBtns).toHaveCount(1, { timeout: 10000 });
    await approveBtns.first().click();
    await expect(approveBtns).toHaveCount(0, { timeout: 10000 });

    // Logout via cookie clear
    await page.context().clearCookies();

    // ─── Step 3: Parent views child's progress on parent dashboard ───────────
    await page.goto('/login');
    await page.fill('input[name="username"]', 'ramesh@gmail.com');
    await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
    await Promise.all([
      page.waitForURL('**/web/parent/**', { timeout: 90000 }),
      page.click('button:has-text("Sign in")')
    ]);
    await page.waitForLoadState('domcontentloaded');
    await page.waitForURL('**/web/parent/**', { timeout: 90000 });

    // Explicitly navigate to the parent dashboard (which has the progress tab)
    await page.goto('/web/parent/dashboard');
    await page.waitForLoadState('domcontentloaded');

    // Assert the parent dashboard loaded — heading shows parent's name
    await expect(page.locator('h2:has-text("Ramesh Sharma")')).toBeVisible({ timeout: 10000 });

    // Click the progress tab
    await page.locator('[data-tab="progress"]').first().click();
    await page.waitForLoadState('domcontentloaded');

    // Progress loads asynchronously via /api/parent/child-progress.
    // Approved topics render as green rows: div.bg-emerald-500/5
    const approvedRows = page.locator('div.bg-emerald-500\\/5');
    await expect(approvedRows).toHaveCount(2, { timeout: 30000 });

    // Assert NO action buttons exist in the parent view (read-only)
    await expect(page.locator('button:has-text("Approve")')).toHaveCount(0);
    await expect(page.locator('button:has-text("Mark Complete")')).toHaveCount(0);
    await expect(page.locator('button:has-text("Reject")')).toHaveCount(0);

    // Logout via cookie clear
    await page.context().clearCookies();

    // ─── Step 4: Idempotency — re-login and confirm dashboard loads ──────────
    await page.goto('/login');
    await page.fill('input[name="username"]', 'ramesh@gmail.com');
    await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
    await Promise.all([
      page.waitForURL('**/web/parent/**', { timeout: 90000 }),
      page.click('button:has-text("Sign in")')
    ]);
    await page.waitForLoadState('domcontentloaded');
    await page.waitForURL('**/web/parent/**', { timeout: 90000 });
    await page.goto('/web/parent/dashboard');
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('h2:has-text("Ramesh Sharma")')).toBeVisible({ timeout: 10000 });

    // Logout via cookie clear
    await page.context().clearCookies();
  });
});
