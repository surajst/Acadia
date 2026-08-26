const { test, expect } = require('@playwright/test');

const PW = 'PilotLaunchSecure2026!';

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(u => u.pathname.includes('/web/') && !u.pathname.includes('/login'),
    { timeout: 30000 });
}

/**
 * Allergies and who may collect a child are the two records a preschool asks
 * about first, and they are safety records rather than contact details: the
 * empty state has to be honest, and a revoked name has to actually go.
 */
test.describe.serial('Care & safety record', () => {
  let studentId;

  test.beforeEach(async ({ page }) => {
    await login(page, 'admin@greenwood.com', PW);
    studentId = await page.evaluate(async () => {
      const roster = await (await fetch('/api/admin/messages/roster')).json();
      return roster[0].studentId;
    });
  });

  test('an empty record says nothing is recorded, not that there is nothing', async ({ page }) => {
    await page.goto(`/web/teacher/student/${studentId}`);
    const body = page.locator('body');
    await expect(body).toContainText('Care & Safety');
    // "Nothing recorded — which is not the same as none." A teacher must be
    // able to tell an empty field from a child with no allergies.
    await expect(body).toContainText('which is not the same as none');
    await expect(page.locator('[data-pickup-row]')).toHaveCount(0);
  });

  test('admin records a date of birth, allergies and an emergency contact', async ({ page }) => {
    await page.goto(`/web/teacher/student/${studentId}`);
    await page.evaluate(async (id) => {
      const csrf = document.querySelector('input[name="_csrf"]');
      const params = new URLSearchParams({
        firstName: 'Arjun', lastName: 'Sharma', rollNumber: '6A-41',
        dateOfBirth: '2021-06-15',
        medicalNotes: 'Peanut allergy — EpiPen in the office',
        emergencyContactName: 'Meera Sharma',
        emergencyContactPhone: '+91 90000 11111',
      });
      if (csrf) params.append(csrf.name, csrf.value);
      await fetch(`/web/admin/student/${id}/update`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params.toString(),
      });
    }, studentId);

    await page.goto(`/web/teacher/student/${studentId}`);
    const body = page.locator('body');
    await expect(body).toContainText('Peanut allergy');
    await expect(body).toContainText('Meera Sharma');
    // Age is computed from the date, never stored -- a stored age is wrong the
    // day after it is written.
    await expect(body).toContainText('yrs');
  });

  test('admin authorises someone to collect, then revokes it', async ({ page }) => {
    await page.goto(`/web/teacher/student/${studentId}`);
    await page.fill('[data-pickup-name]', 'Anita Rao');
    await page.locator('input[name="relationship"]').fill('Grandmother');
    await page.locator('input[name="phone"]').first().fill('+91 90000 22222');
    await page.click('button:has-text("Authorise someone")');
    await page.waitForURL(u => u.pathname.includes('/web/teacher/student/'));

    await expect(page.locator('[data-pickup-row]')).toHaveCount(1);
    await expect(page.locator('body')).toContainText('Anita Rao');
    await expect(page.locator('body')).toContainText('Grandmother');

    page.once('dialog', d => d.accept());
    await page.click('[data-revoke-pickup]');
    await page.waitForURL(u => u.pathname.includes('/web/teacher/student/'));

    await expect(page.locator('[data-pickup-row]')).toHaveCount(0);
    await expect(page.locator('body')).not.toContainText('Anita Rao');
  });
});
