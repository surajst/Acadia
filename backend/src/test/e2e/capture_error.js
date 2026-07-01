const { chromium } = require('@playwright/test');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  
  page.on('pageerror', exception => {
    console.log(`Uncaught exception: "${exception}"`);
  });
  
  page.on('console', msg => {
    if (msg.type() === 'error') {
      console.log(`Console error: "${msg.text()}"`);
    }
  });
  
  await page.goto('http://localhost:8080/login');
  await page.fill('#username', 'teacher_1');
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForLoadState('networkidle');
  
  await page.goto('http://localhost:8080/web/teacher/dashboard');
  await page.waitForTimeout(2000);
  
  await browser.close();
})();
