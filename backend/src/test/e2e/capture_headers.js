const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setViewportSize({ width: 1280, height: 800 });

  // Admin Dashboard
  await page.goto('http://localhost:8080/test/reset');
  await page.goto('http://localhost:8080/login');
  await page.fill('#username', 'pilot_admin');
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForLoadState('networkidle');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 15000 });
  await page.goto('http://localhost:8080/web/admin/dashboard');
  await page.waitForTimeout(1000);
  await page.screenshot({ path: 'C:/Users/st/.gemini/antigravity/brain/a258ac0b-8c79-496a-9010-9e4b949a70b3/artifacts/admin_header.png' });

  // Student Dashboard
  await page.goto('http://localhost:8080/login?logout');
  await page.fill('#username', 'student_1');
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForLoadState('networkidle');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 15000 });
  await page.goto('http://localhost:8080/web/student/portal');
  await page.waitForTimeout(1000);
  await page.screenshot({ path: 'C:/Users/st/.gemini/antigravity/brain/a258ac0b-8c79-496a-9010-9e4b949a70b3/artifacts/student_header.png' });

  await browser.close();
})();
