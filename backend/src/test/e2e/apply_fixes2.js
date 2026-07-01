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

const TAB_CLICK_CHALLENGES = "await page.goto('/web/student/portal');\n    await page.locator('[data-tab=\"challenges\"]').first().click();\n    await page.waitForLoadState('networkidle');";
const TAB_CLICK_FEED = "await page.goto('/web/parent/dashboard');\n    await page.locator('[data-tab=\"feed\"]').first().click();\n    await page.waitForLoadState('networkidle');";
const TAB_CLICK_PROGRESS = "await page.goto('/web/parent/dashboard');\n    await page.locator('[data-tab=\"progress\"]').first().click();\n    await page.waitForLoadState('networkidle');";
const TAB_CLICK_REWARDS = "await page.goto('/web/student/portal');\n    await page.locator('[data-tab=\"rewards\"]').first().click();\n    await page.waitForLoadState('networkidle');";
const TAB_CLICK_PROFILE = "await page.goto('/web/parent/dashboard');\n    await page.locator('[data-tab=\"profile\"]').first().click();\n    await page.waitForLoadState('networkidle');";
const TAB_CLICK_SYLLABUS = "await page.goto('/web/parent/dashboard');\n    await page.locator('[data-tab=\"syllabus\"]').first().click();\n    await page.waitForLoadState('networkidle');";
const TAB_CLICK_CHALLENGES_DASHBOARD = "await page.goto('/web/student/dashboard');\n    await page.locator('[data-tab=\"challenges\"]').first().click();\n    await page.waitForLoadState('networkidle');";


// 1. announcement-flow.spec.js
replaceInFile('tests/announcement-flow.spec.js', [
    [/await page\.goto\('\/web\/parent\/dashboard'\);/g, TAB_CLICK_FEED]
]);

// 2. assign-task.spec.js
replaceInFile('tests/assign-task.spec.js', [
    [/await page\.goto\('\/web\/student\/portal'\);/g, TAB_CLICK_CHALLENGES]
]);

// 3. mastery_task.spec.js
replaceInFile('tests/mastery_task.spec.js', [
    [/await page\.goto\('\/web\/student\/portal'\);/g, TAB_CLICK_CHALLENGES]
]);

// 4. mobile_responsive.spec.js
replaceInFile('tests/mobile_responsive.spec.js', [
    [/await page\.goto\('\/web\/student\/portal'\);/g, TAB_CLICK_CHALLENGES],
    [/await page\.goto\('\/web\/student\/dashboard'\);/g, TAB_CLICK_CHALLENGES_DASHBOARD]
]);

// 5. parent_child_progress.spec.js
replaceInFile('tests/parent_child_progress.spec.js', [
    [/await page\.goto\('\/web\/parent\/dashboard'\);/g, TAB_CLICK_PROGRESS]
]);

// 6. quest-engagement.spec.js
replaceInFile('tests/quest-engagement.spec.js', [
    [/await page\.goto\('\/web\/student\/portal'\);/g, TAB_CLICK_CHALLENGES]
]);

// 7. real_user_journey.spec.js
replaceInFile('tests/real_user_journey.spec.js', [
    [/await page\.goto\('\/web\/student\/portal'\);/g, TAB_CLICK_CHALLENGES]
]);

// 8. rewards-delivery.spec.js
replaceInFile('tests/rewards-delivery.spec.js', [
    [/await page\.goto\('\/web\/student\/portal'\);/g, TAB_CLICK_REWARDS],
    [/await page\.goto\('\/web\/parent\/dashboard'\);/g, TAB_CLICK_PROFILE]
]);

// 9. syllabus_verification.spec.js
replaceInFile('tests/syllabus_verification.spec.js', [
    [/await page\.goto\('\/web\/parent\/dashboard'\);/g, TAB_CLICK_SYLLABUS]
]);

// 10 & 11. teacher_task_creation.spec.js
replaceInFile('tests/teacher_task_creation.spec.js', [
    [/await page\.goto\('\/web\/student\/dashboard'\);/g, TAB_CLICK_CHALLENGES_DASHBOARD]
]);

// 12. workflow.spec.js
replaceInFile('tests/workflow.spec.js', [
    [/await page\.goto\('\/web\/student\/portal'\);/g, TAB_CLICK_CHALLENGES]
]);

console.log("All fixes applied using regex.");
