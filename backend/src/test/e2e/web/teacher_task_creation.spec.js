const { test, expect } = require('@playwright/test');

async function setJwtHeader(page, email, password) {
    const loginRes = await page.request.post('/api/mobile/auth/login', {
        data: { email, password }
    });
    if (loginRes.ok()) {
        const { token } = await loginRes.json();
        await page.setExtraHTTPHeaders({ 'Authorization': `Bearer ${token}` });
    }
}
test.describe('Teacher Task Creation', () => {
    test.setTimeout(180000);

    test('Homework task full loop', async ({ page }) => {
        page.on('console', msg => console.log('BROWSER CONSOLE:', msg.text()));
        page.on('response', response => {
            if (response.url().includes('/api/')) {
                console.log(`API RESPONSE: ${response.url()} - ${response.status()}`);
            }
        });

        // Reset via /test/reset
        await page.goto('/test/reset');
        await expect(page.locator('body')).toContainText('OK');

        // Login as teacher@greenwood.com
        await page.goto('/login');
        await page.fill('input[name="username"]', 'teacher@greenwood.com');
        await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
        await page.click('button[type="submit"]');
        await page.waitForURL('**/web/**', { timeout: 90000 });
        await setJwtHeader(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

        // Navigate to /web/teacher/tasks
        await page.goto('/web/teacher/tasks');

        // Fill form
        await page.fill('#taskTitle', 'Test Homework Task');
        await page.selectOption('#subjectType', 'SCIENCE');
        await page.selectOption('#taskType', 'HOMEWORK');
        await page.selectOption('#standard', '6');
        
        // "Whole Class" is default, but explicitly click it
        await page.locator('input[name="assignedToClass"][value="true"]').check();
        
        await page.fill('#xpReward', '60');
        
        // Due date tomorrow
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        const dateStr = tomorrow.toISOString().split('T')[0];
        await page.fill('#dueDate', dateStr);

        // Click "Assign Task"
        await page.click('button:has-text("Assign Task")');

        // Assert success toast appears
        await expect(page.locator('#toast-msg')).toHaveText('Task created successfully!', { timeout: 10000 });

        // Assert "Test Homework Task" appears in My Created Tasks table with ACTIVE badge
        const taskRow = page.locator('#tasksTableBody tr', { hasText: 'Test Homework Task' });
        await expect(taskRow).toBeVisible();
        await expect(taskRow).toContainText('ACTIVE');

        // Logout
        await page.goto('/web/teacher/dashboard'); 
        await Promise.all([
            page.waitForURL('**/login**', { timeout: 90000 }),
            page.click('button:has-text("Logout")')
        ]);

        // Login as arjun@gmail.com
        await page.fill('input[name="username"]', 'arjun@gmail.com');
        await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
        await page.click('button[type="submit"]');
        await page.waitForURL('**/web/**', { timeout: 90000 });
        await setJwtHeader(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');

        // Force navigate to dashboard tab just to be sure
        await page.goto('/web/student/portal');
        await page.locator('[data-tab="home"]').first().click();
        await page.waitForLoadState('networkidle');

        // Record initial XP
        const initialXpStr = await page.locator('#portal-total-xp').innerText();
        const initialXp = parseInt(initialXpStr.trim(), 10);

        await page.locator('[data-tab="challenges"]').first().click();
        await page.waitForLoadState('networkidle');

        // Assert "Test Homework Task" visible in Scholastic Challenges
        const challengeCard = page.locator('#scholastic-tasks-container .bg-slate-900\\/40', { hasText: 'Test Homework Task' });
        await expect(challengeCard).toBeVisible({ timeout: 10000 });
        
        // Assert XP shows 60
        await expect(challengeCard).toContainText('+60 XP');

        // Submit with proof notes
        await challengeCard.locator('button:has-text("Submit Completion")').click();
        await expect(page.locator('#taskSubmissionModal')).toBeVisible();
        
        await page.fill('#taskSubmissionModal textarea[name="proofOfWorkNotes"]', 'Completed all exercises');
        
        // Submit
        await page.click('#taskSubmissionModal button[type="submit"]');
        await page.waitForURL('**/web/student/portal?success=true', { timeout: 90000 });

        // Assert PENDING status in history
        await page.goto('/web/student/portal');
        await page.locator('[data-tab="profile"]').first().click();
        await page.waitForLoadState('networkidle');
        const historyRow = page.locator('.bg-slate-950\\/40', { hasText: 'Test Homework Task' });
        await expect(historyRow).toBeVisible();
        await expect(historyRow).toContainText('PENDING');

        // Logout via form evaluation (no visible logout button on student portal)
        await Promise.all([
            page.waitForURL('**/login**', { timeout: 90000 }),
            page.evaluate(() => {
                const form = document.createElement('form');
                form.method = 'POST';
                form.action = '/logout';
                const csrfInput = document.querySelector('input[name="_csrf"]');
                if (csrfInput) {
                    const input = document.createElement('input');
                    input.type = 'hidden';
                    input.name = '_csrf';
                    input.value = csrfInput.value;
                    form.appendChild(input);
                }
                document.body.appendChild(form);
                form.submit();
            })
        ]);

        // Login as teacher@greenwood.com
        await page.fill('input[name="username"]', 'teacher@greenwood.com');
        await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
        await page.click('button[type="submit"]');
        await page.waitForURL('**/web/**', { timeout: 90000 });
        await setJwtHeader(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');
        await page.goto('/web/teacher/dashboard');

        // Assert submission appears in Verification Queue
        const verificationRow = page.locator('div[id^="task-row-"]', { hasText: 'Test Homework Task' });
        await expect(verificationRow).toBeVisible();
        
        // Approve it
        page.once('dialog', dialog => dialog.accept());
        await verificationRow.locator('button:has-text("Approve Task")').click();
        
        await expect(verificationRow).not.toBeVisible({ timeout: 10000 });

        // Logout
        await Promise.all([
            page.waitForURL('**/login**', { timeout: 90000 }),
            page.click('button:has-text("Logout")')
        ]);

        // Login as arjun@gmail.com
        await page.fill('input[name="username"]', 'arjun@gmail.com');
        await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
        await page.click('button[type="submit"]');
        await page.waitForURL('**/web/**', { timeout: 90000 });
        await setJwtHeader(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');

        // Assert task approved in history
        await page.goto('/web/student/portal');
    await page.locator('[data-tab="profile"]').first().click();
    await page.waitForLoadState('networkidle');
        const historyRowAfter = page.locator('.bg-slate-950\\/40', { hasText: 'Test Homework Task' });
        await expect(historyRowAfter).toContainText('APPROVED');

        // Assert XP increased by 60
        await page.goto('/web/student/portal');
        await page.locator('[data-tab="home"]').first().click();
        await page.waitForLoadState('networkidle');
        await expect(page.locator('#portal-total-xp')).toHaveText((initialXp + 60).toString(), { timeout: 10000 });
    });

    test('Reading task with comprehension questions', async ({ page }) => {
        // Reset via /test/reset
        await page.goto('/test/reset');
        await expect(page.locator('body')).toContainText('OK');

        // Login as teacher@greenwood.com
        await page.goto('/login');
        await page.fill('input[name="username"]', 'teacher@greenwood.com');
        await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
        await page.click('button[type="submit"]');
        await page.waitForURL('**/web/**', { timeout: 90000 });
        await setJwtHeader(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

        // Navigate to /web/teacher/tasks
        await page.goto('/web/teacher/tasks');

        // Select READING from task type dropdown
        await page.selectOption('#taskType', 'READING');

        // Assert comprehension questions section appears
        await expect(page.locator('#readingQuestionsSection')).toHaveClass(/show/);

        // Fill form
        await page.fill('#taskTitle', 'Read Chapter 1');
        await page.selectOption('#subjectType', 'ENGLISH');
        await page.selectOption('#standard', '6');
        await page.fill('#xpReward', '50');
        
        await page.fill('#question1', 'What is the main theme?');
        await page.fill('#question2', 'Name one character');

        // Click Assign Task, assert success
        await page.click('button:has-text("Assign Task")');
        await expect(page.locator('#toast-msg')).toHaveText('Task created successfully!', { timeout: 10000 });

        // Assert task in My Created Tasks with READING badge
        const taskRow = page.locator('#tasksTableBody tr', { hasText: 'Read Chapter 1' });
        await expect(taskRow).toBeVisible();
        await expect(taskRow).toContainText('READING');

        // Logout
        await page.goto('/web/teacher/dashboard'); 
        await Promise.all([
            page.waitForURL('**/login**', { timeout: 90000 }),
            page.click('button:has-text("Logout")')
        ]);

        // Login as arjun@gmail.com
        await page.fill('input[name="username"]', 'arjun@gmail.com');
        await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
        await page.click('button[type="submit"]');
        await page.waitForURL('**/web/**', { timeout: 90000 });
        await setJwtHeader(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');
        await page.goto('/web/student/portal');
        await page.locator('[data-tab="home"]').first().click();
        await page.waitForLoadState('networkidle');

        const initialXpStr = await page.locator('#portal-total-xp').innerText();
        const initialXp = parseInt(initialXpStr.trim(), 10);

        await page.locator('[data-tab="challenges"]').first().click();
        await page.waitForLoadState('networkidle');

        // Find "Read Chapter 1" in Scholastic Challenges
        const challengeCard = page.locator('#scholastic-tasks-container .bg-slate-900\\/40', { hasText: 'Read Chapter 1' });
        await expect(challengeCard).toBeVisible({ timeout: 10000 });
        
        // Assert ðŸ“– Reading badge visible
        await expect(challengeCard).toContainText('Reading');

        // Click submit, assert question form appears with both questions
        await challengeCard.locator('button:has-text("Submit Completion")').click();
        await expect(page.locator('#taskSubmissionModal')).toBeVisible();
        
        await expect(page.locator('#modalQuestionsContainer')).toContainText('What is the main theme?');
        await expect(page.locator('#modalQuestionsContainer')).toContainText('Name one character');

        // Fill answers
        await page.fill('input[name="answer1"]', 'The theme is friendship');
        await page.fill('input[name="answer2"]', 'Tom');
        await page.fill('#taskSubmissionModal textarea[name="proofOfWorkNotes"]', 'Done');

        // Submit, assert PENDING
        await page.click('#taskSubmissionModal button[type="submit"]');
        await page.waitForURL('**/web/student/portal?success=true', { timeout: 90000 });

        await page.goto('/web/student/portal');
        await page.locator('[data-tab="profile"]').first().click();
        await page.waitForLoadState('networkidle');
        const historyRow = page.locator('.bg-slate-950\\/40', { hasText: 'Read Chapter 1' });
        await expect(historyRow).toBeVisible();
        await expect(historyRow).toContainText('PENDING');

        // Logout
        await Promise.all([
            page.waitForURL('**/login**', { timeout: 90000 }),
            page.evaluate(() => {
                const form = document.createElement('form');
                form.method = 'POST';
                form.action = '/logout';
                const csrfInput = document.querySelector('input[name="_csrf"]');
                if (csrfInput) {
                    const input = document.createElement('input');
                    input.type = 'hidden';
                    input.name = '_csrf';
                    input.value = csrfInput.value;
                    form.appendChild(input);
                }
                document.body.appendChild(form);
                form.submit();
            })
        ]);

        // Login as teacher@greenwood.com
        await page.fill('input[name="username"]', 'teacher@greenwood.com');
        await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
        await page.click('button[type="submit"]');
        await page.waitForURL('**/web/**', { timeout: 90000 });
        await setJwtHeader(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');
        await page.goto('/web/teacher/dashboard');

        // Find submission in Verification Queue
        const verificationRow = page.locator('div[id^="task-row-"]', { hasText: 'Read Chapter 1' });
        await expect(verificationRow).toBeVisible();

        // Assert questions and student answers both visible
        await expect(verificationRow).toContainText('Question 1');
        await expect(verificationRow).toContainText('The theme is friendship');
        await expect(verificationRow).toContainText('Question 2');
        await expect(verificationRow).toContainText('Tom');

        // Approve
        page.once('dialog', dialog => dialog.accept());
        await verificationRow.locator('button:has-text("Approve Task")').click();
        
        await expect(verificationRow).not.toBeVisible({ timeout: 10000 });

        // Logout
        await Promise.all([
            page.waitForURL('**/login**', { timeout: 90000 }),
            page.click('button:has-text("Logout")')
        ]);

        // Login as arjun@gmail.com
        await page.fill('input[name="username"]', 'arjun@gmail.com');
        await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
        await page.click('button[type="submit"]');
        await page.waitForURL('**/web/**', { timeout: 90000 });
        await setJwtHeader(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');

        // Assert approved and XP increased by 50
        await page.goto('/web/student/portal');
    await page.locator('[data-tab="profile"]').first().click();
    await page.waitForLoadState('networkidle');
        const historyRowAfter = page.locator('.bg-slate-950\\/40', { hasText: 'Read Chapter 1' });
        await expect(historyRowAfter).toContainText('APPROVED');

        await page.goto('/web/student/portal');
        await page.locator('[data-tab="home"]').first().click();
        await page.waitForLoadState('networkidle');
        await expect(page.locator('#portal-total-xp')).toHaveText((initialXp + 50).toString(), { timeout: 10000 });
    });
});

