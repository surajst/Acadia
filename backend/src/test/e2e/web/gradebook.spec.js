const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

test.describe('Sprint 5: Gradebook (bulk score entry)', () => {

  test('Teacher creates an assessment, enters scores in bulk, and scores prefill on reload', async ({ page }) => {
    const suffix = Date.now();
    const title = `E2E Quiz ${suffix}`;

    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/teacher/assessments');

    // Class dropdown populates from /api/teacher/classes
    await page.waitForFunction(() => {
      const s = document.getElementById('classSelect');
      return s && s.options.length > 0;
    }, { timeout: 10000 });

    // Create a new assessment
    await page.fill('#newTitle', title);
    await page.selectOption('#newTerm', 'TERM1');
    await page.fill('#newMaxScore', '50');
    await page.fill('#newAssessmentDate', '2026-07-01');
    await page.click('#createAssessmentForm button[type="submit"]');

    // It becomes selectable and the roster loads
    await page.waitForFunction((t) => {
      const s = document.getElementById('assessmentSelect');
      return s && Array.from(s.options).some(o => o.textContent.includes(t));
    }, title, { timeout: 10000 });
    const optionValue = await page.evaluate((t) => {
      const s = document.getElementById('assessmentSelect');
      return Array.from(s.options).find(o => o.textContent.includes(t)).value;
    }, title);
    await page.selectOption('#assessmentSelect', optionValue);

    await page.waitForFunction(() => document.querySelectorAll('.score-input').length > 0, { timeout: 10000 });
    await expect(page.locator('#rosterTitle')).toContainText(title);

    // Enter scores for the first two students
    const inputs = page.locator('.score-input');
    await inputs.nth(0).fill('42');
    await inputs.nth(1).fill('38');
    await page.click('#saveScoresBtn');
    await expect(page.locator('#saveStatus')).toBeVisible({ timeout: 10000 });

    // Reload — scores must prefill from the server
    await page.reload();
    await page.waitForFunction((t) => {
      const s = document.getElementById('assessmentSelect');
      return s && Array.from(s.options).some(o => o.textContent.includes(t));
    }, title, { timeout: 10000 });
    await page.selectOption('#assessmentSelect', optionValue);
    await page.waitForFunction(() => document.querySelectorAll('.score-input').length > 0, { timeout: 10000 });

    await expect(page.locator('.score-input').nth(0)).toHaveValue('42');
    await expect(page.locator('.score-input').nth(1)).toHaveValue('38');
  });

});
