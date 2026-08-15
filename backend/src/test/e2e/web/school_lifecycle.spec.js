const { test, expect } = require('@playwright/test');

/**
 * Full lifecycle of a REAL self-onboarded school.
 *
 * Every other spec in this suite starts from /test/reset — 500 seeded students,
 * admin@greenwood.com, fixed UUIDs. That is a useful fixture but it is not how a
 * school actually arrives, and it hides a whole class of bug: pages that look
 * correct against seeded data because they are showing seeded data. Hardcoded
 * mock content on the student profile survived for exactly this reason.
 *
 * This spec signs a brand-new school up through the public form and then drives
 * every role inside that tenant, touching nothing that was seeded.
 *
 * NEVER point this at production. Each run creates a permanent tenant and there
 * is no delete-tenant path. Local or CI only, against a disposable database.
 */

const PW = 'PilotLaunchSecure2026!';

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(u => u.pathname.includes('/web/') && !u.pathname.includes('/login'),
    { timeout: 30000 });
}

/** Signs up a fresh school and returns everything needed to drive it. */
async function onboardSchool(page, label) {
  const suffix = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const school = {
    suffix,
    name: `${label} School ${suffix}`,
    subdomain: `${label}-${suffix}`.toLowerCase(),
    adminEmail: `admin-${suffix}@${label}.test`,
    teacherEmail: `teacher-${suffix}@${label}.test`,
    parentEmail: `parent-${suffix}@${label}.test`,
    studentEmail: `student-${suffix}@${label}.test`,
    roll: `R${suffix}`.slice(0, 12),
  };

  await page.goto('/web/onboard/signup');
  await page.fill('#schoolName', school.name);
  await page.fill('#subdomain', school.subdomain);
  await page.fill('#adminFullName', `${label} Admin`);
  await page.fill('#adminEmail', school.adminEmail);
  await page.fill('#adminPassword', PW);
  await page.click('#submitBtn');
  await page.waitForURL(u => !u.pathname.includes('/onboard/signup'), { timeout: 30000 });

  return school;
}

/**
 * A SchoolClass is what a student is registered into. The onboarding wizard
 * creates a ClassSection, which is a different entity — so a school that only
 * completed the wizard has an empty "Class Assignment" dropdown and cannot
 * register anybody. Creating both here mirrors what an admin has to do.
 */
async function createClassroom(page, gradeLevel, sectionName) {
  return page.evaluate(async ([grade, section]) => {
    const post = (url, params) => fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams(params).toString(),
    });
    await post('/web/admin/school-classes/add',
      { gradeLevel: grade, sectionName: section, roomNumber: 'R1', totalCapacity: '30' });
    await post('/web/admin/class-sections/add',
      { gradeName: grade, sectionName: section, roomNumber: 'R1' });
    const sections = await (await fetch('/web/admin/class-sections')).json();
    return { sections };
  }, [gradeLevel, sectionName]);
}

/** Admin roster rows look like { studentId, studentName, className, hasParent }. */
function findStudent(roster, needle) {
  return (roster || []).find(r => (r.studentName || '').includes(needle));
}

