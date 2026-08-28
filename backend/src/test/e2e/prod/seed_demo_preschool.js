/**
 * Seeds one believable preschool into a deployed environment, for a demo.
 *
 * This is not a test. It makes no assertions about behaviour and it is not run
 * by the Playwright runner -- it is a script that drives the real UI and the
 * real endpoints to put a school on screen that looks like somebody has been
 * running it for a term, rather than one created ten minutes ago.
 *
 * ADDITIVE ONLY. It creates a single tenant and touches nothing outside it.
 * There is no reset, no wipe, and no DELETE anywhere in this file. To remove
 * what it creates, use scripts/purge_tenant.sql with the subdomain below.
 *
 * Run:
 *   cd backend/src/test/e2e
 *   PLAYWRIGHT_BASE_URL=https://portal.concept-edu.com node prod/seed_demo_preschool.js
 *
 * Roughly four minutes. It prints the login table at the end -- keep that.
 */

const { chromium } = require('@playwright/test');

const BASE = process.env.PLAYWRIGHT_BASE_URL || 'https://portal.concept-edu.com';

// One password across the demo logins. This is a throwaway tenant on a demo
// URL; the point is that you can read a credential off a slide mid-sentence
// without fumbling, not that it resists attack. Purge the tenant afterwards.
const PW = 'Sunshine2026!Demo';

const SCHOOL = {
  name: 'Sunshine Montessori',
  subdomain: 'sunshine',
  adminEmail: 'admin@sunshine.demo',
  adminName: 'Priya Nair',
};

const PRINCIPAL = { email: 'head@sunshine.demo', name: 'Lakshmi Iyer' };

const TEACHERS = [
  { email: 'anita@sunshine.demo', name: 'Anita Deshpande' },
  { email: 'ravi@sunshine.demo', name: 'Ravi Menon' },
  { email: 'fatima@sunshine.demo', name: 'Fatima Sheikh' },
];

// Four levels, one room each -- a real preschool of this size does not run
// parallel sections, and inventing them would show as padding.
const LEVELS = [
  { grade: 'Pre-Nursery', section: 'A', room: 'Marigold Room', capacity: 12, fee: 28000 },
  { grade: 'Nursery',     section: 'A', room: 'Sunflower Room', capacity: 16, fee: 34000 },
  { grade: 'LKG',         section: 'A', room: 'Bluebell Room',  capacity: 18, fee: 38000 },
  { grade: 'UKG',         section: 'A', room: 'Peepal Room',    capacity: 18, fee: 42000 },
];

/**
 * The roster. Ages are set from the level, so a Pre-Nursery child reads as two
 * and a half and a UKG child as five -- the profile page prints the age, and a
 * five-year-old in Pre-Nursery is the kind of detail that derails a demo.
 *
 * Care notes are deliberately on a minority of children. Every child having an
 * allergy looks like seed data; four out of twenty-two looks like a school.
 */
