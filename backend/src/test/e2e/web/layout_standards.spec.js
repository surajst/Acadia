const { test, expect } = require('@playwright/test');

const pagesToTest = [
    { role: 'admin_1', path: '/web/admin/dashboard' },
    { role: 'admin_1', path: '/web/admin/management' },
    { role: 'admin_1', path: '/web/admin/fees' },
    { role: 'admin_1', path: '/web/admin/curriculum' },
    { role: 'teacher@greenwood.com', path: '/web/teacher/dashboard' },
    { role: 'teacher@greenwood.com', path: '/web/teacher/tasks' },
    { role: 'ramesh@gmail.com', path: '/web/parent/portal' },
    { role: 'arjun@gmail.com', path: '/web/student/portal' }
];

test.describe('Viewport Architecture Standards', () => {
    test.beforeAll(async ({ browser }) => {
        const page = await browser.newPage();
        await page.goto('/test/reset');
        await page.close();
    });

    for (const p of pagesToTest) {
        test(`Assert zero-scroll layout on ${p.path} (${p.role})`, async ({ page }) => {
            await page.goto('/login');
            await page.fill('#username', p.role);
            await page.fill('#password', 'PilotLaunchSecure2026!');
            await page.click('button[type="submit"]');
            await page.waitForLoadState('load');

            await page.goto(p.path);
            await page.waitForLoadState('load');

            const hasNoOuterScroll = await page.evaluate(() => {
                return {
                    htmlScrollHeight: document.documentElement.scrollHeight,
                    htmlClientHeight: document.documentElement.clientHeight,
                    bodyScrollHeight: document.body.scrollHeight,
                    bodyClientHeight: document.body.clientHeight
                };
            });

            // If layout is perfectly zero-scroll, scrollHeight equals clientHeight on outer containers
            expect(hasNoOuterScroll.htmlScrollHeight).toBe(hasNoOuterScroll.htmlClientHeight);
            expect(hasNoOuterScroll.bodyScrollHeight).toBe(hasNoOuterScroll.bodyClientHeight);
        });
    }
});

/**
 * A `pattern` a browser cannot compile is not a weaker check -- it is no check at
 * all. The attribute is discarded silently and the field accepts anything, so the
 * form looks validated and is not.
 *
 * This exists because that happened. R3-P2-7 gave the guardian phone field the
 * server's rule as a pattern, and I verified the regex against the server on
 * eighteen values in node before shipping it -- in node's **default** dialect. HTML
 * compiles `pattern` with the **v** flag, where `(` and `)` inside a character
 * class are syntax errors rather than literals, so the browser discarded the whole
 * thing and every value validated. The test written for that one field caught it;
 * nothing would have caught the next one.
 *
 * <h2>Read off the templates, not off the pages</h2>
 *
 * Two earlier shapes of this gate were worse, and both failure modes are worth
 * recording because they look like passes:
 *
 * <p>Signing in and sweeping rendered pages reused `pagesToTest`, which signs in as
 * `admin_1` -- not a seeded login, nothing in the codebase creates it. Those pages
 * redirect to /login, where there are no patterned inputs, and the gate passed
 * having inspected nothing. `#guardianPhone` is on /web/admin/management, so it
 * would have missed the exact bug it was written for.
 *
 * <p>Naming pages by hand then needs every page to have one: the second attempt
 * listed /web/teacher/tasks, which has no patterned input at all.
 *
 * <p>So it reads the templates from disk and compiles what it finds in a real
 * browser engine -- comprehensive by construction, no login, no routing, and a new
 * template is covered the day it is written. `total` must be greater than zero, so
 * a scan that silently finds nothing fails rather than reassures.
 */
const fs = require('fs');
const path = require('path');

const TEMPLATE_DIR = path.join(__dirname, '..', '..', '..', 'main', 'resources', 'templates');

/** Every pattern="..." in every template, with where it came from. */
function collectPatterns(dir) {
    const found = [];
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            found.push(...collectPatterns(full));
            continue;
        }
        if (!entry.name.endsWith('.html')) continue;
        const html = fs.readFileSync(full, 'utf8');
        const attribute = /\spattern\s*=\s*"([^"]*)"/g;
        let match;
        while ((match = attribute.exec(html)) !== null) {
            found.push({ file: entry.name, source: match[1] });
        }
    }
    return found;
}

test.describe('Every input pattern is a regex a browser will actually use', () => {
    test('Assert every template pattern compiles the way HTML compiles it', async ({ page }) => {
        const patterns = collectPatterns(TEMPLATE_DIR);

        // The anti-vacuity guard. An absence assertion that cannot tell "nothing
        // wrong" from "nothing looked at" is the trap this gate exists to close.
        expect(patterns.length,
            `no pattern attributes found under ${TEMPLATE_DIR} -- the templates moved, `
            + 'or the scan is broken; either way this gate is asserting nothing')
            .toBeGreaterThan(0);

        // In a browser, because the dialect is the whole point. Re-implementing it
        // here would only test my copy of it, which is how the bug shipped.
        await page.goto('/login');
        const broken = await page.evaluate((list) => {
            return list.filter((entry) => {
                try {
                    new RegExp('^(?:' + entry.source + ')$', 'v');
                    return false;
                } catch (e) {
                    entry.error = e.message;
                    return true;
                }
            });
        }, patterns);

        expect(broken,
            'a pattern the browser cannot compile is discarded, so the field checks nothing')
            .toEqual([]);
    });
});
