// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * The row actions added to the Staff Registry and the Rewards Marketplace.
 *
 * These are fetch calls carrying a CSRF token, and nothing but a human had
 * clicked them: the QA pass recorded Approve, the credentials CSV and the
 * reward controls as "still untested". Two of them are the only way to recover
 * an account, so they are worth a test that actually presses the button.
 *
 * Approve needs a pending account, and nothing in the product creates one any
 * more -- V17 released every stuck row and an invite now lands APPROVED. The
 * dev-mode-only /test/staff/{email}/pending fixture exists for that, and only
 * ever moves an account INTO pending: a hook that could approve would be a
 * hook that bypasses the thing under test.
 */

async function loginAsAdmin(page) {
  await page.goto('/login');
  await page.fill('#username', 'admin@greenwood.com');
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });
  await page.waitForLoadState('networkidle');
}

/** Opens the Staff Registry section of the management hub. */
async function openStaffSection(page) {
  await page.goto('/web/admin/management');
  await page.waitForLoadState('networkidle');
  await page.locator('#hubGrid').getByText('Staff Registry', { exact: true }).first().click();
  await expect(page.locator('#sec-staff')).toBeVisible();
  // The table is populated by fetch, so wait for a row rather than the section.
  await expect(page.locator('#staffTableBody tr').first()).toBeVisible({ timeout: 30000 });
}

async function inviteTeacher(page, email) {
  const result = await page.evaluate(async (e) => {
    const params = new URLSearchParams({ fullName: 'Playwright Teacher', email: e, role: 'TEACHER' });
    const res = await fetch('/web/admin/staff/add', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params.toString(),
    });
    return res.json();
  }, email);
  expect(result.status, `invite should succeed: ${JSON.stringify(result)}`).toBe('created');
  return result;
}

test.describe('Staff Registry row actions', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsAdmin(page);
  });

  test('an invited teacher shows as Active with no Approve button', async ({ page }) => {
    const email = `pw-active-${Date.now()}@greenwood.com`;
    await inviteTeacher(page, email);
    await openStaffSection(page);

    const row = page.locator('#staffTableBody tr', { hasText: email });
    await expect(row).toContainText('Active');
    // Approve is offered only where it means something. An invite is already
    // approved, so offering it here would imply the account is not usable.
    await expect(row.locator('[data-staff-approve]')).toHaveCount(0);
  });

  test('a pending teacher can be approved from the registry', async ({ page }) => {
    const email = `pw-pending-${Date.now()}@greenwood.com`;
    await inviteTeacher(page, email);

    // Put it back into the state that used to leave teachers stuck.
    const fixture = await page.evaluate(async (e) => {
      const res = await fetch(`/test/staff/${encodeURIComponent(e)}/pending`, { method: 'POST' });
      return res.json();
    }, email);
    expect(fixture.status).toBe('pending');

    await openStaffSection(page);
    const row = page.locator('#staffTableBody tr', { hasText: email });
    await expect(row).toContainText('Pending approval');

    await row.locator('[data-staff-approve]').click();
    await page.waitForLoadState('networkidle');

    // The registry reloads itself, and the row must change state.
    await expect(page.locator('#staffTableBody tr', { hasText: email })).toContainText('Active', { timeout: 30000 });
    await expect(page.locator('#staffAddSuccess')).toContainText('can sign in now');
  });

  test('resetting a password shows the new one exactly once', async ({ page }) => {
    const email = `pw-reset-${Date.now()}@greenwood.com`;
    const invited = await inviteTeacher(page, email);
    await openStaffSection(page);

    // The confirm dialog is a native confirm(); accept it.
    page.once('dialog', d => d.accept());
    await page.locator('#staffTableBody tr', { hasText: email })
      .locator('[data-staff-reset]').click();
    await page.waitForLoadState('networkidle');

    const banner = page.locator('#staffAddSuccess');
    await expect(banner).toContainText('New temporary password', { timeout: 30000 });
    await expect(banner).toContainText('not shown again');
    // A reset that returned the same password would not be a reset.
    await expect(banner).not.toContainText(invited.temporaryPassword);
  });
});

test.describe('Rewards Marketplace row actions', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsAdmin(page);
  });

  async function openRewards(page) {
    await page.goto('/web/admin/management');
    await page.waitForLoadState('networkidle');
    await page.locator('#hubGrid').getByText('Rewards Marketplace', { exact: true }).first().click();
    await expect(page.locator('#sec-rewards')).toBeVisible();
  }

  async function createReward(page, title, xpCost) {
    const result = await page.evaluate(async ([t, c]) => {
      const params = new URLSearchParams({
        title: t, description: 'Playwright reward', xpCost: String(c),
        displayEmoji: 'R', inventoryCount: '5',
      });
      const res = await fetch('/web/admin/rewards/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params.toString(),
      });
      return { ok: res.ok, url: res.url };
    }, [title, xpCost]);
    return result;
  }

  test('a reward priced at or below zero is refused by the server', async ({ page }) => {
    // The form carries min="1", which a browser honours and fetch ignores --
    // so this is the only path that proves the rule exists.
    const result = await createReward(page, `PW Free XP ${Date.now()}`, -50);
    expect(result.url, 'the server must refuse a negative cost').toContain('rewardError');
  });

  test('a reward can be withdrawn from the marketplace', async ({ page }) => {
    const title = `PW Removable ${Date.now()}`;
    await createReward(page, title, 40);
    await openRewards(page);

    const row = page.locator('#sec-rewards tr', { hasText: title });
    await expect(row).toBeVisible({ timeout: 30000 });

    page.once('dialog', d => d.accept());
    await row.locator('[data-reward-delete]').click();
    await page.waitForLoadState('networkidle');

    await expect(page.locator('#sec-rewards tr', { hasText: title })).toHaveCount(0, { timeout: 30000 });
  });
});