test.describe.serial('Lifecycle of a self-onboarded school', () => {
  let school;

  test('a new school signs itself up and the admin lands in the console', async ({ page }) => {
    school = await onboardSchool(page, 'alpha');
    await page.goto('/web/admin/dashboard');
    await expect(page.locator('body')).not.toContainText('Forbidden');
    await expect(page.locator('body')).not.toContainText('Something went wrong');
  });

  test('admin creates a classroom, and it appears in the student form', async ({ page }) => {
    await login(page, school.adminEmail, PW);
    const { sections } = await createClassroom(page, `Grade-${school.suffix}`, 'A');
    expect(sections.length).toBeGreaterThanOrEqual(1);

    // The dropdown is the real gate on registering a student: it is populated
    // from SchoolClass, not from the wizard's ClassSection.
    await page.goto('/web/admin/management');
    await page.click('button:has-text("Classrooms & Students")');
    await page.click('button:has-text("Register New Student")');
    const options = page.locator('#schoolClassId option:not([disabled])');
    await expect(options).not.toHaveCount(0);
  });

  test('admin registers a student with a guardian, and the roll appears in the roster', async ({ page }) => {
    await login(page, school.adminEmail, PW);
    await page.goto('/web/admin/management');
    await page.click('button:has-text("Classrooms & Students")');
    await page.click('button:has-text("Register New Student")');

    await page.fill('#firstName', 'Asha');
    await page.fill('#lastName', `Student${school.suffix}`);
    await page.fill('#rollNumber', school.roll);
    const loginEmail = page.locator('#loginEmail');
    if (await loginEmail.count()) {
      await loginEmail.fill(school.studentEmail);
      await page.fill('#loginPassword', PW);
    }
    await page.selectOption('#schoolClassId', { index: 1 });
    await page.click('#registerStudentModal button[type="submit"]');
    await page.waitForURL(u => u.pathname.includes('/web/admin/management'), { timeout: 30000 });

    // The management page renders counts and classes, not student names, so the
    // roster endpoint is what actually proves the registration landed.
    const roster = await page.evaluate(async () =>
      (await fetch('/api/admin/messages/roster')).json());
    const flat = JSON.stringify(roster);
    expect(flat).toContain(`Student${school.suffix}`);

    const row = findStudent(roster, `Student${school.suffix}`);
    expect(row, 'the new student appears in the admin roster').toBeTruthy();
    expect(row.className).toContain(`Grade-${school.suffix}`);
    expect(row.hasParent, 'no guardian linked yet').toBe(false);
    school.studentId = row.studentId;
  });

  test('admin adds a parent linked to that student, and the parent can log in', async ({ page }) => {
    await login(page, school.adminEmail, PW);

    const body = await page.evaluate(async ([email, pw, studentId]) => {
      const params = new URLSearchParams({
        firstName: 'Meera', lastName: 'Guardian',
        loginEmail: email, loginPassword: pw, studentId,
      });
      const res = await fetch('/web/admin/parent/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params.toString(),
      });
      return res.json();
    }, [school.parentEmail, PW, school.studentId]);

    expect(body.status).toBe('created');

    // The roster's own hasParent flag is the assertion that the link took --
    // creating a parent row and linking it to a student are separate things.
    const linked = await page.evaluate(async () =>
      (await fetch('/api/admin/messages/roster')).json());
    expect(findStudent(linked, `Student${school.suffix}`).hasParent,
      'guardian is linked to the student').toBe(true);

    await page.context().clearCookies();
    await login(page, school.parentEmail, PW);
    await expect(page.locator('body')).not.toContainText('Forbidden');
  });

  test('admin hires a teacher, approves them, and assigns them to the class', async ({ page }) => {
    await login(page, school.adminEmail, PW);

    const result = await page.evaluate(async ([email, pw]) => {
      const form = (url, params) => fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(params).toString(),
      });

      await form('/web/admin/staff/add',
        { fullName: 'Ravi Teacher', email, password: pw, role: 'TEACHER' });

      // Staff arrive PENDING; an unapproved teacher must not be able to work.
      const staff = await (await fetch('/web/admin/staff')).json();
      const teacher = staff.find(s => s.email === email);
      if (!teacher) return { error: 'teacher not created', staff };
      const approve = await (await fetch(`/api/principal/staff/${teacher.id}/approve`,
        { method: 'POST' })).json();

      const sections = await (await fetch('/web/admin/class-sections')).json();
      const assign = await (await fetch('/api/admin/assignments/assign', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          teacherId: teacher.id,
          classSectionId: sections[0].id,
          subjectName: 'Mathematics',
          isHomeClass: true,
        }),
      })).json();

      return { approve, assign, sectionId: sections[0].id };
    }, [school.teacherEmail, PW]);

    expect(result.error).toBeUndefined();
    expect(result.approve.status).toBe('approved');
    expect(result.assign).toBeTruthy();
    school.sectionId = result.sectionId;
  });

  test('the teacher sees their own class and marks attendance for the student', async ({ page }) => {
    await login(page, school.teacherEmail, PW);

    const classes = await page.evaluate(async () =>
      (await fetch('/api/teacher/classes')).json());
    expect(JSON.stringify(classes)).toContain(`Grade-${school.suffix}`);

    await page.goto('/web/teacher/attendance');
    await expect(page.locator('body')).not.toContainText('Forbidden');
    await expect(page.locator('body')).toContainText(`Student${school.suffix}`);

    const marked = await page.evaluate(async (studentId) => {
      const res = await fetch('/web/teacher/attendance/submit', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ studentIds: studentId, statuses: 'PRESENT' }).toString(),
      });
      return res.status;
    }, school.studentId);
    expect(marked).toBeLessThan(400);

    const summary = await page.evaluate(async () =>
      (await fetch('/api/teacher/attendance/summary')).json());
    expect(summary.totalClasses).toBeGreaterThanOrEqual(1);
  });

  test('the parent can open the progress page for their child', async ({ page }) => {
    await login(page, school.parentEmail, PW);
    await page.goto('/web/parent/dashboard');
    await expect(page.locator('body')).not.toContainText('Forbidden');
    await expect(page.locator('body')).not.toContainText('Something went wrong');
    // The child must be reachable from the parent's own portal, not just in the DB.
    await expect(page.locator('body')).toContainText('Asha');
  });

  test('a second school sees none of the first schools data', async ({ page }) => {
    const other = await onboardSchool(page, 'beta');

    const roster = await page.evaluate(async () =>
      (await fetch('/api/admin/messages/roster')).json());
    expect(Array.isArray(roster)).toBe(true);
    expect(JSON.stringify(roster)).not.toContain(`Student${school.suffix}`);

    const sections = await page.evaluate(async () =>
      (await fetch('/web/admin/class-sections')).json());
    expect(JSON.stringify(sections)).not.toContain(`Grade-${school.suffix}`);

    // And the first school's admin still cannot see the second school's tenant.
    await page.context().clearCookies();
    await login(page, school.adminEmail, PW);
    const backAgain = await page.evaluate(async () =>
      (await fetch('/api/admin/messages/roster')).json());
    expect(JSON.stringify(backAgain)).not.toContain(other.subdomain);
  });
});
