const fs = require('fs');
const path = require('path');

const filePath = path.join(__dirname, 'tests', 'mobile_responsive.spec.js');
let content = fs.readFileSync(filePath, 'utf8');

// Fix Parent Dashboard: Change "Assign Home Task" to "Assign Task"
content = content.replace(
    /await expect\(page\.locator\('text=Assign Home Task'\)\.first\(\)\)\.toBeVisible\(\);/g,
    "await expect(page.locator('text=Assign Task').first()).toBeVisible();"
);

// Fix Student Dashboard: remove the bogus tab clicks
content = content.replace(
    /await page\.goto\('\/web\/student\/dashboard'\);\s*await page\.locator\('\[data-tab="challenges"\]'\)\.first\(\)\.click\(\);\s*await page\.waitForLoadState\('networkidle'\);\s*await page\.locator\('\[data-tab="challenges"\]'\)\.first\(\)\.click\(\);\s*await page\.waitForLoadState\('networkidle'\);/g,
    "await page.goto('/web/student/dashboard');"
);

// Fix Student Portal: remove the duplicate click
content = content.replace(
    /await page\.goto\('\/web\/student\/portal'\);\s*await page\.locator\('\[data-tab="challenges"\]'\)\.first\(\)\.click\(\);\s*await page\.waitForLoadState\('networkidle'\);\s*await page\.locator\('\[data-tab="challenges"\]'\)\.first\(\)\.click\(\);\s*await page\.waitForLoadState\('networkidle'\);/g,
    "await page.goto('/web/student/portal');\n    await page.locator('[data-tab=\"challenges\"]').first().click();\n    await page.waitForLoadState('networkidle');"
);

fs.writeFileSync(filePath, content);
console.log('mobile_responsive.spec.js fixed');
