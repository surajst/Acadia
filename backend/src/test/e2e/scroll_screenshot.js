const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.goto('http://localhost:8080/login');
  await page.fill('input[name="username"]', 'admin@greenwood.com');
  await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForURL('**/web/**');
  await page.goto('http://localhost:8080/web/admin/assignments?teacher=25988889-5013-412c-a39b-9273903df5c1');
  await page.waitForLoadState('load');
  // Scroll to the table
  await page.locator('text=Current Assignments').scrollIntoViewIfNeeded();
  await page.waitForTimeout(500);
  await page.screenshot({ path: 'd:\\Development\\parent-trust-os\\backend\\src\\test\\e2e\\teacher_assignments_table.png', fullPage: true });
  console.log('done');
  await browser.close();
})();
