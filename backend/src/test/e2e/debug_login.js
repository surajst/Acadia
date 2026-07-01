const { chromium, expect } = require('@playwright/test');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  console.log("Student (arjun) logging in...");
  await page.goto('http://localhost:8080/web/login');
  await page.fill('#username', 'arjun');
  await page.fill('#password', 'StudentTesting2026!');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(2000);
  
  console.log("Current URL:", page.url());
  console.log("Page title:", await page.title());
  
  // Let's print the entire page text content for debugging
  const bodyText = await page.locator('body').innerText();
  console.log("Body text preview:", bodyText.substring(0, 200));

  await browser.close();
})();
