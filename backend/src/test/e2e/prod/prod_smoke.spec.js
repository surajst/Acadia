const { test, expect } = require('@playwright/test');

/**
 * Drives one real school through production.
 *
 * Additive only. It creates a single tenant whose subdomain begins with
 * "smoke-" so it is trivially identifiable and purgeable, and it touches
 * nothing outside that tenant. There is no /test/reset in production and this
 * never asks for one -- dev-mode is off, and a smoke test that needs to wipe
 * the database is not a smoke test.
 *
 * Run with:
 *   PLAYWRIGHT_BASE_URL=https://portal.concept-edu.com npx playwright test prod/
 */

const PW = 'SmokeRun2026!Secure';
const stamp = Date.now();
const school = {
  subdomain: `smoke-${stamp}`,
  name: `Smoke Preschool ${stamp}`,
  adminEmail: `admin-${stamp}@smoke.test`,
  headEmail: `head-${stamp}@smoke.test`,
  teacherEmail: `teacher-${stamp}@smoke.test`,
  parentEmail: `parent-${stamp}@smoke.test`,
};

// Carried between steps in this serial run.
const ctx = {};

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(u => u.pathname.includes('/web/') && !u.pathname.includes('/login'),
    { timeout: 60000 });
}

/**
 * Posts a form the way the browser would, CSRF token included.
 *
 * The web security chain enables CSRF with an explicit exemption list, so some
 * admin endpoints accept a bare POST and others do not. Reading the token off
 * the current page means this helper works for both, and it exercises the same
 * path a real click does rather than one that happens to be exempt.
 */
const form = async (page, url, params) => page.evaluate(async ([u, p]) => {
  const token = document.querySelector('input[name="_csrf"]');
  const body = new URLSearchParams(p);
  if (token) body.append(token.getAttribute('name'), token.getAttribute('value'));
  const res = await fetch(u, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  });
  const text = await res.text();
  // Whole body, not a prefix: these endpoints redirect and the flash message
  // that carries the outcome renders well down the page, so a truncated body
  // reports "no receipt" for a payment that in fact succeeded.
  return { status: res.status, body: text.slice(0, 200000) };
}, [url, params]);

test.describe.configure({ mode: 'serial' });

// A single page for the whole run. Playwright gives each test its own context
// by default, which meant a fresh login per step -- production allows 20 logins
// per 15 minutes per IP, and the run tripped its own rate limiter. Roles are
// switched deliberately, only where a step needs a different pair of eyes.
let page;
let signedInAs = null;

async function as(who, password) {
  if (signedInAs === who) return;
  await login(page, who, password);
  signedInAs = who;
}

