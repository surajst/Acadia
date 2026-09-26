const base = require('@playwright/test');

/**
 * What every mobile spec should use in place of @playwright/test.
 *
 * <h2>Why this exists</h2>
 *
 * Tests that click anything near the bottom of the screen were failing, and
 * sometimes passing, on this:
 *
 *     <div id="error-toast"></div> intercepts pointer events
 *
 * The div looks empty and reads like an app crash with no message, which is how
 * it came to be recorded as an unexplained error on the teacher screens. It is
 * neither.
 *
 * `@expo/log-box` creates that div unconditionally on web when NODE_ENV is
 * development (see its `setupLogBox`), and puts its content in a **shadow root**
 * -- so the host element is empty from the DOM's point of view, whatever the
 * toast says. Inside it, `ErrorToast.module.css` pins a bar with
 * `position: fixed; bottom: 6px; left: 10px; right: 10px; max-width: 320px`,
 * which on the 390dp Pixel 5 viewport these tests use covers most of the bottom
 * of the screen. Any button anchored down there is underneath it.
 *
 * So it is not the app: CI runs the Expo **dev server**, and the deployed build
 * is an `expo export`, where log-box is not installed at all. The overlay is a
 * property of the instrument, and a test that fails because a development
 * overlay caught the click is measuring the wrong thing. It makes every
 * bottom-anchored button in every spec hostage to any console error anywhere in
 * the app, which is also why the failure came and went.
 *
 * Two things happen below, and the second matters as much as the first:
 *
 *  1. The overlay stops swallowing clicks. It stays visible, so a trace or a
 *     screenshot still shows it.
 *  2. Whatever it says is printed. The toast only renders when something logged
 *     at error level, so it is real signal about the app -- it was simply
 *     unreadable while it sat in a shadow root behind an empty div. Printing it
 *     keeps it from being silently hidden by step 1.
 *
 * Step 2 reports rather than fails, deliberately: there is at least one
 * long-standing console error on the teacher screens, and turning it into a
 * failure would take out the suite in the same breath as un-blocking it. Naming
 * it is the point; what to do about it is the next piece of work.
 */

/** Makes the dev overlay inert without hiding it. */
const DISARM_THE_DEV_OVERLAY = `
  #error-toast { pointer-events: none !important; }
`;

const test = base.test.extend({
  page: async ({ page }, use, testInfo) => {
    // On every document, including after a reload or a hard navigation -- the
    // specs navigate by address a great deal.
    await page.addInitScript((css) => {
      const install = () => {
        const style = document.createElement('style');
        style.setAttribute('data-playwright', 'disarm-expo-log-box');
        style.textContent = css;
        document.head.appendChild(style);
      };
      if (document.head) {
        install();
      } else {
        document.addEventListener('DOMContentLoaded', install, { once: true });
      }
    }, DISARM_THE_DEV_OVERLAY);

    await use(page);

    // Read the toast out of its shadow root, so it is legible for once.
    let said = '';
    try {
      said = await page.evaluate(() => {
        const host = document.getElementById('error-toast');
        return host && host.shadowRoot ? (host.shadowRoot.textContent || '').trim() : '';
      });
    } catch {
      // The page may already be closing; a diagnostic must never fail a test.
    }
    if (said) {
      // eslint-disable-next-line no-console
      console.log(`\n[expo log-box] "${testInfo.title}" left an error on screen: ${said}\n`);
    }
  },
});

module.exports = { test, expect: base.expect };
