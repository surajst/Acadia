const { chromium } = require('@playwright/test');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.goto('http://localhost:8080/web/login');
  await page.fill('#username', 'teacher_1@greenwood.com');
  await page.fill('#password', 'GreenwoodStaffTesting2026!');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(2000);
  const response = await page.goto('http://localhost:8080/web/teacher/dashboard');
  console.log(`Teacher dashboard status: ${response.status()}`);
  const title = await page.title();
  console.log(`Teacher dashboard title: ${title}`);
  await browser.close();
})();
