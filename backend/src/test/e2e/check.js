const { chromium } = require('playwright');
const fs = require('fs');

(async () => {
    const browser = await chromium.launch();
    const page = await browser.newPage();
    
    await page.goto('http://127.0.0.1:8080/test/reset');
    await page.goto('http://127.0.0.1:8080/login');
    
    await page.fill('#username', 'ramesh');
    await page.fill('#password', 'GreenwoodStaffTesting2026!');
    await page.click('button[type="submit"]');
    await page.waitForURL('**/web/**');
    
    await page.goto('http://127.0.0.1:8080/web/parent/dashboard');
    
    const content = await page.content();
    fs.writeFileSync('output.html', content);
    console.log('Saved to output.html');
    
    await browser.close();
})();