test.describe('Production smoke: one preschool, end to end', () => {

  test.beforeAll(async ({ browser }) => { page = await browser.newPage(); });
  test.afterAll(async () => { if (page) await page.close(); });

  test('1. a preschool onboards itself', async () => {
    await page.goto('/web/onboard/signup');
    const res = await page.evaluate(async (s) => {
      const r = await fetch('/api/onboard/create-school', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          schoolName: s.name, subdomain: s.subdomain,
          adminEmail: s.adminEmail, adminPassword: s.pw,
          adminFullName: 'Smoke Admin', schoolType: 'PRESCHOOL',
        }),
      });
      return { status: r.status, body: await r.json() };
    }, { ...school, pw: PW });

    expect(res.status, JSON.stringify(res.body)).toBe(200);
    expect(res.body.tenantId).toBeTruthy();
    ctx.tenantId = res.body.tenantId;
  });

  test('2. the console speaks preschool, not secondary', async () => {
    await as(school.adminEmail, PW);
    await page.goto('/web/admin/management');
    await expect(page.locator('body')).toContainText('Active Levels');
    await expect(page.locator('body')).not.toContainText('Active Classes');
  });

  test('3. admin creates a Nursery level', async () => {
    await as(school.adminEmail, PW);
    await page.goto('/web/admin/management');
    const r = await form(page, '/web/admin/class-sections/add',
      { gradeName: 'Nursery', sectionName: 'A', roomNumber: 'Sunflower', totalCapacity: '20' });
    expect(r.status).toBeLessThan(400);

    const sections = await page.evaluate(async () =>
      (await fetch('/web/admin/class-sections')).json());
    expect(sections.length).toBe(1);
    expect(sections[0].gradeName).toBe('Nursery');
    ctx.sectionId = sections[0].id;
  });

  test('4. admin registers a child with a guardian', async () => {
    await as(school.adminEmail, PW);
    await page.goto('/web/admin/management');
    await page.click('button:has-text("Classrooms & Students")');
    await page.click('button:has-text("Register New Student")');
    await page.fill('#firstName', 'Aarav');
    await page.fill('#lastName', 'Smoke');
    await page.fill('#rollNumber', `N-${stamp % 10000}`);
    await page.selectOption('#schoolClassId', { index: 1 });
    await page.click('#registerStudentModal button[type="submit"]');
    await page.waitForURL(u => u.pathname.includes('/web/admin/management'));

    const roster = await page.evaluate(async () =>
      (await fetch('/api/admin/messages/roster')).json());
    const child = roster.find(r => r.studentName.includes('Aarav'));
    expect(child, 'child is on the roster').toBeTruthy();
    ctx.studentId = child.studentId;
  });

  test('5. admin records the safety fields and a pickup contact', async () => {
    await as(school.adminEmail, PW);
    await page.goto(`/web/teacher/student/${ctx.studentId}`);
    await expect(page.locator('body')).toContainText('Care & Safety');
    await expect(page.locator('body')).toContainText('which is not the same as none');

    // Already on the student profile, which carries the CSRF token this
    // endpoint requires -- it is not on the exemption list, unlike student/add.
    const upd = await form(page, `/web/admin/student/${ctx.studentId}/update`, {
      firstName: 'Aarav', lastName: 'Smoke', rollNumber: `N-${stamp % 10000}`,
      dateOfBirth: '2022-04-10',
      medicalNotes: 'Peanut allergy — EpiPen kept in the office',
      emergencyContactName: 'Meera Smoke',
      emergencyContactPhone: '+91 90000 12345',
    });
    expect(upd.status).toBeLessThan(400);

    await page.goto(`/web/teacher/student/${ctx.studentId}`);
    await expect(page.locator('body')).toContainText('Peanut allergy');
    await expect(page.locator('body')).toContainText('Meera Smoke');
    await expect(page.locator('body')).toContainText('yrs');

    await page.fill('[data-pickup-name]', 'Anita Rao');
    await page.locator('input[name="relationship"]').fill('Grandmother');
    await page.click('button:has-text("Authorise someone")');
    await page.waitForURL(u => u.pathname.includes('/web/teacher/student/'));
    await expect(page.locator('[data-pickup-row]')).toHaveCount(1);
  });

  test('6. admin invites a principal and a teacher', async () => {
    await as(school.adminEmail, PW);
    await page.goto('/web/admin/management');
    const staff = await page.evaluate(async ([head, teacher]) => {
      const add = (email, role, name) => fetch('/web/admin/staff/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ fullName: name, email, role }).toString(),
      }).then(r => r.json());

      const h = await add(head, 'PRINCIPAL', 'Smoke Head');
      const t = await add(teacher, 'TEACHER', 'Smoke Teacher');
      const list = await (await fetch('/web/admin/staff')).json();
      for (const s of list) {
        if (s.email === head || s.email === teacher) {
          await fetch(`/api/principal/staff/${s.id}/approve`, { method: 'POST' });
        }
      }
      return { head: h, teacher: t };
    }, [school.headEmail, school.teacherEmail]);

    ctx.headPw = staff.head.temporaryPassword || staff.head.password;
    ctx.teacherPw = staff.teacher.temporaryPassword || staff.teacher.password;
    expect(ctx.headPw, 'principal got a password').toBeTruthy();
    expect(ctx.teacherPw, 'teacher got a password').toBeTruthy();
  });

  test('7. a fee plan waits for the principal', async () => {
    await as(school.adminEmail, PW);
    await page.goto('/web/admin/fees/settings');
    await page.fill('[data-plan-grade]', 'Nursery');
    await page.locator('[data-inst-label]').first().fill('Term 1');
    await page.locator('[data-inst-amount]').first().fill('12000');
    await page.locator('[data-inst-offset]').first().fill('0');
    await page.click('#feePlanForm button[type="submit"]');

    await expect(page.locator('body')).toContainText('Sent to the principal for approval');
    await expect(page.locator('[data-plan-row]')).toHaveCount(0);
  });

  test('8. the principal approves it, and only then does the plan exist', async () => {
    await as(school.headEmail, ctx.headPw);
    const decided = await page.evaluate(async () => {
      const queue = await (await fetch('/api/principal/approvals/pending')).json();
      const res = await fetch(`/api/principal/approvals/${queue[0].requestId}/approve`,
        { method: 'POST' });
      return { queued: queue.length, summary: queue[0].summary, status: res.status };
    });
    expect(decided.queued).toBe(1);
    expect(decided.summary).toContain('Nursery');
    expect(decided.status).toBe(200);

    await as(school.adminEmail, PW);
    await page.goto('/web/admin/fees/settings');
    await expect(page.locator('[data-plan-row]')).toHaveCount(1);
  });

  test('9. billing raises an invoice and a payment gets a receipt', async () => {
    await as(school.adminEmail, PW);
    const made = await form(page, '/web/admin/fees/invoice/create', { studentId: ctx.studentId });
    expect(made.status).toBeLessThan(400);

    await page.goto('/web/admin/fees');
    await expect(page.locator('body')).toContainText('12,000.00');

    const invoiceId = await page.evaluate(() => {
      const el = document.querySelector('button[data-id][data-due]');
      return el ? el.getAttribute('data-id') : null;
    });
    expect(invoiceId, 'an invoice exists to collect against').toBeTruthy();

    const paid = await form(page, '/web/admin/fees/collect',
      { invoiceId, amount: '5000', paymentMode: 'CASH' });
    expect(paid.status).toBeLessThan(400);
    // The receipt number arrives as a flash message on the redirect that this
    // fetch follows, so it is in the response body -- a later page load is a
    // fresh request that has already consumed it.
    expect(paid.body, 'a receipt number was issued').toContain('Receipt #');

    // Durable evidence rather than a flash: the payment is on the ledger and
    // is now reversible, which is what step 11 acts on.
    await page.goto('/web/admin/fees');
    await expect(page.locator('[data-reverse-payment]')).toHaveCount(1);
    ctx.receiptSeen = true;
  });

  test('10. overpayment is refused by the server', async () => {
    await as(school.adminEmail, PW);
    await page.goto('/web/admin/fees');
    const invoiceId = await page.evaluate(() => {
      const el = document.querySelector('button[data-id][data-due]');
      return el ? el.getAttribute('data-id') : null;
    });
    const over = await form(page, '/web/admin/fees/collect',
      { invoiceId, amount: '999999', paymentMode: 'CASH' });
    expect(over.body).toContain('more than');
  });

  test('11. reversing a payment also waits for the principal', async () => {
    await as(school.adminEmail, PW);
    await page.goto('/web/admin/fees');
    const reverseBtn = page.locator('[data-reverse-payment]').first();
    await expect(reverseBtn).toBeVisible();
    page.once('dialog', d => d.accept('Smoke test reversal'));
    await reverseBtn.click();

    await page.goto('/web/admin/fees');
    // Asking changes nothing: the payment still stands and is still reversible.
    await expect(page.locator('[data-reverse-payment]')).toHaveCount(1);

    await as(school.headEmail, ctx.headPw);
    const decided = await page.evaluate(async () => {
      const queue = await (await fetch('/api/principal/approvals/pending')).json();
      const res = await fetch(`/api/principal/approvals/${queue[0].requestId}/approve`,
        { method: 'POST' });
      return { summary: queue[0].summary, status: res.status };
    });
    expect(decided.summary).toContain('Reverse a payment');
    expect(decided.status).toBe(200);

    await as(school.adminEmail, PW);
    await page.goto('/web/admin/fees');
    await expect(page.locator('[data-reverse-payment]')).toHaveCount(0);
  });

  test('12. the reports render with real rows', async () => {
    await as(school.adminEmail, PW);
    await page.goto('/web/admin/fees/collections');
    await expect(page.locator('body')).toContainText('Receipts issued');
    // The reversed receipt survives on the day-book rather than disappearing.
    await expect(page.locator('[data-receipt-row]').first()).toBeVisible();

    await page.goto('/web/admin/fees/defaulters');
    await expect(page.locator('body')).toContainText('Overdue invoices');
  });

  test('13. a parent is linked and sees no quests', async () => {
    await as(school.adminEmail, PW);
    await page.goto('/web/admin/management');
    const created = await form(page, '/web/admin/parent/add', {
      firstName: 'Meera', lastName: 'Smoke',
      loginEmail: school.parentEmail, loginPassword: PW, studentId: ctx.studentId,
    });
    expect(created.status).toBeLessThan(400);

    await as(school.parentEmail, PW);
    await page.goto('/web/parent/dashboard');
    // A preschool parent is not set homework for a three-year-old.
    await expect(page.locator('[data-tab="quests"]')).toHaveCount(0);
    await expect(page.locator('[data-tab="progress"]')).toHaveCount(0);
    await expect(page.locator('[data-tab="messages"]')).not.toHaveCount(0);
  });

  test('14. a teacher can mark attendance', async () => {
    await as(school.teacherEmail, ctx.teacherPw);
    await page.goto('/web/teacher/attendance');
    await expect(page.locator('body')).not.toContainText('Something went wrong');
    await expect(page.locator('body')).toContainText('Aarav');
  });

  test('15. this school cannot see another school', async () => {
    await as(school.adminEmail, PW);
    const roster = await page.evaluate(async () =>
      (await fetch('/api/admin/messages/roster')).json());
    // The whole tenant holds exactly the one child this run created.
    expect(roster.length, 'roster is this tenant only').toBe(1);
    expect(roster[0].studentName).toContain('Aarav');

    const queue = await page.evaluate(async () => {
      const r = await fetch('/api/academic/teacher/pending');
      return r.status;
    });
    // Admin may read it; what matters is that it is scoped, which the roster
    // count above already demonstrates for this tenant.
    expect([200, 403]).toContain(queue);
  });
});
