const fs = require('fs');
const path = require('path');

const dir = path.join(__dirname, 'tests');
const files = fs.readdirSync(dir).filter(f => f.endsWith('.spec.js'));

for (const file of files) {
    const filePath = path.join(dir, file);
    let content = fs.readFileSync(filePath, 'utf8');
    let changed = false;

    // Matches await page.goto('/web/student/portal?tab=XXX');
    const studentRegex = /await page\.goto\('\/web\/student\/portal\?tab=([a-z]+)'\);/g;
    if (studentRegex.test(content)) {
        content = content.replace(studentRegex, (match, tab) => {
            let actualTab = tab;
            if (tab === 'dashboard') actualTab = 'challenges'; // mostly challenges for tasks
            if (tab === 'history') actualTab = 'profile';
            if (tab === 'rewards') actualTab = 'rewards';
            
            return `await page.goto('/web/student/portal');\n    await page.locator('[data-tab="${actualTab}"]').first().click();\n    await page.waitForLoadState('networkidle');`;
        });
        changed = true;
    }

    // Matches await page.goto('/web/parent/dashboard?tab=XXX');
    const parentRegex = /await page\.goto\('\/web\/parent\/dashboard\?tab=([a-z]+)'\);/g;
    if (parentRegex.test(content)) {
        content = content.replace(parentRegex, (match, tab) => {
            return `await page.goto('/web/parent/dashboard');\n    await page.locator('[data-tab="${tab}"]').first().click();\n    await page.waitForLoadState('networkidle');`;
        });
        changed = true;
    }
    
    // Also, if there are any remaining /web/parent/portal in any file, replace with dashboard.
    if (content.includes("await page.goto('/web/parent/portal');")) {
        content = content.replace(/await page\.goto\('\/web\/parent\/portal'\);/g, "await page.goto('/web/parent/dashboard');");
        changed = true;
    }

    if (changed) {
        fs.writeFileSync(filePath, content);
        console.log(`Fixed hardcoded tabs in ${file}`);
    }
}
