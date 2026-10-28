const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });
  await page.waitForLoadState('networkidle');
}

// A real JPEG, drawn by the browser, handed to the file picker as a file.
async function jpegFile(page, name, width, height) {
  const base64 = await page.evaluate(({ width, height }) => {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#22c55e';
    ctx.fillRect(0, 0, width, height);
    return canvas.toDataURL('image/jpeg', 0.9).split(',')[1];
  }, { width, height });
  return { name, mimeType: 'image/jpeg', buffer: Buffer.from(base64, 'base64') };
}

test.describe('Photos page', () => {

  test('A teacher makes an album for their class, adds a photo, opens it and removes it', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'teacher@greenwood.com', process.env.DEMO_ADMIN_PASSWORD);

    // Reached from the sidebar, not only by typing the address.
    await page.locator('a.navlink[href="/web/gallery"]').click();
    await page.waitForURL(url => url.pathname === '/web/gallery', { timeout: 90000 });
    await page.waitForLoadState('networkidle');

    const title = 'Field Trip ' + Math.random().toString(36).substring(7);
    await page.fill('#albumTitle', title);
    // A teacher is offered only classes they teach; take the first.
    await expect(page.locator('#albumAudience option')).not.toHaveCount(1);
    await page.selectOption('#albumAudience', { index: 1 });

    // Without the consent confirmation the album is refused, in words.
    await page.click('#createAlbumBtn');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('#new-album-error')).toContainText('Confirm you have checked photo consent');

    await page.check('#consentConfirmed');
    await page.click('#createAlbumBtn');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('#album-title')).toHaveText(title);
    await expect(page.locator('#photos')).toContainText('No photos in this album yet');

    const uploaded = page.waitForResponse(r => r.url().includes('/photos') && r.request().method() === 'POST');
    await page.setInputFiles('#photoInput', await jpegFile(page, 'trip.jpg', 1200, 900));
    expect((await uploaded).status()).toBe(200);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('.photo-tile')).toHaveCount(1);
    await expect(page.locator('.photo-tile img')).toHaveJSProperty('complete', true);

    // Full size opens over the page and closes on a tap.
    await page.locator('.photo-open').click();
    await expect(page.locator('#lightbox')).toHaveClass(/open/);
    await page.locator('#lightbox').click();
    await expect(page.locator('#lightbox')).not.toHaveClass(/open/);

    page.once('dialog', dialog => dialog.accept());
    await page.locator('.photo-remove').click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('.photo-tile')).toHaveCount(0);
    await expect(page.locator('#photos')).toContainText('No photos in this album yet');
  });

  test('A parent sees a school album from the sidebar, without staff controls', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'admin@greenwood.com', process.env.DEMO_ADMIN_PASSWORD);
    await page.goto('/web/gallery');
    await page.waitForLoadState('networkidle');
    const title = 'Annual Day ' + Math.random().toString(36).substring(7);
    await page.fill('#albumTitle', title);
    await page.selectOption('#albumAudience', { label: 'The whole school' });
    await page.check('#consentConfirmed');
    await page.click('#createAlbumBtn');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('#album-title')).toHaveText(title);
    await page.setInputFiles('#photoInput', await jpegFile(page, 'stage.jpg', 800, 600));
    await expect(page.locator('.photo-tile')).toHaveCount(1, { timeout: 30000 });

    await page.context().clearCookies();
    await login(page, 'ramesh@gmail.com', process.env.DEMO_ADMIN_PASSWORD);
    await page.locator('#nav-photos').click();
    await page.waitForURL(url => url.pathname === '/web/gallery', { timeout: 90000 });
    await page.waitForLoadState('networkidle');

    await expect(page.locator('#newAlbumForm')).toHaveCount(0);
    const card = page.locator('.album-card', { hasText: title });
    await expect(card).toContainText('the whole school · 1 photo');
    await card.click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('.photo-tile')).toHaveCount(1);
    await expect(page.locator('#album-manage')).toBeHidden();
    await expect(page.locator('.photo-remove')).toHaveCount(0);
  });

});
