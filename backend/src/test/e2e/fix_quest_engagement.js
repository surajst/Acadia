const fs = require('fs');
const path = require('path');

const filePath = path.join(__dirname, 'tests', 'quest-engagement.spec.js');
let code = fs.readFileSync(filePath, 'utf8');

code = code.replace(
    /await page\.waitForURL\(url => url\.pathname\.includes\('\/web\/student\/portal'\), { timeout: 90000 }\);\n\s*await expect\(page\.locator\('text=Awaiting Parent Approval'\)\.first\(\)\)\.toBeVisible\({ timeout: 90000 }\);/g,
    "await page.waitForURL(url => url.pathname.includes('/web/student/portal'), { timeout: 90000 });\n    await page.locator('[data-tab=\"challenges\"]').first().click();\n    await page.waitForLoadState('networkidle');\n    await expect(page.locator('text=Awaiting Parent Approval').first()).toBeVisible({ timeout: 90000 });"
);

fs.writeFileSync(filePath, code);
console.log('Fixed quest-engagement.spec.js tab navigation after redirect');
