const { test, expect } = require('./fixtures');

/**
 * R3-P2-5. An unexplained "English" sat in the corner of the parent News screen.
 * It was reported, a globe icon and an accessible name were added, and it was
 * reported again -- fairly, because the wording was never the whole fault.
 *
 * Three things were wrong behind the one symptom:
 *
 *  1. The screen held the language in `useState('en')` while its picker saved the
 *     choice to the parent's record. So it wrote a preference and then ignored
 *     it: a parent who picked Hindi came back to a chip reading "English". The
 *     value up there was never their setting, just a hardcoded default, which is
 *     exactly why it read as text left behind by something.
 *  2. Choosing English back was guarded by `if (code !== 'en')` and so never
 *     saved -- the one choice the picker offered that it refused to remember.
 *  3. The chip said only a language name. In the default state nothing visible
 *     depends on it, because Translate does not even appear until the language is
 *     something other than English.
 *
 * The persistence assertions are the substance. Wording alone was tried once and
 * came straight back.
 */
test.describe('The language a parent reads notices in', () => {
  test.use({ baseURL: 'http://localhost:8081' });

  const API = 'http://localhost:8080';

  async function login(page) {
    await page.goto('/');
    await page.getByPlaceholder('Email / Username').fill('ramesh@gmail.com');
    await page.getByPlaceholder('Password').fill('PilotLaunchSecure2026!');
    await page.getByText('Log In').click();
    await page.waitForLoadState('networkidle');
    await expect(page.getByPlaceholder('Password')).toHaveCount(0, { timeout: 30000 });
  }

  async function openNews(page) {
    await page.goto('/announcements');
    await page.waitForLoadState('networkidle');
  }

  test('the control says what it governs, not just a language name', async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page);
    await openNews(page);

    await expect(page.getByText('Read in English')).toBeVisible({ timeout: 30000 });
  });

  test('a language a parent picks is still there when they come back', async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page);
    await openNews(page);

    await page.getByText('Read in English').click();
    await page.getByText('Hindi', { exact: true }).click();
    await expect(page.getByText('Read in Hindi')).toBeVisible({ timeout: 30000 });

    // The whole finding: leave and come back. Before this, the chip reset to
    // English every time while the record said Hindi.
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    await openNews(page);

    await expect(page.getByText('Read in Hindi')).toBeVisible({ timeout: 30000 });
    await expect(page.getByText('Read in English')).toHaveCount(0);
  });

  /**
   * The half that was guarded out. Going back to English has to be as saveable as
   * leaving it, or a parent who tries another language is stuck with it.
   */
  test('going back to English saves too', async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page);
    await openNews(page);

    await page.getByText('Read in English').click();
    await page.getByText('Marathi', { exact: true }).click();
    await expect(page.getByText('Read in Marathi')).toBeVisible({ timeout: 30000 });

    await page.getByText('Read in Marathi').click();
    await page.getByText('English', { exact: true }).click();
    await expect(page.getByText('Read in English')).toBeVisible({ timeout: 30000 });

    await page.goto('/');
    await page.waitForLoadState('networkidle');
    await openNews(page);

    await expect(page.getByText('Read in English')).toBeVisible({ timeout: 30000 });
    await expect(page.getByText('Read in Marathi')).toHaveCount(0);
  });
});
