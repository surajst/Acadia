const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  
  await page.goto('http://localhost:8080/test/reset');
  await page.goto('http://localhost:8080/login');
  await page.fill('#username', 'teacher_1');
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForLoadState('networkidle');
  
  await page.goto('http://localhost:8080/web/teacher/dashboard');
  await page.waitForTimeout(2000);
  console.log(await page.evaluate(() => document.body.innerHTML));
  await browser.close();
})();
