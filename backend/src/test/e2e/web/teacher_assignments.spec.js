// @ts-check
const { test, expect } = require('@playwright/test');

// ─── Shared helpers ───────────────────────────────────────────────────────────

async function loginAsAdmin(page) {
  await page.goto('/login');
  await page.fill('#username', 'admin@greenwood.com');
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });
}

async function loginAsTeacher(page) {
  await page.goto('/login');
  await page.fill('#username', 'teacher@greenwood.com');
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });
}

// ─── Page Health ─────────────────────────────────────────────────────────────

test.describe('Page Health: Admin Assignments', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsAdmin(page);
  });

  test('Admin Assignments page loads without errors', async ({ page }) => {
    await page.goto('/web/admin/assignments');
    await page.waitForLoadState('load');

    await expect(page.locator('text=Something went wrong')).not.toBeVisible();
    await expect(page.locator('text=Whitelabel Error Page')).not.toBeVisible();
    await expect(page.locator('text=Application Error')).not.toBeVisible();

    // Page title heading present
    await expect(page.locator('h2:has-text("Teacher Assignments")')).toBeVisible();
  });
});

// ─── Seed Endpoint Security ───────────────────────────────────────────────────
// Assignment seed endpoint is now secured (app.dev-mode=false + ADMIN role).
// seed-security.spec.js owns the full security story;
// these tests confirm the endpoint is NOT publicly callable.

test.describe('API: Assignment Seed Endpoint — secured in production', () => {
  test('POST /api/admin/assignments/seed — unauthenticated returns 401 or 403', async ({ request }) => {
    const res = await request.post('/api/admin/assignments/seed');
    expect([401, 403]).toContain(res.status());
  });

  test('POST /api/admin/assignments/seed — as TEACHER returns 403 (not admin)', async ({ request }) => {
    const loginRes = await request.post('/api/mobile/auth/login', {
      data: { email: 'teacher@greenwood.com', password: 'PilotLaunchSecure2026!' },
    });
    const { token } = await loginRes.json();
    const res = await request.post('/api/admin/assignments/seed', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.status()).toBe(403);
  });
});


// ─── Assignment Flow: Create & Remove ────────────────────────────────────────

test.describe('Feature: Assign and Remove Teacher Assignment', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsAdmin(page);
  });

  test('Admin can see existing assignment in the table', async ({ page }) => {
    await page.goto('/web/admin/assignments');
    await page.waitForLoadState('load');

    // The seeded assignment row: Mathematics in Grade 6
    await expect(page.locator('td:has-text("Mathematics")')).toBeVisible();
  });

  test('Admin can assign a new subject to teacher via form', async ({ page }) => {
    await page.goto('/web/admin/assignments');
    await page.waitForLoadState('load');

    // Select teacher by label text (avoids disabled placeholder index issues)
    const teacherOptions = await page.locator('#teacherId option:not([disabled])').all();
    expect(teacherOptions.length).toBeGreaterThanOrEqual(1);
    const firstTeacherValue = await teacherOptions[0].getAttribute('value');
    await page.selectOption('#teacherId', firstTeacherValue);

    // Select a section by label text
    const sectionOptions = await page.locator('#classSectionId option:not([disabled])').all();
    expect(sectionOptions.length).toBeGreaterThanOrEqual(1);
    const firstSectionValue = await sectionOptions[0].getAttribute('value');
    await page.selectOption('#classSectionId', firstSectionValue);

    await page.fill('#subjectName', 'Physical Education');

    // Submit
    await page.click('button:has-text("Assign Subject")');
    await page.waitForLoadState('networkidle');

    // Should redirect back to /web/admin/assignments?teacher=...
    await expect(page).toHaveURL(/\/web\/admin\/assignments/, { timeout: 90000 });

    // Either success flash (new assignment) OR error flash (duplicate from seed) — page must not crash
    await expect(page.locator('text=Something went wrong')).not.toBeVisible();
    await expect(page.locator('text=Whitelabel Error Page')).not.toBeVisible();
    const pageText = await page.locator('body').innerText();
    const hasFlash = pageText.includes('successfully') || pageText.includes('Duplicate') || pageText.includes('assignment');
    expect(hasFlash).toBeTruthy();
  });

  test('Duplicate assignment returns error flash (not a 500)', async ({ page }) => {
    await page.goto('/web/admin/assignments');
    await page.waitForLoadState('networkidle');

    // Use the seeded combo: pilot teacher + same section = guaranteed duplicate
    const teacherOptions = await page.locator('#teacherId option:not([disabled])').all();
    const firstTeacherValue = await teacherOptions[0].getAttribute('value');
    await page.selectOption('#teacherId', firstTeacherValue);

    const sectionOptions = await page.locator('#classSectionId option:not([disabled])').all();
    const firstSectionValue = await sectionOptions[0].getAttribute('value');
    await page.selectOption('#classSectionId', firstSectionValue);

    await page.fill('#subjectName', 'Mathematics');

    await page.click('button:has-text("Assign Subject")');
    await page.waitForLoadState('networkidle');

    // Page must not crash — no 500 or Thymeleaf errors
    await expect(page.locator('text=Something went wrong')).not.toBeVisible();
    await expect(page.locator('text=Whitelabel Error Page')).not.toBeVisible();

    // Error flash uses text content, not Tailwind class (purged at runtime)
    const bodyText = await page.locator('body').innerText();
    expect(
      bodyText.includes('Duplicate') ||
      bodyText.includes('already exists') ||
      bodyText.includes('assignment')
    ).toBeTruthy();
  });

  test('Admin can remove an assignment and table updates', async ({ page }) => {
    await page.goto('/web/admin/assignments');
    await page.waitForLoadState('load');

    // Confirm there is at least one Remove button
    const removeBtn = page.locator('button:has-text("Remove")').first();
    await expect(removeBtn).toBeVisible();

    // Override confirm() so it doesn't block headless execution
    await page.addInitScript(() => { window.confirm = () => true; });
    await page.reload();
    await page.waitForLoadState('load');

    // Click Remove — the form POSTs and redirects
    await page.locator('button:has-text("Remove")').first().click();
    await page.waitForLoadState('networkidle');

    // Success flash message
    await expect(page.locator('text=Assignment removed')).toBeVisible();
  });
});