const CHILDREN = [
  // Pre-Nursery -- born 2023
  { first: 'Aarav',  last: 'Kulkarni', level: 0, dob: '2023-03-14', g: ['Meera', 'Kulkarni', '+91 98200 41122'] },
  { first: 'Zoya',   last: 'Ahmed',    level: 0, dob: '2023-01-27', g: ['Saira', 'Ahmed', '+91 98200 41123'],
    care: 'Peanut allergy. EpiPen kept in the office cabinet, second one in Marigold Room.' },
  { first: 'Vihaan', last: 'Reddy',    level: 0, dob: '2023-05-02', g: ['Divya', 'Reddy', '+91 98200 41124'] },
  { first: 'Anaya',  last: 'Bose',     level: 0, dob: '2023-02-19', g: ['Ishita', 'Bose', '+91 98200 41125'] },
  { first: 'Kabir',  last: 'Grewal',   level: 0, dob: '2023-06-08', g: ['Simran', 'Grewal', '+91 98200 41126'] },

  // Nursery -- born 2022
  { first: 'Myra',   last: 'Pillai',   level: 1, dob: '2022-04-11', g: ['Rekha', 'Pillai', '+91 98200 41127'] },
  { first: 'Arjun',  last: 'Rao',      level: 1, dob: '2022-07-23', g: ['Anita', 'Rao', '+91 98200 41128'] },
  { first: 'Saanvi', last: 'Joshi',    level: 1, dob: '2022-09-30', g: ['Neha', 'Joshi', '+91 98200 41129'],
    care: 'Mild asthma. Inhaler in her bag, name-labelled. Slow down at outdoor play.' },
  { first: 'Reyansh',last: 'Shetty',   level: 1, dob: '2022-03-05', g: ['Pooja', 'Shetty', '+91 98200 41130'] },
  { first: 'Ira',    last: 'Chandra',  level: 1, dob: '2022-11-16', g: ['Kavya', 'Chandra', '+91 98200 41131'] },
  { first: 'Dhruv',  last: 'Malhotra', level: 1, dob: '2022-08-02', g: ['Ritu', 'Malhotra', '+91 98200 41132'] },

  // LKG -- born 2021
  { first: 'Aadhya', last: 'Verma',    level: 2, dob: '2021-05-19', g: ['Sunita', 'Verma', '+91 98200 41133'] },
  { first: 'Ayaan',  last: 'Qureshi',  level: 2, dob: '2021-02-08', g: ['Nadia', 'Qureshi', '+91 98200 41134'] },
  { first: 'Kiara',  last: 'DSouza',   level: 2, dob: '2021-10-24', g: ['Elena', 'DSouza', '+91 98200 41135'],
    care: 'Lactose intolerant. Soy milk sent from home in a blue flask.' },
  { first: 'Advait', last: 'Iyengar',  level: 2, dob: '2021-06-30', g: ['Shruti', 'Iyengar', '+91 98200 41136'] },
  { first: 'Navya',  last: 'Bhatt',    level: 2, dob: '2021-01-12', g: ['Priyanka', 'Bhatt', '+91 98200 41137'] },
  { first: 'Ishaan', last: 'Sengupta', level: 2, dob: '2021-09-07', g: ['Moushumi', 'Sengupta', '+91 98200 41138'] },

  // UKG -- born 2020
  { first: 'Diya',   last: 'Kapoor',   level: 3, dob: '2020-04-21', g: ['Tanya', 'Kapoor', '+91 98200 41139'] },
  { first: 'Vivaan', last: 'Nambiar',  level: 3, dob: '2020-08-15', g: ['Gayatri', 'Nambiar', '+91 98200 41140'] },
  { first: 'Amaira', last: 'Chopra',   level: 3, dob: '2020-12-03', g: ['Sonia', 'Chopra', '+91 98200 41141'],
    care: 'Wears hearing aids. Seat her near the front at Circle Time; check batteries after nap.' },
  { first: 'Rudra',  last: 'Patil',    level: 3, dob: '2020-02-27', g: ['Manasi', 'Patil', '+91 98200 41142'] },
  { first: 'Anvi',   last: 'Krishnan', level: 3, dob: '2020-11-09', g: ['Lata', 'Krishnan', '+91 98200 41143'] },
];

// People other than the parent who may collect a child. A short list, on the
// children whose guardians would realistically need it.
const PICKUPS = [
  { child: 'Aarav',  name: 'Sushila Kulkarni', rel: 'Grandmother', phone: '+91 98200 55001' },
  { child: 'Zoya',   name: 'Imran Ahmed',      rel: 'Uncle',       phone: '+91 98200 55002' },
  { child: 'Diya',   name: 'Ramesh Kapoor',    rel: 'Grandfather', phone: '+91 98200 55003' },
  { child: 'Myra',   name: 'Sheela Menon',     rel: 'Nanny',       phone: '+91 98200 55004' },
];

// The parent who gets a real login, so the parent view can be shown live.
const DEMO_PARENT = { first: 'Meera', last: 'Kulkarni', email: 'meera@sunshine.demo', child: 'Aarav' };

// ---------------------------------------------------------------------------

const ctx = { sections: {}, students: {}, headPw: null, teacherPw: {} };
let page;
let signedInAs = null;

const log = (msg) => console.log(`  ${msg}`);
const step = (n, msg) => console.log(`\n[${n}] ${msg}`);

/**
 * Production allows 20 logins per 15 minutes per IP, and an earlier run of the
 * smoke suite tripped that limiter by logging in once per step. One page, and
 * a role switch only where the work genuinely needs different eyes.
 */
async function as(who, password) {
  if (signedInAs === who) return;
  await page.goto(`${BASE}/login`);
  await page.fill('#username', who);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(u => u.pathname.includes('/web/') && !u.pathname.includes('/login'),
    { timeout: 60000 });
  signedInAs = who;
  log(`signed in as ${who}`);
}

/** Posts a form the way the browser would, carrying the CSRF token if the page has one. */
const form = (url, params) => page.evaluate(async ([u, p]) => {
  const token = document.querySelector('input[name="_csrf"]');
  const body = new URLSearchParams(p);
  if (token) body.append(token.getAttribute('name'), token.getAttribute('value'));
  const res = await fetch(u, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  });
  return { status: res.status, body: (await res.text()).slice(0, 200000) };
}, [url, params]);

