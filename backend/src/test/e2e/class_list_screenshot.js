const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  await page.goto('http://localhost:8080/login');
  await page.fill('input[name="username"]', 'teacher@greenwood.com');
  await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForURL('**/web/**');
  await page.waitForLoadState('load');

  await page.goto('http://localhost:8080/web/admin/dashboard');
  await page.waitForLoadState('load');
  await page.waitForTimeout(800);
  await page.screenshot({ path: 'd:\\Development\\parent-trust-os\\backend\\src\\test\\e2e\\teacher_class_list.png', fullPage: true });

  const bodyText = await page.locator('body').innerText();
  console.log('URL:', page.url());
  console.log('Has Grade 6:', bodyText.includes('Grade 6'));
  console.log('Has Grade 7:', bodyText.includes('Grade 7'));
  console.log('Has Grade 8:', bodyText.includes('Grade 8'));
  console.log('Has Mathematics:', bodyText.includes('Mathematics'));
  console.log('Has Science:', bodyText.includes('Science'));
  console.log('Has English:', bodyText.includes('English'));
  await browser.close();
})();
