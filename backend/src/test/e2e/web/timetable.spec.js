// @ts-check
const { test, expect } = require('@playwright/test');

// ─── Shared helpers ───────────────────────────────────────────────────────────

async function loginAsTeacher(page) {
  await page.goto('/login');
  await page.fill('#username', 'teacher@greenwood.com');
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });
}

/**
 * Returns a Bearer JWT for the pilot teacher via the mobile auth endpoint.
 */
async function getTeacherToken(request) {
  const loginRes = await request.post('/api/mobile/auth/login', {
    data: { email: 'teacher@greenwood.com', password: 'PilotLaunchSecure2026!' }
  });
  expect(loginRes.ok()).toBeTruthy();
  const body = await loginRes.json();
  expect(body.token).toBeDefined();
  return body.token;
}

// ─── Seed Endpoint Security ───────────────────────────────────────────────────
// These endpoints are now secured (app.dev-mode=false + @PreAuthorize ADMIN).
// The seed-security.spec.js owns the full security story;
// these tests confirm the timetable seed endpoint is NOT publicly callable.

test.describe('API: Timetable Seed Endpoint — secured in production', () => {
  test('POST /api/teacher/timetable/seed — unauthenticated returns 401 or 403', async ({ request }) => {
    const res = await request.post('/api/teacher/timetable/seed');
    expect([401, 403]).toContain(res.status());
  });

  test('POST /api/teacher/timetable/seed — as TEACHER returns 403 (not admin)', async ({ request }) => {
    const token = await getTeacherToken(request);
    const res = await request.post('/api/teacher/timetable/seed', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.status()).toBe(403);
  });
});

// ─── Today Timetable API ──────────────────────────────────────────────────────

test.describe('API: GET /api/teacher/timetable/today', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
  });

  test('Returns 401/403 when called without authentication', async ({ request }) => {
    const res = await request.get('/api/teacher/timetable/today');
    expect([401, 403]).toContain(res.status());
  });

  test('Returns array for authenticated teacher after seed', async ({ request }) => {
    const token = await getTeacherToken(request);
    const res = await request.get('/api/teacher/timetable/today', {
      headers: { Authorization: `Bearer ${token}` }
    });
    expect(res.ok()).toBeTruthy();
    const body = await res.json();

    // Must be an array (empty on weekend, non-empty on weekday — we only assert shape)
    expect(Array.isArray(body)).toBeTruthy();
  });

  test('Each period entry has required fields', async ({ request }) => {
    const token = await getTeacherToken(request);
    const res = await request.get('/api/teacher/timetable/today', {
      headers: { Authorization: `Bearer ${token}` }
    });
    expect(res.ok()).toBeTruthy();
    const body = await res.json();

    // Validate shape of each entry if any exist
    for (const period of body) {
      expect(typeof period.id).toBe('string');
      expect(typeof period.periodNumber).toBe('number');
      expect(typeof period.startTime).toBe('string');
      expect(typeof period.endTime).toBe('string');
      expect(typeof period.subjectName).toBe('string');
      expect(typeof period.className).toBe('string');
      expect(typeof period.attendanceMarked).toBe('boolean');
      // classSectionId must be present (string UUID or null)
      expect(period.classSectionId !== undefined).toBeTruthy();
    }
  });

  test('Period times match expected HH:MM format', async ({ request }) => {
    const token = await getTeacherToken(request);
    const res = await request.get('/api/teacher/timetable/today', {
      headers: { Authorization: `Bearer ${token}` }
    });
    const body = await res.json();

    const timeRegex = /^\d{2}:\d{2}$/;
    for (const period of body) {
      expect(period.startTime).toMatch(timeRegex);
      expect(period.endTime).toMatch(timeRegex);
    }
  });

  test('Weekend returns empty array (not a 500)', async ({ request }) => {
    // We cannot force the date, but we can verify the API never errors.
    // The contract is: either [] (weekend) or a non-empty array (weekday) — never 500.
    const token = await getTeacherToken(request);
    const res = await request.get('/api/teacher/timetable/today', {
      headers: { Authorization: `Bearer ${token}` }
    });

    // Must not be a server error regardless of day of week
    expect(res.status()).toBeLessThan(500);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(Array.isArray(body)).toBeTruthy();
  });
});

// ─── Week Timetable API ───────────────────────────────────────────────────────

