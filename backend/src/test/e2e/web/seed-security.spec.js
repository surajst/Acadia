const { test, expect } = require('@playwright/test');

// Helper: get a JWT for a given user
async function getToken(request, email, password) {
  const res = await request.post('/api/mobile/auth/login', {
    data: { email, password },
  });
  if (!res.ok()) return null;
  const body = await res.json();
  return body.token || null;
}

test.describe('Seed Endpoint Security (dev-mode=false)', () => {
  // ── /api/admin/assignments/seed ──────────────────────────────────────────

  test('POST /api/admin/assignments/seed — unauthenticated returns 401 or 403', async ({ request }) => {
    const res = await request.post('/api/admin/assignments/seed');
    expect([401, 403]).toContain(res.status());
  });

  test('POST /api/admin/assignments/seed — as TEACHER returns 403', async ({ request }) => {
    const token = await getToken(request, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');
    expect(token).not.toBeNull();

    const res = await request.post('/api/admin/assignments/seed', {
      headers: { Authorization: `Bearer ${token}` },
    });
    // TEACHER role is not ADMIN — Spring Security rejects with 403
    expect(res.status()).toBe(403);
  });

  test('POST /api/admin/assignments/seed — as ADMIN returns 403 (devMode=false guard)', async ({ request }) => {
    const token = await getToken(request, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
    expect(token).not.toBeNull();

    const res = await request.post('/api/admin/assignments/seed', {
      headers: { Authorization: `Bearer ${token}` },
    });
    // Admin passes Spring Security but devMode=false fires the 403 guard in the controller
    expect(res.status()).toBe(403);
    const body = await res.json();
    expect(body.error).toBe('Seed endpoints are disabled in production');
  });

  // ── /api/teacher/timetable/seed ──────────────────────────────────────────

  test('POST /api/teacher/timetable/seed — unauthenticated returns 401 or 403', async ({ request }) => {
    const res = await request.post('/api/teacher/timetable/seed');
    expect([401, 403]).toContain(res.status());
  });

  test('POST /api/teacher/timetable/seed — as TEACHER returns 403', async ({ request }) => {
    const token = await getToken(request, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');
    expect(token).not.toBeNull();

    const res = await request.post('/api/teacher/timetable/seed', {
      headers: { Authorization: `Bearer ${token}` },
    });
    // /api/teacher/timetable/seed is now mapped ADMIN-only before the generic /api/teacher/** rule
    // TEACHER role does not satisfy ADMIN requirement → Spring Security returns 403
    expect(res.status()).toBe(403);
  });

  test('POST /api/teacher/timetable/seed — as ADMIN returns 403 (devMode=false guard)', async ({ request }) => {
    const token = await getToken(request, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
    expect(token).not.toBeNull();

    const res = await request.post('/api/teacher/timetable/seed', {
      headers: { Authorization: `Bearer ${token}` },
    });
    // Admin has ADMIN role — passes Spring Security chain for /api/teacher/** (TEACHER rule)
    // but @PreAuthorize(ADMIN) is satisfied, then devMode=false fires the 403 guard
    expect(res.status()).toBe(403);
    const body = await res.json();
    expect(body.error).toBe('Seed endpoints are disabled in production');
  });
});
