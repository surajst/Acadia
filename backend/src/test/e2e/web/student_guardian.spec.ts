import { test, expect } from '@playwright/test';

// Regression coverage for the prod bug where a student who never had a
// guardian added showed a hardcoded phantom parent ("Ramesh Sharma") on
// their profile, and the Register-Student flow had no way to capture a
// guardian at all. These tests drive the REAL self-serve add flow.

async function login(page: any, username: string, password: string) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL((url: URL) => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

// Seeded "Grade 6 - A" class section id (used by the other roster specs).
const GRADE6A = '11111111-2222-3333-4444-555555555551';

async function registerStudent(page: any, opts: {
  first: string; last: string; roll: string;
  guardianFirst?: string; guardianLast?: string; guardianPhone?: string;
}) {
  await page.goto('/web/admin/management');
  // Reveal the Classrooms & Students section from the card hub, then open the modal.
  await page.click('button:has-text("Classrooms & Students")');
  await page.click('button:has-text("Register New Student")');
  await expect(page.locator('#registerStudentModal')).toBeVisible();
  await page.fill('#firstName', opts.first);
  await page.fill('#lastName', opts.last);
  await page.fill('#rollNumber', opts.roll);
  await page.selectOption('#schoolClassId', { label: 'Grade 6 - A' });
  if (opts.guardianFirst) await page.fill('#guardianFirstName', opts.guardianFirst);
  if (opts.guardianLast) await page.fill('#guardianLastName', opts.guardianLast);
  if (opts.guardianPhone) await page.fill('#guardianPhone', opts.guardianPhone);
  // Scope to the modal: the hub card description "…register students…" also
  // substring-matches "Register Student", so a bare text selector is ambiguous.
  await page.click('#registerStudentModal button[type="submit"]');
  await page.waitForURL((url: URL) => url.pathname.includes('/web/admin/management'));
}

async function openProfile(page: any, fullName: string, firstName: string) {
  await page.goto(`/web/admin/dashboard?classId=${GRADE6A}&name=${encodeURIComponent(firstName)}`);
  const row = page.locator(`tr:has-text("${fullName}")`).first();
  await expect(row).toBeVisible();
  await row.locator('a:has-text("View Profile Data")').click();
  await page.waitForURL((url: URL) => url.pathname.includes('/web/teacher/student/'));
}

test.describe('Student guardian capture & profile display', () => {

  test('a guardian entered at registration is linked and shown on the profile (no phantom parent)', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');

    await registerStudent(page, {
      first: 'Guardiantest', last: 'Studentone', roll: '6A-701',
      guardianFirst: 'Gurmeet', guardianLast: 'Singh', guardianPhone: '+91 90000 12345',
    });

    // One-time sign-in credentials are surfaced for both the student (roll number)
    // and the guardian (phone) so the admin can relay them.
    await expect(page.locator('text=Sign-in credentials created')).toBeVisible();
    await expect(page.locator('body')).toContainText('Student login — 6A-701');
    await expect(page.locator('body')).toContainText('Guardian login — +91 90000 12345');

    await openProfile(page, 'Guardiantest Studentone', 'Guardiantest');

    // The guardian we entered appears...
    await expect(page.locator('text=Gurmeet Singh')).toBeVisible();
    // ...and the old hardcoded phantom parent does NOT, nor the empty state.
    await expect(page.locator('body')).not.toContainText('Ramesh Sharma');
    await expect(page.locator('body')).not.toContainText('No guardian linked yet');
  });

  test('a student with no guardian shows an honest empty state, not a fabricated parent', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');

    await registerStudent(page, { first: 'Noguardian', last: 'Studenttwo', roll: '6A-702' });

    await openProfile(page, 'Noguardian Studenttwo', 'Noguardian');

    await expect(page.locator('text=No guardian linked yet')).toBeVisible();
    await expect(page.locator('body')).not.toContainText('Ramesh Sharma');
  });

  test('admin can reset/re-issue student and guardian passwords from the profile', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');

    await registerStudent(page, {
      first: 'Resettest', last: 'Studentthree', roll: '6A-703',
      guardianFirst: 'Resetguardian', guardianLast: 'Kaur', guardianPhone: '+91 90000 33333',
    });
    await openProfile(page, 'Resettest Studentthree', 'Resettest');

    // Re-issue the student's password → confirm dialog → new one-time credentials.
    await page.click('button:has-text("Reset student password")');
    await page.click('button:has-text("Yes, reset password")');
    await expect(page.locator('text=New sign-in credentials')).toBeVisible();
    await expect(page.locator('body')).toContainText('Student login — 6A-703');

    // Re-issue the guardian's password too (same confirm step).
    await page.click('button:has-text("Reset guardian password")');
    await page.click('button:has-text("Yes, reset password")');
    await expect(page.locator('body')).toContainText('Guardian login — +91 90000 33333');
  });

  test('admin can edit a student\'s details from the profile', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');

    await registerStudent(page, { first: 'Edittest', last: 'Studentfour', roll: '6A-704' });
    await openProfile(page, 'Edittest Studentfour', 'Edittest');

    // The student was registered without a guardian → empty state.
    await expect(page.locator('text=No guardian linked yet')).toBeVisible();

    await page.click('button:has-text("Edit")');
    await expect(page.locator('#editStudentModal')).toBeVisible();
    await expect(page.locator('#editFirstName')).toHaveValue('Edittest');
    await page.fill('#editLastName', 'Renamedstudent');
    await page.fill('#editRollNumber', '6A-704Z');
    // Add a guardian in the same edit (create path).
    await page.fill('#editGuardianFirstName', 'Editadded');
    await page.fill('#editGuardianLastName', 'Guardian');
    await page.fill('#editGuardianPhone', '+91 90000 44444');
    await page.click('button:has-text("Save Changes")');

    await expect(page.locator('text=Student details updated')).toBeVisible();
    await expect(page.locator('h2')).toContainText('Edittest Renamedstudent');
    await expect(page.locator('body')).toContainText('Roll: 6A-704Z');
    // The newly added guardian now shows, empty state is gone, and its login is surfaced.
    await expect(page.locator('body')).toContainText('Editadded Guardian');
    await expect(page.locator('body')).not.toContainText('No guardian linked yet');
    await expect(page.locator('body')).toContainText('Guardian login — +91 90000 44444');

    // Editing again pre-fills the guardian and updates it in place.
    await page.click('button:has-text("Edit")');
    await expect(page.locator('#editGuardianFirstName')).toHaveValue('Editadded');
    await page.fill('#editGuardianFirstName', 'Editchanged');
    await page.click('button:has-text("Save Changes")');
    await expect(page.locator('body')).toContainText('Editchanged Guardian');
  });

});
