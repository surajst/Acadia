const { chromium } = require('@playwright/test');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  
  await page.goto('http://localhost:8080/web/login');
  await page.fill('#username', 'admin_1');
  await page.fill('#password', 'admin_1');
  await page.click('button[type="submit"]');
  await page.waitForURL('http://localhost:8080/web/admin/dashboard', { timeout: 10000 });
  
  const sizes = await page.evaluate(() => {
     return {
         html: { ch: document.documentElement.clientHeight, sh: document.documentElement.scrollHeight },
         body: { ch: document.body.clientHeight, sh: document.body.scrollHeight, classes: document.body.className, style: document.body.getAttribute('style') },
         children: Array.from(document.body.children).map(c => ({ 
             tag: c.tagName, 
             id: c.id,
             oh: c.offsetHeight, 
             sh: c.scrollHeight, 
             classes: c.className,
             style: c.getAttribute('style')
         }))
     };
  });
  console.log(JSON.stringify(sizes, null, 2));
  await browser.close();
})();