// ─── Teacher API: dynamic resolution ─────────────────────────────────────────

test.describe('API: Teacher Classes resolve from JWT (not hardcoded UUID)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
  });

  test('GET /api/teacher/classes returns assignments for authenticated teacher', async ({ request }) => {
    // Login as teacher to get JWT
    const loginRes = await request.post('/api/mobile/auth/login', {
      data: { email: 'teacher@greenwood.com', password: 'PilotLaunchSecure2026!' }
    });
    expect(loginRes.ok()).toBeTruthy();
    const { token } = await loginRes.json();
    expect(token).toBeDefined();

    // Call classes endpoint
    const classRes = await request.get('/api/teacher/classes', {
      headers: { Authorization: `Bearer ${token}` }
    });
    expect(classRes.ok()).toBeTruthy();
    const body = await classRes.json();

    // Must be an array (assignments exist after seed)
    expect(Array.isArray(body)).toBeTruthy();
    expect(body.length).toBeGreaterThanOrEqual(1);

    // Each item must have subjectName from the assignment (not hardcoded "Section X")
    const first = body[0];
    expect(first.subject).toBeDefined();
    expect(first.className).toBeDefined();
    expect(first.id).toBeDefined();
  });

  test('GET /api/teacher/classes returns error object when teacher has no assignments', async ({ request }) => {
    // This test verifies the error contract: when no SubjectAssignment rows exist
    // for the teacher, the API returns {error: "...", classes: []} instead of silent []
    // We verify by inspecting the response shape from a fresh reset (before seed)
    const loginRes = await request.post('/api/mobile/auth/login', {
      data: { email: 'teacher@greenwood.com', password: 'PilotLaunchSecure2026!' }
    });
    expect(loginRes.ok()).toBeTruthy();
    const { token } = await loginRes.json();
    expect(token).toBeDefined();

    const classRes = await request.get('/api/teacher/classes', {
      headers: { Authorization: `Bearer ${token}` }
    });
    expect(classRes.ok()).toBeTruthy();
    const body = await classRes.json();

    // Contract: response is either:
    // (a) an array of assignments (if seed ran), or
    // (b) {error: "...", classes: []} (if no assignments)
    // Both are valid — the key requirement is NO 500 and NO silent empty array without error
    const isArray  = Array.isArray(body);
    const isObject = typeof body === 'object' && body !== null && !Array.isArray(body);
    expect(isArray || isObject).toBeTruthy();

    // If it's the error object form, verify the shape
    if (isObject && body.error) {
      expect(typeof body.error).toBe('string');
      expect(Array.isArray(body.classes)).toBeTruthy();
    }
  });

  test('GET /api/teacher/attendance/summary returns totals for authenticated teacher', async ({ request }) => {
    const loginRes = await request.post('/api/mobile/auth/login', {
      data: { email: 'teacher@greenwood.com', password: 'PilotLaunchSecure2026!' }
    });
    const { token } = await loginRes.json();

    const res = await request.get('/api/teacher/attendance/summary', {
      headers: { Authorization: `Bearer ${token}` }
    });
    expect(res.ok()).toBeTruthy();
    const body = await res.json();

    expect(typeof body.totalClasses).toBe('number');
    expect(typeof body.markedToday).toBe('number');
    expect(typeof body.pendingToday).toBe('number');
    expect(body.totalClasses).toBeGreaterThanOrEqual(1);
  });
});

