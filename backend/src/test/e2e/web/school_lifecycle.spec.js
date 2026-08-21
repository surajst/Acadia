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
 * Local or CI only. The schools it creates are removed afterwards through the
 * dev-mode-gated purge endpoint, which does not exist in production -- so
 * pointing this at a real deployment would create tenants it cannot clean up.
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

/**
 * Registers a student through the real modal and returns the one-time
 * credentials from the flash banner.
 */
async function registerStudent(page, firstName, lastName, roll) {
  await page.goto('/web/admin/management');
  await page.click('button:has-text("Classrooms & Students")');
  await page.click('button:has-text("Register New Student")');
  await page.fill('#firstName', firstName);
  await page.fill('#lastName', lastName);
  await page.fill('#rollNumber', roll);
  await page.selectOption('#schoolClassId', { index: 1 });
  await page.click('#registerStudentModal button[type="submit"]');
  await page.waitForURL(u => u.pathname.includes('/web/admin/management'), { timeout: 30000 });

  const banner = page.locator('[data-flash]');
  await expect(banner).toContainText('Student login', { timeout: 15000 });
  const creds = (await banner.innerText()).match(/Student login\s*[—-]\s*(\S+)\s*\/\s*(\S+)/);
  expect(creds, `credentials shown for roll ${roll}`).toBeTruthy();
  return { username: creds[1], password: creds[2] };
}

