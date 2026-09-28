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
  /**
   * The Enter route, on a page whose script has finished loading. This is the
   * ordinary case and it always worked; the race that broke it has its own test
   * below, because this one cannot see it.
   *
   * Asserted through the same refusal the button's test uses, because it is the
   * cheapest thing that proves the handler ran at all: if the browser navigates,
   * the page reloads, the notice is gone and the field is empty.
   */
  test('pressing Enter in the link box submits rather than reloading', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'teacher@greenwood.com');
    await page.goto('/web/teacher/videos');
    await page.waitForLoadState('networkidle');

    await page.fill('#videoUrl', 'https://www.youtube.com/playlist?list=PLabc123');
    await page.selectOption('#videoSection', { label: 'Grade 6 - A' });
    await page.locator('#videoUrl').press('Enter');
    await page.waitForLoadState('networkidle');

    // The handler ran: the server answered and the page said so.
    await expect(page.locator('[data-video-notice]'))
      .toContainText(/playlist/i, { timeout: 30000 });
    // And what was typed is still there, which a reload would have taken.
    await expect(page.locator('#videoUrl'))
      .toHaveValue('https://www.youtube.com/playlist?list=PLabc123');
  });

  /**
   * The race QA actually hit, and the reason this took two attempts to find.
   *
   * The form carries no `method`, so its default is GET, and the script that
   * defines `addVideo` is parsed seventy lines below the fields. Between the
   * fields becoming interactive and that script executing there is a window where
   * the handler does not exist. An Enter press in that window threw
   * ReferenceError out of the inline attribute, the default action proceeded, and
   * the browser did a native GET to this same URL. A reload, which presents as
   * "the form cleared itself". The button looked fine only because moving a mouse
   * to it takes longer than the script takes to arrive -- so the bug was real,
   * intermittent, and invisible to a test that waits for networkidle first.
   *
   * Reproduced by serving the page with its script removed, which is what "the
   * script has not run yet" looks like from the form's point of view, and is the
   * only way to hold the window open long enough to assert on. What is asserted
   * is the thing that hurt: the page must not navigate, so what was typed
   * survives.
   */
  test('Enter cannot trigger a native GET when the handler is unavailable', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'teacher@greenwood.com');
    await page.goto('/web/teacher/videos');
    await page.waitForLoadState('networkidle');

    // The form must be VALID first. The two selects are `required` and start
    // empty, so an incomplete form is blocked by the browser's own constraint
    // validation and never reaches submit at all -- which is a different reason
    // for "nothing happened" and would make this test pass for the wrong reason.
    // A first attempt at this stripped the whole script and proved only that.
    const typed = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';
    await page.fill('#videoUrl', typed);
    await page.selectOption('#videoSection', { label: 'Grade 6 - A' });
    // The subject list is built from the chosen section, so it arrives after it.
    const subject = page.locator('#videoSubject option[value]:not([value=""])').first();
    await expect(subject).toHaveCount(1, { timeout: 30000 });
    await page.selectOption('#videoSubject',
      await subject.getAttribute('value') ?? '');

    // Now take away the one thing the race takes away: the handler. This is what
    // the form sees in the window between becoming interactive and the script
    // seventy lines below it executing.
    // Assigned rather than deleted: a top-level `function` declaration creates a
    // NON-CONFIGURABLE global, so `delete window.addVideo` fails silently and
    // leaves the handler in place -- a first attempt at this asserted nothing for
    // exactly that reason. Assignment works, and throws TypeError from the
    // attribute where the real race throws ReferenceError. The distinction does
    // not matter here: the question is whether an exception thrown out of the
    // handler lets the native submit proceed, and both answer it the same way.
    await page.evaluate(() => { window.addVideo = undefined; });
    expect(await page.evaluate(() => typeof window.addVideo),
      'the handler has to actually be gone for this to test anything')
      .toBe('undefined');

    let navigated = false;
    page.on('framenavigated', (frame) => {
      if (frame === page.mainFrame()) navigated = true;
    });

    await page.locator('#videoUrl').press('Enter');
    await page.waitForTimeout(1000);

    // The form carries no method, so a native submit is a GET to this same URL:
    // a reload, which is what "the form cleared itself" actually was.
    expect(navigated, 'Enter must not navigate: a native GET reloads the page and '
      + 'takes the link the teacher just pasted with it').toBe(false);
    await expect(page.locator('#videoUrl'),
      'what was typed has to survive a submit the page could not handle')
      .toHaveValue(typed);
  });

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
