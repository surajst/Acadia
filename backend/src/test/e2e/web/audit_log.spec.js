const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

test.describe('Sprint 4: Audit Trail', () => {

  test('Audited action produces a visible log entry; access is restricted to ADMIN and PRINCIPAL', async ({ page }) => {
    const suffix = Date.now();
    const gradeName = `Audit-Grade-${suffix}`;

    // ADMIN performs an audited write
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
    await page.evaluate(async (grade) => {
      const params = new URLSearchParams({ gradeName: grade, sectionName: 'A', roomNumber: '' });
      await fetch('/web/admin/class-sections/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params.toString(),
      });
    }, gradeName);

    // The action shows up on the audit log page
    await page.goto('/web/admin/audit-log');
    await page.waitForFunction((grade) => {
      const rows = document.getElementById('auditRows');
      return rows && rows.textContent.includes(grade);
    }, gradeName, { timeout: 10000 });
    const auditRow = page.locator('#auditRows tr', { hasText: gradeName });
    await expect(auditRow).toContainText('CLASS_SECTION_ADDED');

    // A TEACHER cannot reach the audit log directly
    await page.context().clearCookies();
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');
    const teacherResponse = await page.goto('/web/admin/audit-log');
    expect(teacherResponse.status()).toBe(403);

    // A PRINCIPAL can reach the audit log (cross-cutting oversight)
    const principalEmail = `principal-audit-${suffix}@greenwood.com`;
    await page.context().clearCookies();
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
    const inviteResult = await page.evaluate(async (email) => {
      const params = new URLSearchParams({
        fullName: 'Audit Principal', email, password: 'PilotLaunchSecure2026!', role: 'PRINCIPAL',
      });
      const res = await fetch('/web/admin/staff/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params.toString(),
      });
      return res.json();
    }, principalEmail);
    expect(inviteResult.status).toBe('created');

    await page.context().clearCookies();
    await login(page, principalEmail, 'PilotLaunchSecure2026!');
    const principalResponse = await page.goto('/web/admin/audit-log');
    expect(principalResponse.status()).toBe(200);
  });

});
