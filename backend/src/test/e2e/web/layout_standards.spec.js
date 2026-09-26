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
 * all. The attribute is discarded silently and the field accepts anything.
 *
 * This exists because that happened. R3-P2-7 gave the guardian phone field the
 * server's rule as a pattern, and I verified the regex against the server on
 * eighteen values in node before shipping it -- in node's default dialect. HTML
 * compiles `pattern` with the **v** flag, where `(` and `)` inside a character
 * class are syntax errors rather than literals, so the browser threw the whole
 * thing away and every value validated. The test written for that field caught
 * it, but only that one field; nothing would have caught the next one.
 *
 * So the rule is checked in the browser that has to run it, across every page the
 * standards sweep already visits, rather than by re-implementing the dialect in a
 * test. Compiling is all this asserts -- whether a pattern says the right thing is
 * its own screen's business.
 */
test.describe('Every input pattern is a regex a browser will actually use', () => {
    for (const p of pagesToTest) {
        test(`Assert usable pattern attributes on ${p.path} (${p.role})`, async ({ page }) => {
            await page.goto('/login');
            await page.fill('#username', p.role);
            await page.fill('#password', 'PilotLaunchSecure2026!');
            await page.click('button[type="submit"]');
            await page.waitForLoadState('load');

            await page.goto(p.path);
            await page.waitForLoadState('load');

            const broken = await page.evaluate(() => {
                const bad = [];
                document.querySelectorAll('input[pattern]').forEach((input) => {
                    const source = input.getAttribute('pattern');
                    try {
                        // How the HTML spec says a pattern is compiled: anchored,
                        // and with the v flag.
                        new RegExp('^(?:' + source + ')$', 'v');
                    } catch (e) {
                        bad.push((input.id || input.name || 'unnamed') + ': ' + e.message);
                    }
                });
                return bad;
            });

            expect(broken,
                'a pattern the browser cannot compile is ignored, so the field checks nothing')
                .toEqual([]);
        });
    }
});
