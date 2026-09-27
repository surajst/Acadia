const { test, expect } = require('@playwright/test');

/**
 * The teacher's Videos page: paste a link, pick a class and subject, remove one.
 *
 * <p>LearningVideoServiceTest owns the rules -- who may post where, what counts as
 * a YouTube link, what happens on removal. This covers what a teacher can actually
 * do with them, and the two things only a browser can show: that the refusals reach
 * the page in the server's own words, and that an admin can open the endpoints at
 * all.
 *
 * <p>That last one is not hypothetical. /api/teacher/** is hasRole("TEACHER") and
 * the chain decides before any method annotation, so an admin would have been
 * refused every video endpoint whatever @PreAuthorize said -- the same fault the
 * task endpoints carry a comment about in SecurityConfig.
 */
async function login(page, username) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'),
    { timeout: 90000 });
}

/** A real video id, never fetched: the app asks YouTube, the test does not. */
const LINK = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';

test.describe('Sharing a video with a class', () => {
  test.setTimeout(180000);

  test('the page lists what the reset seeded', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'teacher@greenwood.com');

    await page.goto('/web/teacher/videos');
    await page.waitForLoadState('networkidle');

    // The reset seeds one for this teacher's own class, so the list has to have
    // something in it -- otherwise the assertions below pass on an empty page.
    await expect(page.locator('[data-video-row]').first()).toBeVisible({ timeout: 30000 });
    await expect(page.locator('#videoCount')).toContainText(/video/);
  });

  test('a playlist is refused in the page, in the server\'s words', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'teacher@greenwood.com');
    await page.goto('/web/teacher/videos');
    await page.waitForLoadState('networkidle');

    await page.fill('#videoUrl', 'https://www.youtube.com/playlist?list=PLabc123');
    await page.selectOption('#videoSection', { label: 'Grade 6 - A' });
    await page.click('[data-add-video]');
    await page.waitForLoadState('networkidle');

    // The refusal has to say what to do next. "Invalid link" sends a teacher to
    // support; "open the one video you want" sends them to the address bar.
    await expect(page.locator('[data-video-notice]'))
      .toContainText(/playlist/i, { timeout: 30000 });
    await expect(page.locator('[data-video-notice]')).toContainText(/one video/i);
  });

  /**
   * The subject picker offers only what this teacher takes for the chosen class,
   * because the server refuses the rest -- the same rule the task form follows.
   */
  test('the subject picker offers only what this teacher teaches', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'teacher@greenwood.com');
    await page.goto('/web/teacher/videos');
    await page.waitForLoadState('networkidle');

    const offered = await page.locator('#videoSubject option')
      .evaluateAll(opts => opts.map(o => o.value).filter(Boolean));
    expect(offered.length, 'the picker must offer something').toBeGreaterThan(0);
    expect(offered, 'this teacher takes Mathematics in 6-A and nothing else')
      .toEqual(['MATHEMATICS']);
  });

  test('removing asks first, and says what is kept', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'teacher@greenwood.com');
    await page.goto('/web/teacher/videos');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-video-row]').first()).toBeVisible({ timeout: 30000 });

    let asked = null;
    page.once('dialog', (dialog) => { asked = dialog.message(); dialog.dismiss(); });
    await page.locator('[data-remove-video]').first().click();
    await page.waitForLoadState('networkidle');

    expect(asked, 'removing a video must ask first').toBeTruthy();
    // The question somebody about to press it actually has.
    expect(asked).toMatch(/watched it stays on record/i);
    // Dismissed, so it is still there.
    await expect(page.locator('[data-video-row]').first()).toBeVisible();
  });

  test('accepting the confirmation takes it off the list', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'teacher@greenwood.com');
    await page.goto('/web/teacher/videos');
    await page.waitForLoadState('networkidle');

    const before = await page.locator('[data-video-row]').count();
    expect(before, 'nothing to remove means nothing is being tested').toBeGreaterThan(0);

    page.once('dialog', (dialog) => dialog.accept());
    await page.locator('[data-remove-video]').first().click();
    await expect(page.locator('[data-video-notice]')).toContainText(/removed/i, { timeout: 30000 });

    await expect(page.locator('[data-video-row]')).toHaveCount(before - 1);
  });

  /**
   * An admin is meant to see and remove any video in the school. Worth its own
   * test because the thing that would stop them is not in this feature's code at
   * all: /api/teacher/** is hasRole("TEACHER"), the security chain is evaluated
   * before @PreAuthorize, and it would refuse them silently.
   */
  test('an admin can open the video endpoints', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'admin@greenwood.com');

    const status = await page.evaluate(async () => {
      const response = await fetch('/api/teacher/videos');
      return response.status;
    });

    expect(status, 'an admin refused here can never reach the service rule that allows them')
      .toBe(200);
  });
});