test.describe('Endpoints added for these screens', () => {
  test('grade options come from the school, not a fixed list', async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsAdmin(page);

    // An admin must be able to read this: /web/teacher/tasks admits ADMIN, and
    // the dropdown on it is filled from here. The chain used to refuse them
    // regardless of the method annotation, so the page offered "Could not load
    // classes" to the one role most likely to be setting a school up.
    const response = await page.evaluate(async () => {
      const res = await fetch('/api/teacher/grade-options');
      return { status: res.status, body: await res.json() };
    });
    expect(response.status, 'an admin must not be refused this endpoint').toBe(200);

    const grades = response.body;
    expect(Array.isArray(grades)).toBe(true);
    // Whatever the school runs, every option must carry both halves -- the
    // numeric standard a task is stored against and a label to show.
    for (const g of grades) {
      expect(g).toHaveProperty('value');
      expect(g).toHaveProperty('label');
      expect(typeof g.value).toBe('number');
    }
  });

  test('the subdomain check is rate limited per client, not globally', async ({ page }) => {
    await page.goto('/web/onboard/signup');
    await page.waitForLoadState('networkidle');

    // The endpoint has to stay unauthenticated -- nobody has an account at
    // signup -- and it answers whether an address belongs to a school on
    // ACADIA, so left open it is a customer list anyone can walk.
    //
    // A deliberately unique forwarded address, so exhausting the limit here
    // cannot refuse the other specs that call this endpoint in the same minute.
    const scraper = '198.18.' + Math.floor(Math.random() * 250) + '.' + Math.floor(Math.random() * 250);

    const outcome = await page.evaluate(async (from) => {
      let firstRefusalAt = null;
      let servedBeforeRefusal = 0;
      for (let i = 1; i <= 40; i++) {
        const res = await fetch('/api/onboard/subdomain-available?subdomain=probe' + i,
          { headers: { 'X-Forwarded-For': from } });
        if (res.status === 429) { firstRefusalAt = i; break; }
        servedBeforeRefusal = i;
      }
      // A different client must still be served, or one scraper takes signup
      // down for everybody -- which is what keying on the proxy's own address
      // would have done.
      const other = await fetch('/api/onboard/subdomain-available?subdomain=someone-else',
        { headers: { 'X-Forwarded-For': '203.0.113.200' } });
      return { firstRefusalAt, servedBeforeRefusal, otherStatus: other.status };
    }, scraper);

    expect(outcome.firstRefusalAt, 'bulk checks from one address have to be cut off').not.toBeNull();
    expect(outcome.servedBeforeRefusal,
      'and the limit has to be generous enough that typing a name never trips it')
      .toBeGreaterThanOrEqual(20);
    expect(outcome.otherStatus, 'a different client keeps its own allowance').toBe(200);
  });

  test('section options are what the task form asks for now', async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsAdmin(page);

    // The task form asks for a section rather than a grade: a task set against
    // "Grade 6" reached 6-A and 6-B alike, which is how one teacher's homework
    // landed on another teacher's class list.
    const response = await page.evaluate(async () => {
      const res = await fetch('/api/teacher/section-options');
      return { status: res.status, body: await res.json() };
    });
    expect(response.status, 'an admin fills this dropdown too').toBe(200);

    const sections = response.body;
    expect(Array.isArray(sections)).toBe(true);
    expect(sections.length, 'the seeded school runs at least one section').toBeGreaterThan(0);
    for (const s of sections) {
      // A section id, not a number: the numeric standard is what reached two
      // sections at once.
      expect(typeof s.value).toBe('string');
      expect(s.value).toMatch(/^[0-9a-fA-F-]{36}$/);
      expect(typeof s.label).toBe('string');
      expect(s.label.length).toBeGreaterThan(0);
    }
  });

  test('subdomain availability normalises and reports honestly', async ({ page }) => {
    await page.goto('/web/onboard/signup');
    await page.waitForLoadState('networkidle');

    // "Demo@SSC" cannot be a hostname; the server says what it would become.
    const messy = await page.evaluate(async () =>
      (await fetch('/api/onboard/subdomain-available?subdomain=' + encodeURIComponent('Demo@SSC'))).json());
    expect(messy.subdomain).toBe('demo-ssc');

    // A name this test creates, rather than a seeded one: the seeded
    // subdomains contain underscores ("greenwood_static"), and an underscore
    // is not a legal hostname character, so normalisation rewrites it to
    // "greenwood-static" -- a different name, which really is available. That
    // is correct behaviour and a misleading thing to assert against.
    const slug = `pw-taken-${Date.now()}`;
    const created = await page.evaluate(async (sub) => {
      const res = await fetch('/api/onboard/create-school', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          schoolName: 'PW Availability School', subdomain: sub,
          adminEmail: `${sub}@example.com`, adminPassword: 'PilotLaunchSecure2026!',
          adminFullName: 'PW Admin', schoolType: 'SECONDARY',
        }),
      });
      return res.ok;
    }, slug);
    expect(created, 'the fixture school must be created').toBe(true);

    const taken = await page.evaluate(async (sub) =>
      (await fetch('/api/onboard/subdomain-available?subdomain=' + encodeURIComponent(sub))).json(), slug);
    expect(taken.available).toBe(false);
    expect(taken.reason).toBeTruthy();

    // And a name nobody has taken reads as free.
    const free = await page.evaluate(async (sub) =>
      (await fetch('/api/onboard/subdomain-available?subdomain=' + encodeURIComponent(sub + '-x'))).json(), slug);
    expect(free.available).toBe(true);
  });
});
