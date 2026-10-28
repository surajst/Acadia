const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });
  await page.waitForLoadState('networkidle');
}

// Calls the gallery API from inside the signed-in page, so the portal session is
// what authenticates -- the same way the gallery screens will call it.
async function api(page, method, path, body) {
  return page.evaluate(async ({ method, path, body }) => {
    const init = { method, headers: {} };
    if (body !== undefined) {
      init.headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(body);
    }
    const res = await fetch(path, init);
    const text = await res.text();
    let json = null;
    try { json = JSON.parse(text); } catch (e) { /* not JSON */ }
    return { status: res.status, json };
  }, { method, path, body });
}

// A real JPEG, drawn in the browser, sent as the raw request body.
async function uploadPhoto(page, albumId, width, height) {
  return page.evaluate(async ({ albumId, width, height }) => {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#3b82f6';
    ctx.fillRect(0, 0, width, height);
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.9));
    const res = await fetch(`/api/gallery/albums/${albumId}/photos`, {
      method: 'POST', headers: { 'Content-Type': 'image/jpeg' }, body: blob,
    });
    return { status: res.status, json: await res.json() };
  }, { albumId, width, height });
}

test.describe('Gallery API', () => {

  test('An admin makes a school album, adds a photo, and a parent sees it', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'admin@greenwood.com', process.env.DEMO_ADMIN_PASSWORD);

    const status = await api(page, 'GET', '/api/gallery/status');
    expect(status.status).toBe(200);
    expect(status.json.available).toBe(true);

    const audiences = await api(page, 'GET', '/api/gallery/audiences');
    expect(audiences.status).toBe(200);
    expect(audiences.json[0]).toEqual({ type: 'SCHOOL', ref: null, label: 'The whole school' });
    expect(audiences.json.some(o => o.type === 'SECTION')).toBe(true);

    const check = await api(page, 'GET', '/api/gallery/consent-check?audienceType=SCHOOL');
    expect(check.status).toBe(200);
    expect(Array.isArray(check.json)).toBe(true);

    const refused = await api(page, 'POST', '/api/gallery/albums',
      { title: 'Annual Day', audienceType: 'SCHOOL', consentConfirmed: false });
    expect(refused.status).toBe(400);

    const title = 'Annual Day ' + Math.random().toString(36).substring(7);
    const created = await api(page, 'POST', '/api/gallery/albums',
      { title, audienceType: 'SCHOOL', consentConfirmed: true });
    expect(created.status).toBe(200);
    const albumId = created.json.id;

    const added = await uploadPhoto(page, albumId, 2400, 1600);
    expect(added.status).toBe(200);
    expect(added.json.width).toBe(1600);
    expect(added.json.height).toBe(1067);

    const notAPhoto = await page.evaluate(async (albumId) => {
      const res = await fetch(`/api/gallery/albums/${albumId}/photos`, {
        method: 'POST', headers: { 'Content-Type': 'image/jpeg' }, body: 'hello',
      });
      return res.status;
    }, albumId);
    expect(notAPhoto).toBe(400);

    const pupils = await api(page, 'GET', `/api/gallery/albums/${albumId}/pupils`);
    expect(pupils.status).toBe(200);

    // Ramesh, Arjun's parent, sees the album and the photo, and the link serves it.
    await page.context().clearCookies();
    await login(page, 'ramesh@gmail.com', process.env.DEMO_ADMIN_PASSWORD);
    const albums = await api(page, 'GET', '/api/gallery/albums');
    expect(albums.status).toBe(200);
    const mine = albums.json.find(a => a.id === albumId);
    expect(mine.photoCount).toBe(1);
    expect(mine.canAddPhotos).toBe(false);
    const photos = await api(page, 'GET', `/api/gallery/albums/${albumId}/photos`);
    expect(photos.status).toBe(200);
    const thumb = await page.request.get(photos.json[0].thumbUrl);
    expect(thumb.status()).toBe(200);
    expect(thumb.headers()['content-type']).toContain('image/jpeg');

    // A parent cannot add to it or remove from it.
    expect((await uploadPhoto(page, albumId, 400, 300)).status).toBe(403);
    expect((await api(page, 'DELETE', `/api/gallery/photos/${added.json.id}`)).status).toBe(403);
  });

  test('Staff remove a photo and an album, and tagging needs consent', async ({ page, request }) => {
    await page.goto('/test/reset');

    // Arjun's id, the way his phone learns it. Seeded pupils have no photo
    // consent on record (the honest default), so he cannot be tagged.
    const loginRes = await request.post('/api/mobile/auth/login', {
      data: { email: 'arjun@gmail.com', password: process.env.DEMO_ADMIN_PASSWORD },
    });
    expect(loginRes.ok()).toBeTruthy();
    const dash = await request.get('/api/mobile/student/dashboard', {
      headers: { Authorization: `Bearer ${(await loginRes.json()).token}` },
    });
    const arjunId = (await dash.json()).student.id;

    await login(page, 'admin@greenwood.com', process.env.DEMO_ADMIN_PASSWORD);
    const created = await api(page, 'POST', '/api/gallery/albums',
      { title: 'Science Fair', audienceType: 'SCHOOL', consentConfirmed: true });
    const albumId = created.json.id;
    const first = await uploadPhoto(page, albumId, 800, 600);
    const second = await uploadPhoto(page, albumId, 800, 600);
    expect(first.status).toBe(200);
    expect(second.status).toBe(200);

    const tag = await api(page, 'POST', `/api/gallery/photos/${first.json.id}/pupils`, { studentIds: [arjunId] });
    expect(tag.status).toBe(400);
    expect(tag.json.error).toContain('has no photo consent on record');
    expect(tag.json.error).not.toContain(arjunId);
    const ofArjun = await api(page, 'GET', `/api/gallery/pupils/${arjunId}/photos`);
    expect(ofArjun.status).toBe(200);
    expect(ofArjun.json).toEqual([]);

    const removedPhoto = await api(page, 'DELETE', `/api/gallery/photos/${first.json.id}`);
    expect(removedPhoto.status).toBe(200);
    const after = await api(page, 'GET', `/api/gallery/albums/${albumId}/photos`);
    expect(after.json.map(p => p.id)).toEqual([second.json.id]);

    const removedAlbum = await api(page, 'DELETE', `/api/gallery/albums/${albumId}`);
    expect(removedAlbum.status).toBe(200);
    expect((await api(page, 'GET', `/api/gallery/albums/${albumId}/photos`)).status).toBe(404);
  });

});
