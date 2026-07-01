const fs = require('fs');
const path = require('path');

const filePath = path.join(__dirname, 'tests', 'workflow.spec.js');
let code = fs.readFileSync(filePath, 'utf8');

const prefix = code.split('    // The metric display shows <span th:text="${totalXp}"> inside metrics card.')[0];

const newEnd = `    // The metric display shows <span th:text="\${totalXp}"> inside metrics card.
    await page.goto('/web/student/portal');
    const totalXpEl = page.locator('span:near(span:has-text("Total XP"))').first();
    await expect(totalXpEl).toHaveText('250');

    // Log out
    await page.goto('/logout');

    // ----------------------------------------------------
    // STEP 3.7: Log in as Ramesh and Release Reward
    // ----------------------------------------------------
    await login(page, 'ramesh', 'GreenwoodStaffTesting2026!');
    await page.goto('/web/parent/dashboard');
    await page.locator('[data-tab="quests"]').first().click();
    await page.waitForLoadState('networkidle');
    // Release the custom reward "Pizza Party" from delivery queue
    const releaseRewardLink = page.locator('a[href*="/web/parent/reward/"][href$="/release"]').first();
    await releaseRewardLink.click();

    // Verify release redirect/toast success indicator
    await page.waitForURL(/.*\\/web\\/parent\\/dashboard.*/);
    const releaseToast = page.locator('#toast');
    await expect(releaseToast).toBeVisible();
    await expect(releaseToast).toContainText('Reward successfully delivered/released!');
  });

});
`;

fs.writeFileSync(filePath, prefix + newEnd);
console.log('Fixed workflow.spec.js');
