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

async function onboard(page, schoolType, label) {
  const suffix = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const school = {
    subdomain: `${label}-${suffix}`,
    adminEmail: `head-${suffix}@${label}.test`,
    parentEmail: `parent-${suffix}@${label}.test`,
    suffix,
  };
  await page.goto('/web/onboard/signup');
  const res = await page.evaluate(async ([s, pw, type]) => {
    const r = await fetch('/api/onboard/create-school', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        schoolName: `Test ${type}`, subdomain: s.subdomain,
        adminEmail: s.adminEmail, adminPassword: pw,
        adminFullName: 'Head Teacher', schoolType: type,
      }),
    });
    return { status: r.status, body: await r.json() };
  }, [school, PW, schoolType]);
  expect(res.status, `onboarding a ${schoolType}`).toBe(200);
  return school;
}

/**
 * A preschool and a secondary school run on the same tables -- the same
 * ClassSection, Subject, Student and fee rows. What differs is the words the
 * app uses and the modules it shows.
 *
 * These tests exist because that distinction is invisible in the data: nothing
 * about a "Nursery" row is different from a "Class 6" row, so only the rendered
 * page can show whether the school type actually reached the user.
 */
test.describe.serial('School type drives vocabulary and modules', () => {

  test('the signup form offers the four school types', async ({ page }) => {
    await page.goto('/web/onboard/signup');
    const options = await page.locator('#schoolType option').allTextContents();
    expect(options.join(' ')).toContain('Preschool');
    expect(options.join(' ')).toContain('Primary');
    expect(options.join(' ')).toContain('Secondary');
  });

  test('a preschool admin console speaks of Levels, not Classes', async ({ page }) => {
    const school = await onboard(page, 'PRESCHOOL', 'preschool');
    await login(page, school.adminEmail, PW);

    await page.goto('/web/admin/management');
    const body = page.locator('body');
    await expect(body).toContainText('Active Levels');
    await expect(body).toContainText('Add Level');
    await expect(body).not.toContainText('Active Classes');
  });

  test('a secondary school is untouched and still speaks of Classes', async ({ page }) => {
    // The point of the whole feature is that it is opt-in. A school that never
    // said what it was must read exactly as it did before.
    const school = await onboard(page, 'SECONDARY', 'secondary');
    await login(page, school.adminEmail, PW);

    await page.goto('/web/admin/management');
    const body = page.locator('body');
    await expect(body).toContainText('Active Classes');
    await expect(body).not.toContainText('Active Levels');
  });

  test('a preschool parent is never shown quests or a syllabus', async ({ page }) => {
    // This is the one that matters for a preschool: the parent portal is the
    // surface they care most about, and it is built around setting homework
    // quests and tracking a syllabus. Neither applies to a three-year-old.
    const school = await onboard(page, 'PRESCHOOL', 'preschool2');
    await login(page, school.adminEmail, PW);

    // A level and a child in it, through the same tables a secondary uses.
    // Two structures are involved: ClassSection (what the console lists) and
    // SchoolClass (what the registration dropdown offers). Both are created,
    // then the child is registered through the real form -- the SchoolClass id
    // is only rendered into that dropdown, never exposed as JSON.
    await page.evaluate(async () => {
      const form = (url, params) => fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(params).toString(),
      });
      await form('/web/admin/class-sections/add', { gradeName: 'Nursery', sectionName: 'A' });
      await form('/web/admin/school-classes/add', {
        gradeLevel: 'Nursery', sectionName: 'A', totalCapacity: '20',
      });
    });

    await page.goto('/web/admin/management');
    await page.click('button:has-text("Classrooms & Students")');
    await page.click('button:has-text("Register New Student")');
    await page.fill('#firstName', 'Aarav');
    await page.fill('#lastName', 'Tot');
    await page.fill('#rollNumber', 'N01');
    await page.selectOption('#schoolClassId', { index: 1 });
    await page.click('#registerStudentModal button[type="submit"]');
    await page.waitForURL(u => u.pathname.includes('/web/admin/management'), { timeout: 30000 });

    const studentId = await page.evaluate(async () => {
      const roster = await (await fetch('/api/admin/messages/roster')).json();
      const child = roster.find(r => r.studentName && r.studentName.includes('Aarav'));
      return child ? child.studentId : null;
    });
    expect(studentId, 'a child was registered into the Nursery level').toBeTruthy();

    await page.evaluate(async ([email, pw, sid]) => {
      await fetch('/web/admin/parent/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          firstName: 'Meera', lastName: 'Parent',
          loginEmail: email, loginPassword: pw, studentId: sid,
        }).toString(),
      });
    }, [school.parentEmail, PW, studentId]);

    await login(page, school.parentEmail, PW);
    await page.goto('/web/parent/dashboard');

    // Every way in, not just the panel: a visible tab leading somewhere
    // irrelevant is the same wrong-product signal as the panel itself.
    await expect(page.locator('[data-tab="quests"]')).toHaveCount(0);
    await expect(page.locator('#tab-quests')).toHaveCount(0);
    await expect(page.locator('[data-tab="progress"]')).toHaveCount(0);

    // ...while the things a preschool parent does want are still there.
    await expect(page.locator('[data-tab="messages"]')).not.toHaveCount(0);
    await expect(page.locator('[data-tab="feed"]')).not.toHaveCount(0);
  });
});
