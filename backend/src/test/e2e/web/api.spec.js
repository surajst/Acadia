const { test, expect } = require('@playwright/test');

test('Teacher Tasks Autocomplete Isolate Class 6', async ({ page }) => {
    await page.goto('/test/reset');
    await page.goto('/login');
    await page.fill('#username', 'teacher@greenwood.com');
    await page.fill('#password', 'PilotLaunchSecure2026!');
    await page.click('button[type="submit"]');
    await page.waitForLoadState('networkidle');

    const response = await page.evaluate(async () => {
        const res = await fetch('/api/teacher/my-students?q=ar');
        return await res.json();
    });
    
    console.log("API RESPONSE:", response);
});

test('Student Attendance API returns 200', async ({ page, request }) => {
    await page.goto('/test/reset');
    await page.goto('/login');
    await page.fill('#username', 'arjun@gmail.com');
    await page.fill('#password', 'PilotLaunchSecure2026!');
    await page.click('button[type="submit"]');
    await page.waitForLoadState('networkidle');

    const res = await page.request.get('/api/student/attendance');
    expect(res.ok()).toBeTruthy();
    const data = await res.json();
    expect(Array.isArray(data)).toBeTruthy();
});

test('Parent Attendance and Syllabus API returns 200', async ({ page, request }) => {
    await page.goto('/test/reset');
    await page.goto('/login');
    await page.fill('#username', 'ramesh@gmail.com');
    await page.fill('#password', 'PilotLaunchSecure2026!');
    await page.click('button[type="submit"]');
    await page.waitForLoadState('networkidle');

    const attRes = await page.request.get('/api/parent/child-attendance');
    expect(attRes.ok()).toBeTruthy();
    
    const sylRes = await page.request.get('/api/parent/child-syllabus');
    expect(sylRes.ok()).toBeTruthy();
});