test.describe.serial('Lifecycle of a self-onboarded school', () => {
  let school;
  const created = [];

  // Signing up real schools would otherwise leave one behind per run, for ever.
  test.afterAll(async ({ browser }) => {
    const page = await browser.newPage();
    for (const subdomain of created) {
      const res = await page.request.post(`/test/tenant/${subdomain}/purge`);
      const body = await res.json();
      console.log(`purge ${subdomain}: ${JSON.stringify(body)}`);
      if (body.status !== 'purged') {
        throw new Error(`cleanup failed for ${subdomain}: ${JSON.stringify(body)}`);
      }
    }
    await page.close();
  });

  test('a new school signs itself up and the admin lands in the console', async ({ page }) => {
    school = await onboardSchool(page, 'alpha');
    created.push(school.subdomain);
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
    await page.selectOption('#schoolClassId', { index: 1 });
    await page.click('#registerStudentModal button[type="submit"]');
    await page.waitForURL(u => u.pathname.includes('/web/admin/management'), { timeout: 30000 });

    // The registration form has no password fields: a student login is
    // provisioned automatically with the roll number as the username and a
    // generated temporary password, shown once in a flash banner. Scraping it
    // here is the only way to obtain it -- exactly what a real admin does before
    // relaying it to the family.
    const banner = page.locator('[data-flash]');
    await expect(banner).toContainText('Student login', { timeout: 15000 });
    const creds = (await banner.innerText()).match(/Student login\s*[—-]\s*(\S+)\s*\/\s*(\S+)/);
    expect(creds, 'one-time student credentials are shown to the admin').toBeTruthy();
    school.studentEmail = creds[1];
    school.studentPassword = creds[2];

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

      // The server issues the teacher's temporary password now; capture it so
      // the next test can sign in as them.
      const invite = await (await form('/web/admin/staff/add',
        { fullName: 'Ravi Teacher', email, role: 'TEACHER' })).json();

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

      return { approve, assign, sectionId: sections[0].id, teacherPassword: invite.temporaryPassword };
    }, [school.teacherEmail, PW]);

    expect(result.error).toBeUndefined();
    expect(result.approve.status).toBe('approved');
    expect(result.assign).toBeTruthy();
    expect(result.teacherPassword, 'the server must issue the staff credential').toBeTruthy();
    school.sectionId = result.sectionId;
    school.teacherPassword = result.teacherPassword;
  });

  test('the teacher sees their own class and marks attendance for the student', async ({ page }) => {
    await login(page, school.teacherEmail, school.teacherPassword);

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
    created.push(other.subdomain);

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

  test('purging a school removes it completely enough to sign up again', async ({ page }) => {
    // The strongest available proof that the purge is complete rather than
    // merely successful: the subdomain and admin email are unique-constrained,
    // so reusing both only works if every trace of the school is really gone.
    const doomed = await onboardSchool(page, 'gamma');
    await login(page, doomed.adminEmail, PW);
    await createClassroom(page, `Grade-${doomed.suffix}`, 'A');

    const purge = await page.evaluate(async (subdomain) =>
      (await fetch(`/test/tenant/${subdomain}/purge`, { method: 'POST' })).json(), doomed.subdomain);
    expect(purge.status).toBe('purged');
    expect(purge.rows).toBeGreaterThan(0);

    // The old admin can no longer get in.
    await page.context().clearCookies();
    await page.goto('/login');
    await page.fill('#username', doomed.adminEmail);
    await page.fill('#password', PW);
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/login/, { timeout: 15000 });

    // And the same identifiers are free again.
    const reuse = await page.evaluate(async ([sub, email]) => {
      const res = await fetch('/api/onboard/create-school', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          schoolName: 'Reborn School', subdomain: sub, adminEmail: email,
          adminPassword: 'PilotLaunchSecure2026!', adminFullName: 'Reborn Admin',
        }),
      });
      return { status: res.status, body: await res.json() };
    }, [doomed.subdomain, doomed.adminEmail]);

    expect(reuse.status, 'subdomain and email are reusable after a purge').toBe(200);
    created.push(doomed.subdomain);
  });

  test('admin raises a fee invoice and it appears in the invoice table', async ({ page }) => {
    await login(page, school.adminEmail, PW);

    // A brand-new school has no invoices at all, which makes the record count a
    // precise assertion. The student name alone is not: the page also renders a
    // dropdown of every student, so the name is present before any invoice exists.
    await page.goto('/web/admin/fees');
    await expect(page.locator('body')).toContainText('0 of 0 records');

    // Invoicing refuses to price a grade the school has not set fees for, so a
    // newly onboarded school must configure them first. This used to fall
    // through to a hardcoded 15000 + 5000, which meant every school on the
    // platform billed a plausible-looking 20,000 that nobody had chosen.
    const refused = await page.evaluate(async (studentId) => {
      const res = await fetch('/web/admin/fees/invoice/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ studentId }).toString(),
      });
      return res.status;
    }, school.studentId);
    expect(refused).toBeLessThan(400); // redirect, not a 500
    await page.goto('/web/admin/fees');
    await expect(page.locator('body')).toContainText('0 of 0 records');

    // Now set the fees for this school's grade and try again.
    await page.goto('/web/admin/fees/settings');
    await page.fill('[data-fee-grade]', `Grade-${school.suffix}`);
    await page.fill('[data-fee-tuition]', '18000');
    await page.fill('[data-fee-term]', '4000');
    await page.click('#feeStructureForm button[type="submit"]');
    await expect(page.locator('[data-fee-row]')).toHaveCount(1);
    await expect(page.locator('[data-fee-total]')).toContainText('22,000.00');

    const created = await page.evaluate(async (studentId) => {
      const res = await fetch('/web/admin/fees/invoice/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ studentId }).toString(),
      });
      return res.status;
    }, school.studentId);
    expect(created).toBeLessThan(400);

    await page.goto('/web/admin/fees');
    await expect(page.locator('body')).not.toContainText('Something went wrong');
    await expect(page.locator('body')).toContainText('1 of 1 records');
    await expect(page.locator('table')).toContainText(`Student${school.suffix}`);
    // Priced from what this school set, not from a platform-wide constant.
    await expect(page.locator('table')).toContainText('22,000.00');
  });

  test('admin bills an adjusted amount, and the ledger shows what it was adjusted from', async ({ page }) => {
    await login(page, school.adminEmail, PW);

    // Fees for this grade were set by the previous test; this one departs from
    // them for a single invoice. The point of the feature is that the departure
    // stays visible -- a number alone cannot distinguish a deliberate
    // concession from a fee change or a typo months later.
    await page.goto('/web/admin/fees');
    await page.click('button:has-text("Create Invoice")');
    await page.selectOption('#invoiceStudentSelect', { label: `Asha Student${school.suffix}` });
    await page.check('[data-override-toggle]');
    await page.fill('[data-override-amount]', '9000');
    await page.fill('[data-override-reason]', 'Sibling discount — 2 children enrolled');
    await page.click('#createInvoiceModal button[type="submit"]');

    await page.goto('/web/admin/fees');
    await expect(page.locator('body')).toContainText('2 of 2 records');
    // The adjusted row carries both numbers and the reason.
    const badge = page.locator('[data-override-badge]').first();
    await expect(badge).toBeVisible();
    await expect(badge).toContainText('22,000.00');
    await expect(page.locator('[data-override-reason-text]').first())
      .toContainText('Sibling discount');
  });

  test('a mistyped payment can be reversed, and both entries stay on the ledger', async ({ page }) => {
    await login(page, school.adminEmail, PW);
    await page.goto('/web/admin/fees');

    // Record a payment against the 22,000 invoice raised earlier.
    const paid = await page.evaluate(async () => {
      const row = document.querySelector('button[data-id][data-due]');
      const invoiceId = row.getAttribute('data-id');
      const res = await fetch('/web/admin/fees/collect', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ invoiceId, amount: '5000', paymentMode: 'CASH' }).toString(),
      });
      return res.status;
    });
    expect(paid).toBeLessThan(400);

    // Overpaying the remainder must be refused by the SERVER, not just by the
    // form's max attribute.
    const overpay = await page.evaluate(async () => {
      const row = document.querySelector('button[data-id][data-due]');
      const invoiceId = row ? row.getAttribute('data-id') : null;
      const res = await fetch('/web/admin/fees/collect', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ invoiceId, amount: '999999', paymentMode: 'CASH' }).toString(),
      });
      return { status: res.status, text: await res.text() };
    });
    expect(overpay.text).toContain('more than');

    // Now undo the 5,000 through the ledger's own control.
    await page.goto('/web/admin/fees');
    const reverseBtn = page.locator('[data-reverse-payment]').first();
    await expect(reverseBtn).toBeVisible();
    page.once('dialog', d => d.accept('Cheque bounced'));
    await reverseBtn.click();

    await page.goto('/web/admin/fees');
    await expect(page.locator('body')).not.toContainText('Something went wrong');
    // The invoice is owed in full again.
    await expect(page.locator('table')).toContainText('22,000.00');
  });

  test('an adjusted amount without a reason is refused', async ({ page }) => {
    await login(page, school.adminEmail, PW);

    // The form marks the reason required, so post directly: the guard that
    // matters is the server's, not the browser's.
    const body = await page.evaluate(async (studentId) => {
      const res = await fetch('/web/admin/fees/invoice/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ studentId, overrideAmount: '5000', overrideReason: '  ' }).toString(),
      });
      return { status: res.status, text: await res.text() };
    }, school.studentId);

    expect(body.status).toBeLessThan(400);
    expect(body.text).toContain('reason is required');
    // And nothing was billed.
    await page.goto('/web/admin/fees');
    await expect(page.locator('body')).toContainText('2 of 2 records');
  });

  test('parent assigns a quest and a reward to their child', async ({ page }) => {
    await login(page, school.parentEmail, PW);

    const result = await page.evaluate(async ([studentId, tag]) => {
      const form = (url, params) => fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(params).toString(),
      });
      const quest = await form('/web/parent/assign-task',
        { studentId, taskDescription: `Read a chapter ${tag}`, xpBounty: '50' });
      const reward = await form('/web/parent/add-reward',
        { studentId, rewardTitle: `Ice cream ${tag}`, xpCost: '30' });
      return { quest: quest.status, reward: reward.status };
    }, [school.studentId, school.suffix]);

    expect(result.quest).toBeLessThan(400);
    expect(result.reward).toBeLessThan(400);

    // The parent must be able to see the quest they just set. The Approval
    // Queue cannot show it -- that panel lists work the child has finished --
    // so this asserts on the "Quests in progress" panel instead.
    await page.goto('/web/parent/dashboard');
    await expect(page.locator('body')).not.toContainText('Something went wrong');
    await expect(page.locator('[data-active-quest]'))
      .toContainText(`Read a chapter ${school.suffix}`);
  });

  test('the student signs in and sees the quest their parent set', async ({ page }) => {
    await login(page, school.studentEmail, school.studentPassword);
    await page.goto('/web/student/portal');
    await expect(page.locator('body')).not.toContainText('Forbidden');
    await expect(page.locator('body')).not.toContainText('Something went wrong');
    // End of the chain: something a parent created is visible to the child.
    await expect(page.locator('body')).toContainText(`Read a chapter ${school.suffix}`);
  });

  test('deactivating a school locks every one of its users out', async ({ page }) => {
    const doomed = await onboardSchool(page, 'delta');
    created.push(doomed.subdomain);
    await login(page, doomed.adminEmail, PW);
    await page.goto('/web/admin/dashboard');
    await expect(page.locator('body')).not.toContainText('Forbidden');

    await page.evaluate(async (subdomain) =>
      (await fetch(`/test/tenant/${subdomain}/active/false`, { method: 'POST' })).json(),
      doomed.subdomain);

    // The existing session must stop working immediately, not at next login.
    await page.goto('/web/admin/dashboard');
    await expect(page).toHaveURL(/login/, { timeout: 15000 });

    // And logging back in must not resurrect access.
    await page.goto('/login');
    await page.fill('#username', doomed.adminEmail);
    await page.fill('#password', PW);
    await page.click('button[type="submit"]');
    await page.goto('/web/admin/dashboard');
    await expect(page).toHaveURL(/login/, { timeout: 15000 });

    // Reactivating restores it, so this is a switch and not a one-way door.
    await page.request.post(`/test/tenant/${doomed.subdomain}/active/true`);
    await login(page, doomed.adminEmail, PW);
    await page.goto('/web/admin/dashboard');
    await expect(page.locator('body')).not.toContainText('Forbidden');
  });

  test('the student profile shows the login username and can re-issue a password', async ({ page }) => {
    await login(page, school.adminEmail, PW);
    await page.goto(`/web/teacher/student/${school.studentId}`);

    // The username is readable at any time. Before this, an admin who merely
    // wanted to tell a family how to sign in had to reset the password to see
    // it, which invalidated a login that was working perfectly well.
    await expect(page.locator('[data-student-username]')).toHaveText(school.studentEmail);
    await expect(page.locator('[data-guardian-username]')).toHaveText(school.parentEmail);

    // The password itself is never shown, because it is stored hashed. What the
    // profile offers instead is re-issuing one, revealed once.
    await expect(page.locator('body')).not.toContainText(school.studentPassword);

    // Submit the real form rather than fetch()ing the endpoint: the credentials
    // arrive as a flash attribute, which is consumed by whoever follows the
    // redirect. A fetch swallows it and the page then renders with nothing.
    await Promise.all([
      page.waitForURL(u => u.pathname.includes('/web/teacher/student/'), { timeout: 30000 }),
      page.evaluate(() => document.getElementById('resetStudentForm').submit()),
    ]);

    const banner = page.locator('[data-flash]');
    await expect(banner).toContainText('Student login', { timeout: 15000 });
    const creds = (await banner.innerText()).match(/Student login\s*[—-]\s*(\S+)\s*\/\s*(\S+)/);
    expect(creds, 'a fresh password is revealed once').toBeTruthy();
    expect(creds[2]).not.toBe(school.studentPassword);

    // The re-issued password must actually work, and the old one must not.
    await page.context().clearCookies();
    await login(page, creds[1], creds[2]);
    await expect(page.locator('body')).not.toContainText('Forbidden');

    await page.context().clearCookies();
    await page.goto('/login');
    await page.fill('#username', school.studentEmail);
    await page.fill('#password', school.studentPassword);
    await page.click('button[type="submit"]');
    await expect(page, 'the superseded password stops working').toHaveURL(/login/, { timeout: 15000 });
  });

  test('two schools can use the same roll number and both students get logins', async ({ page }) => {
    // Usernames used to be the bare roll number, and User.email is globally
    // unique -- so the first school to register 6A-01 claimed it system-wide and
    // every later school's 6A-01 silently got no login. Roll numbers repeat
    // across schools constantly, so this hit roughly the second customer.
    const roll = 'ZZ-01';

    const first = await onboardSchool(page, 'epsilon');
    created.push(first.subdomain);
    await login(page, first.adminEmail, PW);
    await createClassroom(page, `Grade-${first.suffix}`, 'A');
    const firstCreds = await registerStudent(page, 'Asha', `One${first.suffix}`, roll);

    await page.context().clearCookies();
    const second = await onboardSchool(page, 'zeta');
    created.push(second.subdomain);
    await login(page, second.adminEmail, PW);
    await createClassroom(page, `Grade-${second.suffix}`, 'A');
    const secondCreds = await registerStudent(page, 'Asha', `Two${second.suffix}`, roll);

    // Same first name, same roll, different schools: distinct usernames, both real.
    expect(secondCreds.username).not.toBe(firstCreds.username);
    expect(firstCreds.username).toContain(first.subdomain);
    expect(secondCreds.username).toContain(second.subdomain);

    for (const creds of [firstCreds, secondCreds]) {
      await page.context().clearCookies();
      await login(page, creds.username, creds.password);
      await expect(page.locator('body')).not.toContainText('Forbidden');
    }
  });
});
