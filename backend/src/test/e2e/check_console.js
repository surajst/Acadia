const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();
  
  const logs = [];
  page.on('console', msg => logs.push(`[${msg.type()}] ${msg.text()}`));
  page.on('pageerror', error => logs.push(`[pageerror] ${error.message}`));
  page.on('requestfailed', request => logs.push(`[requestfailed] ${request.url()} - ${request.failure().errorText}`));

  await page.goto('http://localhost:8080/login');
  await page.fill('#username', 'pilot_admin');
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForLoadState('networkidle');

  await page.goto('http://localhost:8080/web/admin/curriculum');
  await page.waitForLoadState('networkidle');
  // wait another 2 seconds just in case
  await page.waitForTimeout(2000);

  console.log("--- BROWSER LOGS ---");
  console.log(logs.join('\n'));
  
  await browser.close();
})();
