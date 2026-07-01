const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  console.log("Navigating to /login...");
  await page.goto('http://localhost:8080/login');
  
  console.log("Current URL:", page.url());
  
  console.log("Filling username and password...");
  await page.fill('#username', 'admin');
  await page.fill('#password', 'password');
  
  console.log("Clicking submit...");
  await page.click('button[type="submit"]');
  
  // Wait a bit
  await page.waitForTimeout(2000);
  console.log("After login redirect, current URL:", page.url());

  console.log("Navigating to /web/admin/management...");
  const response = await page.goto('http://localhost:8080/web/admin/management');
  console.log("Response status:", response.status());
  console.log("Current URL after navigating to management:", page.url());

  const content = await page.content();
  if (content.includes("Authentication Failed")) {
    console.log("Page shows: Authentication Failed");
  }
  
  console.log("Closing browser.");
  await browser.close();
})();
