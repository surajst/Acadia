const { test, expect } = require('@playwright/test');

/**
 * Thirty of the pilot school's fifty-two children had no emergency contact
 * number, and nothing on any screen said so. The field is optional and stays
 * optional -- a school cannot always get a number the day a child is enrolled,
 * and refusing the enrolment over it would be worse. What was missing was anybody
 * knowing.
 *
 * MissingEmergencyContactTest owns the counting and the scoping. This covers what
 * an admin can actually do with it: see the figure, press it, and get back.
 */
async function loginAsAdmin(page) {
  await page.goto('/login');
  await page.fill('#username', 'admin@greenwood.com');
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'),
    { timeout: 90000 });
}

test.describe('Children with no emergency contact', () => {

  test('the dashboard says how many, and the number opens the list', async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsAdmin(page);
    await page.goto('/web/admin/dashboard');
    await page.waitForLoadState('networkidle');

    const kpi = page.locator('[data-kpi-missing-emergency]');
    // The card is only drawn when there is something to do about it, so if the
    // seed ever gives every child a number this test is measuring nothing and
    // should say so rather than pass.
    await expect(kpi, 'the seeded school should have children with no emergency number')
      .toBeVisible({ timeout: 30000 });

    const missing = parseInt((await kpi.textContent()).trim(), 10);
    expect(missing, 'a zero would not be rendered at all').toBeGreaterThan(0);

    // The whole point of the figure being a link.
    await kpi.click();
    await page.waitForLoadState('networkidle');
    expect(page.url()).toContain('missingEmergencyContact=true');

    // The list is exactly as long as the number promised.
    await expect(page.locator('#rosterResultCount'))
      .toHaveText(new RegExp(`^${missing} student`), { timeout: 30000 });
    // And it says why it is short, rather than reading as missing children.
    // Scoped to the roster card: there are three .card-sub on this page, and an
    // unscoped locator is one page-layout change away from asserting about the
    // fee waivers panel instead.
    await expect(page.locator('#rosterCard .card-sub'))
      .toContainText(/no emergency contact number/i);
  });

  test('the filter can be cleared from where it is shown', async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsAdmin(page);
    await page.goto('/web/admin/dashboard');
    await page.waitForLoadState('networkidle');

    const everyone = await page.locator('#rosterResultCount').textContent();

    await page.locator('[data-kpi-missing-emergency]').click();
    await page.waitForLoadState('networkidle');
    const filtered = await page.locator('#rosterResultCount').textContent();
    expect(filtered, 'the filter has to change the list, or there is nothing to clear')
      .not.toBe(everyone);

    // Removable the same way it was applied. A filter you can only clear by
    // editing the address is a trap.
    await page.locator('[data-clear-missing-emergency]').click();
    await page.waitForLoadState('networkidle');

    expect(page.url()).not.toContain('missingEmergencyContact');
    await expect(page.locator('#rosterResultCount')).toHaveText(everyone.trim());
    await expect(page.locator('[data-clear-missing-emergency]')).toHaveCount(0);
  });

  /**
   * The field stays optional, which is the part most easily lost. Registering a
   * child without a number has to keep working -- the count exists so somebody can
   * chase it later, not so the form can refuse the enrolment.
   */
  test('a child can still be registered with no emergency contact', async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsAdmin(page);

    await page.goto('/web/admin/management');
    await page.click('button:has-text("Classrooms & Students")');
    await page.click('button:has-text("Register New Student")');
    await expect(page.locator('#registerStudentModal')).toBeVisible();

    await page.fill('#firstName', 'Noemergency');
    await page.fill('#lastName', 'Contact');
    await page.fill('#rollNumber', 'QA-P19-01');
    await page.selectOption('#schoolClassId', { label: 'Grade 6 - A' });

    await page.click('#registerStudentModal button[type="submit"]');
    await page.waitForURL(url => url.search.includes('success=student_added'), { timeout: 90000 });
    await page.waitForLoadState('networkidle');

    await expect(page.locator('#registerStudentModal')).not.toBeVisible();
  });
});