test.describe('API: GET /api/teacher/timetable/week', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
  });

  test('Returns 401/403 when called without authentication', async ({ request }) => {
    const res = await request.get('/api/teacher/timetable/week');
    expect([401, 403]).toContain(res.status());
  });

  test('Returns object keyed by day codes after seed', async ({ request }) => {
    const token = await getTeacherToken(request);
    const res = await request.get('/api/teacher/timetable/week', {
      headers: { Authorization: `Bearer ${token}` }
    });
    expect(res.ok()).toBeTruthy();
    const body = await res.json();

    // Must be a plain object (not an array)
    expect(typeof body).toBe('object');
    expect(Array.isArray(body)).toBeFalsy();
  });

  test('Response contains all five weekday keys (MON through FRI)', async ({ request }) => {
    const token = await getTeacherToken(request);
    const res = await request.get('/api/teacher/timetable/week', {
      headers: { Authorization: `Bearer ${token}` }
    });
    const body = await res.json();

    const expectedDays = ['MON', 'TUE', 'WED', 'THU', 'FRI'];
    for (const day of expectedDays) {
      expect(Object.prototype.hasOwnProperty.call(body, day)).toBeTruthy();
      expect(Array.isArray(body[day])).toBeTruthy();
    }
  });

  test('Each day has at least one period after seed', async ({ request }) => {
    const token = await getTeacherToken(request);
    const res = await request.get('/api/teacher/timetable/week', {
      headers: { Authorization: `Bearer ${token}` }
    });
    const body = await res.json();

    // The seed creates 2 periods per weekday — every day should have entries
    const expectedDays = ['MON', 'TUE', 'WED', 'THU', 'FRI'];
    for (const day of expectedDays) {
      expect(body[day].length).toBeGreaterThanOrEqual(1);
    }
  });

  test('Periods within each day are sorted by periodNumber ascending', async ({ request }) => {
    const token = await getTeacherToken(request);
    const res = await request.get('/api/teacher/timetable/week', {
      headers: { Authorization: `Bearer ${token}` }
    });
    const body = await res.json();

    const expectedDays = ['MON', 'TUE', 'WED', 'THU', 'FRI'];
    for (const day of expectedDays) {
      const periods = body[day];
      for (let i = 1; i < periods.length; i++) {
        expect(periods[i].periodNumber).toBeGreaterThanOrEqual(periods[i - 1].periodNumber);
      }
    }
  });

  test('Each period entry in week view has required fields', async ({ request }) => {
    const token = await getTeacherToken(request);
    const res = await request.get('/api/teacher/timetable/week', {
      headers: { Authorization: `Bearer ${token}` }
    });
    const body = await res.json();

    const expectedDays = ['MON', 'TUE', 'WED', 'THU', 'FRI'];
    for (const day of expectedDays) {
      for (const period of body[day]) {
        expect(typeof period.id).toBe('string');
        expect(typeof period.periodNumber).toBe('number');
        expect(typeof period.startTime).toBe('string');
        expect(typeof period.endTime).toBe('string');
        expect(typeof period.subjectName).toBe('string');
        expect(typeof period.className).toBe('string');
        // Week view passes null for checkDate so attendanceMarked must always be false
        expect(period.attendanceMarked).toBe(false);
      }
    }
  });

  test('Seeded timetable contains Mathematics as subject', async ({ request }) => {
    const token = await getTeacherToken(request);
    const res = await request.get('/api/teacher/timetable/week', {
      headers: { Authorization: `Bearer ${token}` }
    });
    const body = await res.json();

    // All seeded entries should have subjectName = "Mathematics"
    const expectedDays = ['MON', 'TUE', 'WED', 'THU', 'FRI'];
    for (const day of expectedDays) {
      for (const period of body[day]) {
        expect(period.subjectName).toBe('Mathematics');
      }
    }
  });

  test('Seeded timetable has roomNumber populated', async ({ request }) => {
    const token = await getTeacherToken(request);
    const res = await request.get('/api/teacher/timetable/week', {
      headers: { Authorization: `Bearer ${token}` }
    });
    const body = await res.json();

    const expectedDays = ['MON', 'TUE', 'WED', 'THU', 'FRI'];
    for (const day of expectedDays) {
      for (const period of body[day]) {
        expect(period.roomNumber).toBe('Room 204');
      }
    }
  });
});

// ─── Teacher Dashboard: Timetable visible in UI ───────────────────────────────

test.describe('UI: Timetable on Teacher Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsTeacher(page);
  });

  test('Teacher dashboard loads without errors after timetable seed', async ({ page }) => {
    await page.goto('/web/teacher/dashboard');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('text=Something went wrong')).not.toBeVisible();
    await expect(page.locator('text=Whitelabel Error Page')).not.toBeVisible();
    await expect(page.locator('text=Application Error')).not.toBeVisible();
  });
});
