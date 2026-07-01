const { test, expect } = require('@playwright/test');

test.describe('Native Web App E2E Tests', () => {
  // Use the Expo Web URL instead of the backend URL
  test.use({ baseURL: 'http://localhost:8081' });

  test('Student logs in and navigates all 4 native tabs', async ({ page }) => {
    // Reset database to a clean state
    await page.goto('http://localhost:8080/test/reset');

    // 1. Go to Login Screen
    await page.goto('/');
    
    // 2. Perform Login
    await page.getByPlaceholder('Email / Username').fill('arjun@gmail.com');
    await page.getByPlaceholder('Password').fill('PilotLaunchSecure2026!');
    await page.getByText('Log In').click();
    
    // 3. Wait for Navigation to Dashboard
    await page.waitForLoadState('networkidle');
    
    // Check Dashboard Tab
    await expect(page.locator('text="Hello, Arjun!" >> visible=true')).toBeVisible();
    await expect(page.locator('text="School XP" >> visible=true')).toBeVisible();

    // 4. Navigate to Syllabus Tab
    await page.locator('text="Syllabus" >> visible=true').click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text="Curriculum Overview" >> visible=true')).toBeVisible();

    // Verify default 'All' view shows topic cards
    await expect(
      page.locator('text=/Grade/ >> visible=true').first()
    ).toBeVisible();

    // Click Science chip and assert real CBSE seeded topic appears
    await page.locator('text="Science" >> visible=true').click();
    await page.waitForLoadState('networkidle');
    // Live API returns Grade 6 Science topics (e.g. "The Wonderful World of Science")
    // Mock fallback shows "Food and Health" — assert either is present
    await expect(
      page.locator('text="The Wonderful World of Science" >> visible=true')
        .or(page.locator('text="Food and Health" >> visible=true'))
        .first()
    ).toBeVisible();



    // 5. Navigate to Attendance Tab
    await page.locator('text="Attendance" >> visible=true').click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text="Attendance Summary" >> visible=true')).toBeVisible();
    await expect(page.locator('text="Attendance History" >> visible=true')).toBeVisible();

    // 6. Navigate to Quests Tab
    await page.locator('text="Quests" >> visible=true').click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text="Parent Quests" >> visible=true')).toBeVisible();
    await expect(
      page.locator('text="No quests right now." >> visible=true').or(page.locator('text=/Status:/ >> visible=true').first())
    ).toBeVisible();
    
    await expect(page.locator('text=/Rewards/ >> visible=true').first()).toBeVisible();
    await expect(
      page.locator('text="No rewards available yet." >> visible=true').or(page.locator('text="Claim" >> visible=true').first().or(page.locator('text=/XP/ >> visible=true').first()))
    ).toBeVisible();

    // 7. Navigate to Challenges Tab
    await page.locator('text="Challenges" >> visible=true').click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text="Active Challenges" >> visible=true')).toBeVisible();
    await expect(page.locator('text="Chapter Summary — Food and Health" >> visible=true')).toBeVisible();

    // 8. Navigate to Profile Tab
    await page.locator('text="Profile" >> visible=true').click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text="Account Details" >> visible=true')).toBeVisible();
    await expect(page.locator('text="Student Account" >> visible=true')).toBeVisible();
  });

  test('Parent logs in and views Attendance Calendar tab', async ({ page }) => {
    // Reset database to a clean state
    await page.goto('http://localhost:8080/test/reset');

    // 1. Login as parent
    await page.goto('/');
    await page.getByPlaceholder('Email / Username').fill('ramesh@gmail.com');
    await page.getByPlaceholder('Password').fill('PilotLaunchSecure2026!');
    await page.getByText('Log In').click();
    await page.waitForLoadState('networkidle');

    // 2. Check Parent Dashboard loaded
    await expect(page.locator('text="Hello, Ramesh!" >> visible=true')
      .or(page.locator('text="Parent Portal" >> visible=true'))
      .first()
    ).toBeVisible();

    // 3. Navigate to Attendance tab
    await page.locator('text="Attendance" >> visible=true').click();
    await page.waitForLoadState('networkidle');

    // 4. Assert core Parent-only attendance UI elements
    await expect(page.locator('text="Attendance Calendar" >> visible=true')).toBeVisible();
    await expect(page.locator('text="Monitoring" >> visible=true')).toBeVisible();
    await expect(
      page.locator('text="Present" >> visible=true').or(page.locator('text="Absent" >> visible=true')).first()
    ).toBeVisible();
  });

  test('STUDENT CURRICULUM PROFILE TEST', async ({ page }) => {
    // Reset database to a clean state
    await page.goto('http://localhost:8080/test/reset');

    // 1. Go to Login Screen
    await page.goto('/');
    
    // 2. Perform Login as Student
    await page.getByPlaceholder('Email / Username').fill('arjun@gmail.com');
    await page.getByPlaceholder('Password').fill('PilotLaunchSecure2026!');
    await page.getByText('Log In').click();
    
    // 3. Wait for Navigation to Dashboard
    await page.waitForLoadState('networkidle');
    
    // 4. Navigate to Syllabus Tab
    await page.locator('text="Syllabus" >> visible=true').click();
    await page.waitForLoadState('networkidle');
    
    // Assert that the horizontal category filter chips ('All', 'Math', 'Science') render completely
    await expect(page.locator('text="All" >> visible=true').first()).toBeVisible();
    await expect(page.locator('text="Math" >> visible=true').first()).toBeVisible();
    await expect(page.locator('text="Science" >> visible=true').first()).toBeVisible();
    
    // Simulate clicking the 'Science' chip
    await page.locator('text="Science" >> visible=true').click();
    await page.waitForLoadState('networkidle');
    
    // Assert that the interface dynamically updates to display the backend-seeded Grade 6 CBSE curriculum card: "The Wonderful World of Science"
    await expect(page.locator('text="The Wonderful World of Science" >> visible=true').first()).toBeVisible();
  });

  test('PARENT ATTENDANCE LOG TEST', async ({ page }) => {
    // Reset database to a clean state with both PRESENT and ABSENT records seeded
    await page.goto('http://localhost:8080/test/reset');

    // 1. Go to Login Screen
    await page.goto('/');
    
    // 2. Perform Login as Parent
    await page.getByPlaceholder('Email / Username').fill('ramesh@gmail.com');
    await page.getByPlaceholder('Password').fill('PilotLaunchSecure2026!');
    await page.getByText('Log In').click();
    
    // 3. Wait for Navigation to Dashboard
    await page.waitForLoadState('networkidle');
    
    // 4. Navigate directly to the newly registered Attendance tab
    await page.locator('text="Attendance" >> visible=true').click();
    await page.waitForLoadState('networkidle');
    
    // Verify the top-level student profile badge accurately reflects the linked child's initials and active class section
    await expect(page.locator('text="Arjun Sharma" >> visible=true')).toBeVisible();
    await expect(page.locator('text="A" >> visible=true').first()).toBeVisible();
    await expect(page.locator('text="Grade 6 – A" >> visible=true')).toBeVisible();
    
    // Target the summary statistic row and assert that the 'Present', 'Absent', and 'Attendance %' numeric data wrappers parse layout states cleanly without breaking
    const presentWrapper = page.locator('text="Present" >> visible=true').first();
    const absentWrapper = page.locator('text="Absent" >> visible=true').first();
    const pctWrapper = page.locator('text=/%/ >> visible=true').first();
    
    await expect(presentWrapper).toBeVisible();
    await expect(absentWrapper).toBeVisible();
    await expect(pctWrapper).toBeVisible();
    
    // Verify numeric data wrappers are parsing clean layout states (numeric stats)
    await expect(presentWrapper.locator('xpath=..')).toContainText(/\d+/);
    await expect(absentWrapper.locator('xpath=..')).toContainText(/\d+/);
    await expect(pctWrapper).toContainText(/\d+%/);
    await expect(pctWrapper.locator('xpath=..')).toContainText('Attendance');
    
    // Verify the calendar component structure, asserting that the grid cells color-code dynamically based on the backend's PRESENT and ABSENT status states
    const presentCellText = page.locator('text="✓" >> visible=true').first();
    await expect(presentCellText).toBeVisible();
    await expect(presentCellText).toHaveCSS('color', 'rgb(34, 197, 94)'); // #22c55e
    await expect(presentCellText.locator('xpath=..')).toHaveCSS('background-color', 'rgb(20, 83, 45)'); // #14532d
    
    const absentCellText = page.locator('text="✗" >> visible=true').first();
    await expect(absentCellText).toBeVisible();
    await expect(absentCellText).toHaveCSS('color', 'rgb(239, 68, 68)'); // #ef4444
    await expect(absentCellText.locator('xpath=..')).toHaveCSS('background-color', 'rgb(127, 29, 29)'); // #7f1d1d
  });

  test('ROLE-BASED ACCESS CONTROL GUARD TEST', async ({ page }) => {
    // Reset database to a clean state
    await page.goto('http://localhost:8080/test/reset');

    // 1. Go to Login Screen
    await page.goto('/');
    
    // 2. Perform Login as Student
    await page.getByPlaceholder('Email / Username').fill('arjun@gmail.com');
    await page.getByPlaceholder('Password').fill('PilotLaunchSecure2026!');
    await page.getByText('Log In').click();
    
    // 3. Wait for Navigation to Dashboard
    await page.waitForLoadState('networkidle');
    
    // 4. Simulate a Student account explicitly trying to deep-link into the '/attendance' route
    // The tab is hidden via href: null, so we test the route directly to verify the guard
    await page.goto('/attendance');
    await page.waitForLoadState('networkidle');
    
    // Assert that the UI gracefully catches the unauthorized role and displays our custom "Locked" security screen layout rather than crashing or revealing parental records
    await expect(page.locator('text="🔒" >> visible=true')).toBeVisible();
    await expect(page.locator('text="Parent View Only" >> visible=true')).toBeVisible();
    await expect(page.locator('text="This section is available for parent accounts." >> visible=true')).toBeVisible();
  });

  test('TEACHER PORTAL TAB SMOKE TEST', async ({ page }) => {
    // Reset database to a clean state
    await page.goto('http://localhost:8080/test/reset');

    // 1. Login as Teacher
    await page.goto('/');
    await page.getByPlaceholder('Email / Username').fill('teacher@greenwood.com');
    await page.getByPlaceholder('Password').fill('PilotLaunchSecure2026!');
    await page.getByText('Log In').click();
    await page.waitForLoadState('networkidle');

    // 2. Navigate to the Teacher Portal tab (My Classes)
    await page.locator('text="My Classes" >> visible=true').last().click();
    await page.waitForLoadState('networkidle');

    // 3. Assert header and roster card render correctly
    await expect(page.locator('text="My Classes" >> visible=true').first()).toBeVisible();

    // 4. Assert the Grade 6 roster card is present
    await expect(
      page.locator('text="Grade 6 – A" >> visible=true').first()
    ).toBeVisible();

    // 5. Assert the active status badge is visible
    await expect(page.locator('text="Active" >> visible=true').first()).toBeVisible();
  });
});
