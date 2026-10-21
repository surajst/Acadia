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
    await login(page, 'admin@greenwood.com', process.env.DEMO_ADMIN_PASSWORD);
    const inviteResult = await page.evaluate(async (email) => {
      const params = new URLSearchParams({
        fullName: 'New Principal', email, role: 'PRINCIPAL',
      });
      const res = await fetch('/web/admin/staff/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params.toString(),
      });
      return res.json();
    }, principalEmail);
    expect(inviteResult.status).toBe('created');
    // Invites land APPROVED: this console is only reachable by an ADMIN or
    // PRINCIPAL, the two roles the approval queue waits for, so a pending
    // invite waited on a decision the inviter had already made.
    expect(inviteResult.approvalStatus).toBe('APPROVED');

    // No approval step any more: inviting from this console is the approval, so
    // the queue has nothing to decide and /approve refuses a non-pending
    // account. What matters is that the registry reports the account usable.
    const registryStatus = await page.evaluate(async (email) => {
      const staff = await (await fetch('/web/admin/staff')).json();
      return staff.find(s => s.email === email);
    }, principalEmail);
    expect(registryStatus.approvalStatus).toBe('APPROVED');
    expect(registryStatus.active).toBe(true);

    // Log in as the new principal
    await page.context().clearCookies();
    await login(page, principalEmail, inviteResult.temporaryPassword);

    // Dashboard renders the read-only KPI cards. The Slate-mist reskin split the
    // labels into an eyebrow + a percent figure, so the card reads "Curriculum"
    // (not the old "Curriculum Completion") and "Fee collection" (sentence case since the shell).
    await page.goto('/web/admin/dashboard');
    await expect(page.locator('body')).not.toContainText('Forbidden');
    await expect(page.locator('body')).toContainText('Curriculum');
    await expect(page.locator('body')).toContainText('Fee collection');

    // Every link in the principal's sidebar opens. It used to offer School
    // Management, Fees, Subjects, the Setup Wizard and more -- all ADMIN-only,
    // so a principal clicking round the sidebar met a 403 at almost every turn.
    await page.waitForLoadState('networkidle');
    const sidebarLinks = await page.locator('a.navlink').evaluateAll(
      (links) => [...new Set(links.map((a) => a.getAttribute('href')))]);
    expect(sidebarLinks.length, 'the sidebar should still offer something').toBeGreaterThan(0);
    for (const href of sidebarLinks) {
      const res = await page.request.get(href);
      expect(res.status(), `sidebar link ${href} must open for a principal`).not.toBe(403);
    }
    for (const adminOnly of ['/web/admin/management', '/web/admin/fees', '/web/admin/subjects',
      '/web/onboard/setup', '/web/admin/assignments', '/web/management/upload']) {
      expect(sidebarLinks, `${adminOnly} is ADMIN-only and must not be offered`).not.toContain(adminOnly);
    }

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
