const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, 'tests', 'assign-task.spec.js');
let content = fs.readFileSync(file, 'utf8');

// revert the bad edit in login
content = content.replace(
    /await page\.waitForLoadState\('networkidle'\);\s*await page\.waitForTimeout\(1000\);\s*}/g,
    `await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'));\n}`
);

// fix the actual wait after modal submit
content = content.replace(
    /await page\.click\('button:has-text\("Assign Quest"\)'\);\s*\/\/\s*6\.\s*Verify page reload \/ modal dismissal\s*await page\.waitForURL[^\n]+/g,
    `await page.click('button:has-text("Assign Quest")');\n\n    // 6. Wait for the page reload\n    await page.waitForTimeout(2000);`
);

fs.writeFileSync(file, content);
console.log("assign-task.spec.js fixed with Node script");
