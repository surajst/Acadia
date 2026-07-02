const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

test.describe('Sprint 2: Self-serve Onboarding Wizard', () => {

  test('Public signup creates a new school and establishes a real session', async ({ page }) => {
    const suffix = Date.now();
    const subdomain = `test-school-${suffix}`;
    const adminEmail = `admin-${suffix}@testschool.com`;

    await page.goto('/web/onboard/signup');
    await page.fill('#schoolName', `Test School ${suffix}`);
    await page.fill('#subdomain', subdomain);
    await page.fill('#adminFullName', 'Test Admin');
    await page.fill('#adminEmail', adminEmail);
    await page.fill('#adminPassword', 'PilotLaunchSecure2026!');
    await page.click('#submitBtn');

    await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/onboard/signup'), { timeout: 30000 });
    await expect(page.locator('body')).not.toContainText('Forbidden');

    // Re-submitting the same subdomain surfaces the API's error message in the form, not a raw failure
    await page.context().clearCookies();
    await page.goto('/web/onboard/signup');
    await page.fill('#schoolName', `Test School ${suffix} Dup`);
    await page.fill('#subdomain', subdomain);
    await page.fill('#adminFullName', 'Another Admin');
    await page.fill('#adminEmail', `another-${suffix}@testschool.com`);
    await page.fill('#adminPassword', 'PilotLaunchSecure2026!');
    await page.click('#submitBtn');

    const errorMsg = page.locator('#errorMsg');
    await expect(errorMsg).toBeVisible({ timeout: 10000 });
    await expect(errorMsg).toContainText('Subdomain already in use');
  });

  test('New admin completes the setup wizard: class section, staff invite, parent add', async ({ page }) => {
    const suffix = Date.now();
    const subdomain = `wizard-school-${suffix}`;
    const adminEmail = `admin-${suffix}@wizardschool.com`;
    const teacherEmail = `teacher-${suffix}@wizardschool.com`;

    // Sign up a fresh school so this test is self-contained and tenant-isolated
    await page.goto('/web/onboard/signup');
    await page.fill('#schoolName', `Wizard School ${suffix}`);
    await page.fill('#subdomain', subdomain);
    await page.fill('#adminFullName', 'Wizard Admin');
    await page.fill('#adminEmail', adminEmail);
    await page.fill('#adminPassword', 'PilotLaunchSecure2026!');
    await page.click('#submitBtn');
    await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/onboard/signup'), { timeout: 30000 });

    await page.goto('/web/onboard/setup');

    // Step 1: add a class section
    const gradeName = `Grade-${suffix}`;
    await page.fill('#gradeName', gradeName);
    await page.fill('#sectionName', 'A');
    await page.fill('#roomNumber', 'Room 1');
    await page.click('#classSectionForm button[type="submit"]');
    await expect(page.locator('#classSectionRows')).toContainText(gradeName, { timeout: 10000 });

    // Step 3: invite a teacher
    await page.evaluate(() => goToStep(3));
    await page.fill('#staffFullName', 'New Teacher');
    await page.fill('#staffEmail', teacherEmail);
    await page.fill('#staffPassword', 'PilotLaunchSecure2026!');
    await page.selectOption('#staffRole', 'TEACHER');
    await page.click('#staffForm button[type="submit"]');
    await expect(page.locator('#staffRows')).toContainText(teacherEmail, { timeout: 10000 });

    // The invited teacher can independently log in and reach their own dashboard
    await page.context().clearCookies();
    await login(page, teacherEmail, 'PilotLaunchSecure2026!');
    await page.goto('/web/teacher/dashboard');
    await expect(page.locator('body')).not.toContainText('Forbidden');

    // Admin adds a parent with login provisioning
    await page.context().clearCookies();
    await login(page, adminEmail, 'PilotLaunchSecure2026!');
    const parentEmail = `parent-${suffix}@wizardschool.com`;
    const addParentResult = await page.evaluate(async (email) => {
      const params = new URLSearchParams({
        firstName: 'New', lastName: 'Parent', loginEmail: email, loginPassword: 'PilotLaunchSecure2026!',
      });
      const res = await fetch('/web/admin/parent/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params.toString(),
      });
      return res.json();
    }, parentEmail);
    expect(addParentResult.status).toBe('created');

    // The newly provisioned parent login also works
    await page.context().clearCookies();
    await login(page, parentEmail, 'PilotLaunchSecure2026!');
    await page.goto('/web/parent/portal');
    await expect(page.locator('body')).not.toContainText('Forbidden');
  });

});
