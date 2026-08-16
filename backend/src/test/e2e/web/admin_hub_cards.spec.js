const { test, expect } = require('@playwright/test');

/**
 * The admin console hub is a single page: each card calls showSection() to
 * reveal a sibling <div> and hide the grid. Nothing verified that the section
 * actually became visible, so an unclosed <div> in the classrooms block nested
 * every later section INSIDE the (hidden) classrooms panel. showSection removed
 * `hidden` from the child exactly as written, the element reported
 * display:block -- and it still rendered at zero height because its parent was
 * hidden. Rewards, Staff Registry and Bus Routes were unreachable in
 * production, which meant no school could invite a teacher through the console.
 *
 * Asserting on visibility rather than on the class attribute is the whole point:
 * `toBeVisible()` walks ancestors, `not.toHaveClass('hidden')` does not and
 * would have passed throughout the outage.
 */
const CARDS = [
  ['Classrooms & Students', 'sec-classrooms'],
  ['Rewards Marketplace', 'sec-rewards'],
  ['Staff Registry', 'sec-staff'],
  ['Bus Routes', 'sec-buses'],
];

async function loginAsAdmin(page) {
  await page.goto('/test/reset');
  await page.goto('/login');
  await page.getByPlaceholder(/email|username/i).first().fill('admin@greenwood.com');
  await page.locator('input[type=password]').first().fill('PilotLaunchSecure2026!');
  await page.locator('button[type=submit]').first().click();
  await page.waitForURL('**/web/admin/dashboard');
}

test.describe('Admin console hub cards', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  for (const [label, sectionId] of CARDS) {
    test(`"${label}" card reveals #${sectionId}`, async ({ page }) => {
      await page.goto('/web/admin/management');
      await expect(page.locator('#hubGrid')).toBeVisible();

      await page.getByText(label, { exact: true }).first().click();

      await expect(page.locator('#' + sectionId)).toBeVisible();
      await expect(page.locator('#hubGrid')).toBeHidden();
    });
  }

  test('every detail section is a sibling of the hub grid, not nested in another', async ({ page }) => {
    await page.goto('/web/admin/management');

    // The failure mode was structural, so assert the structure directly: no
    // detail section may contain another. A nested section can never be shown
    // on its own no matter what showSection() does.
    const nested = await page.evaluate(() => {
      const sections = Array.from(document.querySelectorAll('[data-detail]'));
      const bad = [];
      for (const outer of sections) {
        for (const inner of sections) {
          if (outer !== inner && outer.contains(inner)) {
            bad.push(`${inner.id} is nested inside ${outer.id}`);
          }
        }
      }
      return bad;
    });
    expect(nested).toEqual([]);
  });

  test('arriving with a #hash opens that section', async ({ page }) => {
    // Handled on DOMContentLoaded, so each case needs a real document load --
    // going straight from #staff-registry to #bus-routes-section is a
    // same-document navigation that never re-fires the event.
    for (const [hash, sectionId] of [['staff-registry', 'sec-staff'], ['bus-routes-section', 'sec-buses']]) {
      await page.goto('/web/admin/dashboard');
      await page.goto('/web/admin/management#' + hash);
      await expect(page.locator('#' + sectionId)).toBeVisible();
    }
  });

  test('left-nav links open their section', async ({ page }) => {
    // The nav does not rely on the hash at all: it calls showSection() inline
    // and returns false. This is the path an admin actually clicks.
    for (const [label, sectionId] of [['Staff Registry', 'sec-staff'], ['Bus Routes', 'sec-buses']]) {
      await page.goto('/web/admin/management');
      await page.locator('a', { hasText: label }).first().click();
      await expect(page.locator('#' + sectionId)).toBeVisible();
    }
  });
});
