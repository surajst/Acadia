const { test, expect } = require('@playwright/test');

test.describe('TEACHER ATTENDANCE SUBMISSION ENGINE TEST', () => {

    test('Teacher can login, toggle attendance, and submit successfully', async ({ page }) => {
        // Reset database to a clean state
        await page.goto('http://localhost:8080/test/reset');

        // 1. Authentication
        await page.goto('/');

        // 2. Perform Login as Teacher
        await page.getByPlaceholder('Email / Username').fill('teacher@greenwood.com');
        await page.getByPlaceholder('Password').fill('PilotLaunchSecure2026!');
        await page.getByText('Log In').click();

        // 3. Wait for Navigation to Dashboard
        await page.waitForLoadState('networkidle');

        // 4. Navigate to My Classes (Tabs)
        await page.locator('text="My Classes" >> visible=true').last().click();
        await page.waitForLoadState('networkidle');

        // 5. Open Roster Modal for "Grade 6 – A"
        const classCard = page.locator('text=/View Roster/ >> visible=true').first();
        await classCard.waitFor({ state: 'visible', timeout: 5000 });
        await classCard.click();

        // 6. Assert Modal Opens and Roster Renders
        const modalTitle = page.locator('text=/Daily Attendance/ >> visible=true');
        await modalTitle.waitFor({ state: 'visible', timeout: 5000 });
        await expect(modalTitle).toBeVisible();

        const arnavRow = page.locator('text="Arnav Mehta" >> visible=true');
        await arnavRow.waitFor({ state: 'visible', timeout: 5000 });
        await expect(arnavRow).toBeVisible();

        // 7. Toggle Attendance (Mark Alisha as Absent)
        const absentToggle = page.locator('text="A"').nth(1);
        await absentToggle.waitFor({ state: 'visible', timeout: 5000 });
        await absentToggle.click();

        // 8. Submit Attendance
        const submitBtn = page.locator('text="Submit Attendance Sheet"');
        await submitBtn.waitFor({ state: 'visible', timeout: 5000 });
        await submitBtn.click();
        await page.waitForLoadState('networkidle');

        // 9. Handle Success Dialog (if browser dialog appears)
        page.on('dialog', async dialog => {
            await dialog.accept();
        });

        // 10. Assert Modal Closes
        await expect(page.locator('text=/Daily Attendance/ >> visible=true')).not.toBeVisible();
    });
});