const getJson = (url) => page.evaluate(u => fetch(u).then(r => r.json()), url);

function must(res, what) {
  if (res.status >= 400) throw new Error(`${what} failed: HTTP ${res.status}`);
  return res;
}

async function main() {
  console.log(`Seeding "${SCHOOL.name}" into ${BASE}`);
  console.log('Additive only -- nothing outside this tenant is touched.\n');

  const browser = await chromium.launch();
  page = await browser.newPage();

  try {
    // -- 1. The school onboards itself -------------------------------------
    step(1, 'Creating the school');
    await page.goto(`${BASE}/web/onboard/signup`);
    const created = await page.evaluate(async (s) => {
      const r = await fetch('/api/onboard/create-school', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          schoolName: s.name, subdomain: s.subdomain,
          adminEmail: s.adminEmail, adminPassword: s.pw,
          adminFullName: s.adminName, schoolType: 'PRESCHOOL',
        }),
      });
      return { status: r.status, body: await r.json() };
    }, { ...SCHOOL, pw: PW });

    if (created.status !== 200) {
      throw new Error(`Could not create the school: ${JSON.stringify(created.body)}\n` +
        `If the subdomain "${SCHOOL.subdomain}" is taken, change SCHOOL.subdomain and re-run.`);
    }
    log(`tenant ${created.body.tenantId}`);

    // -- 2. Levels ----------------------------------------------------------
    step(2, 'Opening the four levels');
    await as(SCHOOL.adminEmail, PW);
    await page.goto(`${BASE}/web/admin/management`);
    for (const l of LEVELS) {
      must(await form('/web/admin/class-sections/add', {
        gradeName: l.grade, sectionName: l.section,
        roomNumber: l.room, totalCapacity: String(l.capacity),
      }), `create ${l.grade}`);
      log(`${l.grade} -- ${l.room}`);
    }
    for (const s of await getJson('/web/admin/class-sections')) {
      ctx.sections[s.gradeName] = s.id;
    }

    // -- 3. Children, each with the guardian named on the form --------------
    step(3, `Enrolling ${CHILDREN.length} children`);
    let roll = 1;
    for (const c of CHILDREN) {
      const level = LEVELS[c.level];
      must(await form('/web/admin/student/add', {
        firstName: c.first, lastName: c.last,
        rollNumber: `SM-${String(roll++).padStart(3, '0')}`,
        schoolClassId: ctx.sections[level.grade],
        guardianFirstName: c.g[0], guardianLastName: c.g[1], guardianPhone: c.g[2],
      }), `enrol ${c.first}`);
    }
    for (const r of await getJson('/api/admin/messages/roster')) {
      ctx.students[r.studentName.split(' ')[0]] = r.studentId;
    }
    log(`${Object.keys(ctx.students).length} on the roster`);

    // -- 4. Dates of birth, and care notes where they apply -----------------
    step(4, 'Recording dates of birth and care notes');
    let roll2 = 1;
    for (const c of CHILDREN) {
      const id = ctx.students[c.first];
      const rollNumber = `SM-${String(roll2++).padStart(3, '0')}`;
      if (!id) { log(`! no id for ${c.first}, skipped`); continue; }
      // The profile page carries the CSRF token this endpoint requires.
      await page.goto(`${BASE}/web/teacher/student/${id}`);
      must(await form(`/web/admin/student/${id}/update`, {
        firstName: c.first, lastName: c.last, rollNumber,
        dateOfBirth: c.dob,
        medicalNotes: c.care || '',
        emergencyContactName: `${c.g[0]} ${c.g[1]}`,
        emergencyContactPhone: c.g[2],
      }), `update ${c.first}`);
    }
    log(`${CHILDREN.filter(c => c.care).length} children have care notes`);

    // -- 5. Authorised pickup ----------------------------------------------
    step(5, 'Authorising pickup contacts');
    for (const p of PICKUPS) {
      const id = ctx.students[p.child];
      if (!id) continue;
      await page.goto(`${BASE}/web/teacher/student/${id}`);
      must(await form(`/web/admin/student/${id}/pickup/add`,
        { name: p.name, relationship: p.rel, phone: p.phone }), `pickup for ${p.child}`);
      log(`${p.name} (${p.rel}) may collect ${p.child}`);
    }

    // -- 6. Staff -----------------------------------------------------------
    step(6, 'Adding staff');
    await page.goto(`${BASE}/web/admin/management`);
    const staff = await page.evaluate(async ([head, teachers]) => {
      const add = (email, role, name) => fetch('/web/admin/staff/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ fullName: name, email, role }).toString(),
      }).then(r => r.json());

      const out = { head: await add(head.email, 'PRINCIPAL', head.name), teachers: {} };
      for (const t of teachers) out.teachers[t.email] = await add(t.email, 'TEACHER', t.name);

      // Staff accounts start unapproved; a principal signs them off.
      for (const s of await (await fetch('/web/admin/staff')).json()) {
        await fetch(`/api/principal/staff/${s.id}/approve`, { method: 'POST' });
      }
      return out;
    }, [PRINCIPAL, TEACHERS]);

    ctx.headPw = staff.head.temporaryPassword || staff.head.password;
    for (const t of TEACHERS) {
      const s = staff.teachers[t.email];
      ctx.teacherPw[t.email] = s.temporaryPassword || s.password;
    }
    log(`${PRINCIPAL.name} (principal) and ${TEACHERS.length} teachers, all approved`);

    // -- 7. Fee plans, which need the principal ----------------------------
    step(7, 'Proposing a fee plan per level');
    for (const l of LEVELS) {
      await page.goto(`${BASE}/web/admin/fees/settings`);
      await page.fill('[data-plan-grade]', l.grade);
      const labels = ['Term 1', 'Term 2', 'Term 3'];
      const share = Math.round(l.fee / 3);
      // The form starts with fewer rows than a three-term year needs, and the
      // rows are built by script rather than rendered -- so ask for them the
      // way the page does, then wait until they are actually in the DOM.
      while (await page.locator('[data-inst-label]').count() < labels.length) {
        await page.click('[data-add-instalment]');
      }
      await page.locator('[data-inst-label]').nth(labels.length - 1).waitFor();
      for (let i = 0; i < 3; i++) {
        await page.locator('[data-inst-label]').nth(i).fill(labels[i]);
        await page.locator('[data-inst-amount]').nth(i).fill(String(share));
        await page.locator('[data-inst-offset]').nth(i).fill(String(i * 120));
      }
      await page.click('#feePlanForm button[type="submit"]');
      await page.waitForLoadState('networkidle');
      log(`${l.grade}: 3 x Rs ${share.toLocaleString('en-IN')} -- sent for approval`);
    }

    step(8, 'The principal approves them');
    await as(PRINCIPAL.email, ctx.headPw);
    const approved = await page.evaluate(async () => {
      const queue = await (await fetch('/api/principal/approvals/pending')).json();
      let n = 0;
      for (const q of queue) {
        const r = await fetch(`/api/principal/approvals/${q.requestId}/approve`, { method: 'POST' });
        if (r.status === 200) n++;
      }
      return { queued: queue.length, approved: n };
    });
    log(`${approved.approved} of ${approved.queued} approved`);

    // -- 9. Billing ---------------------------------------------------------
    step(9, 'Raising invoices');
    await as(SCHOOL.adminEmail, PW);
    for (const c of CHILDREN) {
      const id = ctx.students[c.first];
      if (!id) continue;
      await form('/web/admin/fees/invoice/create', { studentId: id });
    }
    await page.goto(`${BASE}/web/admin/fees`);
    log('invoices raised for the whole roster');

    // -- 10. Payments -------------------------------------------------------
    // One invoice per child per instalment, so the roster of 22 becomes 66
    // rows across four pages -- collecting only what the first page happens to
    // show would leave fifty untouched and a defaulters list the length of the
    // school.
    //
    // Payment follows the term rather than a flat percentage, because that is
    // how a school actually looks partway through a year: Term 1 is behind you
    // and nearly settled, Term 2 is in progress, Term 3 is barely begun. That
    // gives the collections report real receipts and the defaulters list a
    // short, plausible set of genuinely overdue names.
    step(10, 'Collecting some of it');
    const invoices = [];
    for (let p = 0; ; p++) {
      await page.goto(`${BASE}/web/admin/fees?page=${p}`);
      const got = await page.evaluate(() =>
        Array.from(document.querySelectorAll('button[data-id][data-due]')).map(el => {
          const row = el.closest('tr');
          const text = row ? row.innerText : '';
          const term = (text.match(/Term\s*\d/) || ['Term 1'])[0].replace(/\s+/g, ' ');
          return { id: el.getAttribute('data-id'), due: parseFloat(el.getAttribute('data-due')), term };
        }));
      if (!got.length) break;
      invoices.push(...got);
      const more = await page.evaluate(p2 =>
        !!document.querySelector(`a[href*="page=${p2 + 1}"]`), p);
      if (!more) break;
    }
    log(`${invoices.length} invoices across the year`);

    // full / partial / leave alone, per term.
    const POLICY = { 'Term 1': [0.85, 0.10], 'Term 2': [0.50, 0.25], 'Term 3': [0.10, 0.10] };
    const modes = ['CASH', 'UPI', 'BANK_TRANSFER', 'CHEQUE'];
    let paidFull = 0, paidPart = 0, unpaid = 0;
    const seen = {};

    await page.goto(`${BASE}/web/admin/fees`);
    for (let i = 0; i < invoices.length; i++) {
      const inv = invoices[i];
      if (!inv.due || inv.due <= 0) { unpaid++; continue; }
      const [fullTo, partTo] = POLICY[inv.term] || POLICY['Term 3'];
      // Deterministic within a term, so a re-run tells the same story.
      const n = (seen[inv.term] = (seen[inv.term] || 0) + 1);
      const r = ((n * 7) % 20) / 20;

      let amount;
      if (r < fullTo) { amount = inv.due; paidFull++; }
      else if (r < fullTo + partTo) { amount = Math.round(inv.due / 2); paidPart++; }
      else { unpaid++; continue; }

      const res = await form('/web/admin/fees/collect',
        { invoiceId: inv.id, amount: String(amount), paymentMode: modes[i % modes.length] });
      if (res.status >= 400) log(`! payment rejected for invoice ${inv.id} (HTTP ${res.status})`);
    }
    log(`${paidFull} settled, ${paidPart} part-paid, ${unpaid} left outstanding`);

    // -- 11. A parent who can log in ---------------------------------------
    step(11, 'Giving one parent a login');
    await page.goto(`${BASE}/web/admin/management`);
    must(await form('/web/admin/parent/add', {
      firstName: DEMO_PARENT.first, lastName: DEMO_PARENT.last,
      loginEmail: DEMO_PARENT.email, loginPassword: PW,
      studentId: ctx.students[DEMO_PARENT.child],
    }), 'create parent login');
    log(`${DEMO_PARENT.first} ${DEMO_PARENT.last}, linked to ${DEMO_PARENT.child}`);

    // -- 12. Today's attendance --------------------------------------------
    // Only today. There is no backdating endpoint, and inventing one for a
    // demo would mean shipping a way to rewrite the attendance record.
    step(12, "Marking today's attendance");
    // The page opens on one room, so marking it once leaves the other three
    // blank -- and a demo that opens on LKG should not find an empty register.
    const t0 = TEACHERS[0];
    await as(t0.email, ctx.teacherPw[t0.email]);
    let totalMarked = 0;
    for (const l of LEVELS) {
      const classId = ctx.sections[l.grade];
      if (!classId) continue;
      await page.goto(`${BASE}/web/teacher/attendance?classId=${classId}`);
      const marked = await page.evaluate(async (cid) => {
        const ids = Array.from(document.querySelectorAll('input[name="studentIds"]'));
        if (!ids.length) return 0;
        const token = document.querySelector('input[name="_csrf"]');
        const body = new URLSearchParams();
        ids.forEach((el, i) => {
          body.append('studentIds', el.value);
          // One away per room. A full house in every room every day reads as fake.
          body.append('statuses', i === 2 ? 'ABSENT' : 'PRESENT');
        });
        body.append('classId', cid);
        if (token) body.append(token.getAttribute('name'), token.getAttribute('value'));
        await fetch('/web/teacher/attendance/submit', {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: body.toString(),
        });
        return ids.length;
      }, classId);
      totalMarked += marked;
      log(marked ? `${l.grade}: ${marked} marked` : `! ${l.grade}: no register rendered`);
    }
    log(`${totalMarked} children marked in total`);

    // -- Done ---------------------------------------------------------------
    console.log(`\n${'='.repeat(64)}\nReady. ${BASE}/login\n${'='.repeat(64)}`);
    console.log(`\n  Admin      ${SCHOOL.adminEmail}  /  ${PW}`);
    console.log(`  Principal  ${PRINCIPAL.email}  /  ${ctx.headPw}`);
    for (const t of TEACHERS) console.log(`  Teacher    ${t.email}  /  ${ctx.teacherPw[t.email]}`);
    console.log(`  Parent     ${DEMO_PARENT.email}  /  ${PW}`);
    console.log(`\n  Tenant subdomain: ${SCHOOL.subdomain}`);
    console.log(`  To remove afterwards, purge that subdomain with scripts/purge_tenant.sql\n`);
  } finally {
    await browser.close();
  }
}

main().catch(e => { console.error(`\nFAILED: ${e.message}`); process.exit(1); });