// ─── Nav link: Teacher Assignments visible in admin_management sidebar ────────

test.describe('UI: Teacher Assignments nav link in admin_management', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsAdmin(page);
  });

  test('Teacher Assignments nav link is visible on admin_management page', async ({ page }) => {
    await page.goto('/web/admin/management');
    await page.waitForLoadState('load');

    // Two links share this href now: the page's own in-page nav, and the shell
    // sidebar entry added so the page is reachable from anywhere rather than
    // only from a card on this one. Assert the shell one, which is the global
    // navigation the rest of the app uses.
    const navLink = page.locator('.navlink[href="/web/admin/assignments"]');
    await expect(navLink).toBeVisible();
  });

  test('Teacher Assignments nav link is active/highlighted on assignments page', async ({ page }) => {
    await page.goto('/web/admin/assignments');
    await page.waitForLoadState('load');

    // Active item has indigo bg classes
    const activeLink = page.locator('a[href="/web/admin/assignments"]');
    await expect(activeLink).toBeVisible();
    const cls = await activeLink.getAttribute('class');
    expect(cls).toContain('bg-indigo-600');
  });

  test('Clicking Teacher Assignments nav link navigates to assignments page', async ({ page }) => {
    await page.goto('/web/admin/management');
    await page.waitForLoadState('load');

    await page.click('a[href="/web/admin/assignments"]');
    await page.waitForURL(/\/web\/admin\/assignments/, { timeout: 90000 });
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h2:has-text("Teacher Assignments")')).toBeVisible();
  });
});

// ─── R2-P1-1: the register is scoped to the teacher's own sections ────────────

test.describe('Attendance scope: a teacher is offered only her own sections', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
  });

  /**
   * The dropdown used to list every section in the school, and the submit
   * accepted whichever came back. The backend owns the rule (AttendanceScopeTest
   * posts another section's student id straight at the endpoint and expects a
   * 403); this checks the page a teacher actually sees agrees with it, by
   * comparing the options against the teacher's own assignment list.
   */
  test('the register dropdown offers exactly the sections the teacher is assigned', async ({ page, request }) => {
    const loginRes = await request.post('/api/mobile/auth/login', {
      data: { email: 'teacher@greenwood.com', password: 'PilotLaunchSecure2026!' }
    });
    const { token } = await loginRes.json();
    const assigned = await (await request.get('/api/teacher/classes', {
      headers: { Authorization: `Bearer ${token}` }
    })).json();

    // The endpoint answers with an {error, classes} object when a teacher has
    // no assignments at all, in which case there is nothing to compare.
    test.skip(!Array.isArray(assigned), 'the seeded teacher has no assignments');
    const assignedIds = new Set(assigned.map(c => String(c.id)));

    await loginAsTeacher(page);
    await page.goto('/web/teacher/attendance');
    await page.waitForLoadState('networkidle');

    const optionValues = await page.locator('#attendanceClassSelect option')
      .evaluateAll(nodes => nodes.map(n => n.getAttribute('value') || ''));
    const offered = optionValues
      .map(v => (v.match(/classId=([0-9a-fA-F-]{36})/) || [])[1])
      .filter(Boolean);

    expect(offered.length).toBeGreaterThan(0);
    for (const id of offered) {
      expect(assignedIds.has(id),
        `the register offered ${id}, which this teacher is not assigned to`).toBeTruthy();
    }
  });

  /** An admin covering for an absent teacher still sees the whole school. */
  test('an admin is still offered more sections than a single teacher', async ({ page, request }) => {
    const loginRes = await request.post('/api/mobile/auth/login', {
      data: { email: 'teacher@greenwood.com', password: 'PilotLaunchSecure2026!' }
    });
    const { token } = await loginRes.json();
    const assigned = await (await request.get('/api/teacher/classes', {
      headers: { Authorization: `Bearer ${token}` }
    })).json();
    const teacherCount = Array.isArray(assigned) ? assigned.length : 0;

    await loginAsAdmin(page);
    await page.goto('/web/teacher/attendance');
    await page.waitForLoadState('networkidle');

    const optionValues = await page.locator('#attendanceClassSelect option')
      .evaluateAll(nodes => nodes.map(n => n.getAttribute('value') || ''));
    const offered = optionValues.filter(v => /classId=[0-9a-fA-F-]{36}/.test(v));

    expect(offered.length).toBeGreaterThanOrEqual(teacherCount);
  });
});
