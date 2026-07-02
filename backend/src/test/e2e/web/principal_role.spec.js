const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

test.describe('Sprint 3: PRINCIPAL role and access boundaries', () => {

  test('Principal sees read-only oversight KPIs and is blocked from data-entry pages', async ({ page }) => {
    const suffix = Date.now();
    const principalEmail = `principal-${suffix}@greenwood.com`;

    // Invite a PRINCIPAL as an existing ADMIN
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
    const inviteResult = await page.evaluate(async (email) => {
      const params = new URLSearchParams({
        fullName: 'New Principal', email, password: 'PilotLaunchSecure2026!', role: 'PRINCIPAL',
      });
      const res = await fetch('/web/admin/staff/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params.toString(),
      });
      return res.json();
    }, principalEmail);
    expect(inviteResult.status).toBe('created');

    // Log in as the new principal
    await page.context().clearCookies();
    await login(page, principalEmail, 'PilotLaunchSecure2026!');

    // Dashboard renders the read-only KPI cards
    await page.goto('/web/admin/dashboard');
    await expect(page.locator('body')).not.toContainText('Forbidden');
    await expect(page.locator('body')).toContainText('Curriculum Completion');
    await expect(page.locator('body')).toContainText('Fee Collection');

    // Blocked from ADMIN-only data-entry / setup pages
    for (const path of ['/web/admin/management', '/web/admin/fees', '/web/admin/subjects', '/web/onboard/setup']) {
      const response = await page.goto(path);
      expect(response.status(), `expected 403 for ${path}`).toBe(403);
    }

    // Read-only oversight APIs are still accessible
    const schoolProgress = await page.request.get('/api/principal/progress/school');
    expect(schoolProgress.status()).toBe(200);
    const feeSummary = await page.request.get('/api/principal/fee-summary');
    expect(feeSummary.status()).toBe(200);
  });

});
