const { test, expect } = require('./fixtures');

/**
 * Photos in the app: a parent sees what the school shared, and a teacher adds
 * photos from the phone's library to an album for their class.
 *
 * Albums are made through the API here, as staff would on the web portal; the
 * app's part is showing them and, for a teacher, adding to them.
 */
test.describe('Photos', () => {
  test.use({ baseURL: 'http://localhost:8081' });

  const API = 'http://localhost:8080';

  async function login(page, username, password) {
    await page.goto('/');
    await page.getByPlaceholder('Email / Username').fill(username);
    await page.getByPlaceholder('Password').fill(password);
    await page.getByText('Log In').click();
    await page.waitForLoadState('networkidle');
    await expect(page.getByPlaceholder('Password')).toHaveCount(0, { timeout: 30000 });
  }

  async function tokenFor(request, email) {
    const res = await request.post(`${API}/api/mobile/auth/login`, {
      data: { email, password: process.env.DEMO_ADMIN_PASSWORD },
    });
    expect(res.ok()).toBeTruthy();
    return { Authorization: `Bearer ${(await res.json()).token}` };
  }

  // A real JPEG, drawn by the browser.
  async function jpeg(page, width, height) {
    const base64 = await page.evaluate(({ width, height }) => {
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      const ctx = canvas.getContext('2d');
      ctx.fillStyle = '#f97316';
      ctx.fillRect(0, 0, width, height);
      return canvas.toDataURL('image/jpeg', 0.9).split(',')[1];
    }, { width, height });
    return Buffer.from(base64, 'base64');
  }

  test('a parent opens a school album and a photo, with nothing to change', async ({ page, request }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' }).catch(() => {});
    const admin = await tokenFor(request, 'admin@greenwood.com');
    const title = 'Sports Day ' + Math.random().toString(36).substring(7);
    const album = await (await request.post(`${API}/api/gallery/albums`, {
      headers: admin, data: { title, audienceType: 'SCHOOL', consentConfirmed: true },
    })).json();
    await page.goto('/');
    const added = await request.post(`${API}/api/gallery/albums/${album.id}/photos`, {
      headers: { ...admin, 'Content-Type': 'image/jpeg' }, data: await jpeg(page, 1000, 750),
    });
    expect(added.status()).toBe(200);

    await login(page, 'ramesh@gmail.com', process.env.DEMO_ADMIN_PASSWORD);
    await page.goto('/gallery');
    await page.waitForLoadState('networkidle');

    const card = page.getByRole('button', { name: new RegExp(title) });
    await expect(card).toContainText('1 photo');
    await card.click();
    await page.waitForLoadState('networkidle');
    await expect(page.getByText(title)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Add photos' })).toHaveCount(0);

    const photo = page.getByRole('button', { name: 'Open photo' });
    await expect(photo).toHaveCount(1);
    await expect(photo.locator('img')).toHaveJSProperty('complete', true);
    await photo.click();
    await expect(page.getByRole('button', { name: 'Remove photo' })).toHaveCount(0);
    await page.getByRole('button', { name: 'Close' }).click();
  });

  test('a teacher adds a photo from the library to their class album', async ({ page, request }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' }).catch(() => {});
    const teacher = await tokenFor(request, 'teacher@greenwood.com');
    const audiences = await (await request.get(`${API}/api/gallery/audiences`, { headers: teacher })).json();
    const mine = audiences.find(a => a.type === 'SECTION');
    expect(mine, 'the seeded teacher teaches a class').toBeDefined();
    const title = 'Art Class ' + Math.random().toString(36).substring(7);
    const created = await request.post(`${API}/api/gallery/albums`, {
      headers: teacher, data: { title, audienceType: 'SECTION', audienceRef: mine.ref, consentConfirmed: true },
    });
    expect(created.status()).toBe(200);

    await login(page, 'teacher@greenwood.com', process.env.DEMO_ADMIN_PASSWORD);
    await page.goto('/gallery');
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: new RegExp(title) }).click();
    await page.waitForLoadState('networkidle');
    await expect(page.getByText('No photos in this album yet')).toBeVisible();

    // The library picker, as the browser presents it.
    const chooser = page.waitForEvent('filechooser');
    await page.getByRole('button', { name: 'Add photos' }).click();
    const uploaded = page.waitForResponse(r => r.url().includes('/api/gallery/albums/') && r.request().method() === 'POST');
    await (await chooser).setFiles({ name: 'art.jpg', mimeType: 'image/jpeg', buffer: await jpeg(page, 1200, 900) });
    expect((await uploaded).status()).toBe(200);
    await page.waitForLoadState('networkidle');

    await expect(page.getByRole('button', { name: 'Open photo' })).toHaveCount(1);
    await expect(page.getByText('Photo added')).toBeVisible();
  });
});
