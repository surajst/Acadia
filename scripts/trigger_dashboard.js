const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.goto('http://localhost:8080/web/login');
  await page.fill('#username', 'admin@greenwood.com');
  await page.fill('#password', 'GreenwoodStaffTesting2026!');
  await page.click('button[type="submit"]');
  
  await page.waitForTimeout(2000);
  
  await page.goto('http://localhost:8080/web/teacher/dashboard');
  await page.waitForTimeout(2000);
  
  console.log(await page.title());
  await browser.close();
})();
