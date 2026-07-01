const fs = require('fs');
const path = require('path');

function replaceInFile(filePath, replacements) {
    const fullPath = path.join(__dirname, filePath);
    if (!fs.existsSync(fullPath)) return;
    let content = fs.readFileSync(fullPath, 'utf8');
    for (const [search, replace] of replacements) {
        content = content.replace(search, replace);
    }
    fs.writeFileSync(fullPath, content);
    console.log(`Updated ${filePath}`);
}

// 1. announcement-flow.spec.js
replaceInFile('tests/announcement-flow.spec.js', [
    [/await page\.goto\('\/web\/parent\/dashboard'\);/g, "await page.goto('/web/parent/dashboard?tab=feed');"]
]);

// 2. assign-task.spec.js
replaceInFile('tests/assign-task.spec.js', [
    [/await page\.goto\('\/web\/student\/portal\?tab=dashboard'\);/g, "await page.goto('/web/student/portal?tab=challenges');"]
]);

// 3. mobile_responsive.spec.js
replaceInFile('tests/mobile_responsive.spec.js', [
    [/await page\.goto\('\/web\/student\/portal'\);/g, "await page.goto('/web/student/portal?tab=challenges');"],
    [/await page\.goto\('\/web\/student\/dashboard'\);/g, "await page.goto('/web/student/dashboard?tab=challenges');"]
]);

// 4. mastery_task.spec.js
replaceInFile('tests/mastery_task.spec.js', [
    [/await page\.goto\('\/web\/student\/portal\?tab=dashboard'\);/g, "await page.goto('/web/student/portal?tab=profile');"],
    [/await page\.goto\('\/web\/student\/portal'\);/g, "await page.goto('/web/student/portal?tab=challenges');"],
    [/await page\.goto\('\/web\/student\/portal\?tab=history'\);/g, "await page.goto('/web/student/portal?tab=profile');"]
]);

console.log("All fixes applied.");
