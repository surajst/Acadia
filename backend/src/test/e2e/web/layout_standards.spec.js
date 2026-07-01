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
        await page.goto('http://localhost:8080/test/reset');
        await page.close();
    });

    for (const p of pagesToTest) {
        test(`Assert zero-scroll layout on ${p.path} (${p.role})`, async ({ page }) => {
            await page.goto('http://localhost:8080/login');
            await page.fill('#username', p.role);
            await page.fill('#password', 'PilotLaunchSecure2026!');
            await page.click('button[type="submit"]');
            await page.waitForLoadState('load');

            await page.goto(`http://localhost:8080${p.path}`);
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
