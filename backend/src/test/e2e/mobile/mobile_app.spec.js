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
    await expect(page.locator('text="Hello, Arjun" >> visible=true')).toBeVisible();
    // Was 'School XP', one of three flat stat tiles the handoff header replaced.
    // The XP figures now sit in a legend labelled just "School" / "Parent", which
    // is too generic to locate on, so assert the progress caption instead -- it is
    // always rendered and a regex keeps it stable as the numbers change.
    await expect(page.locator('text=/XP to Level/ >> visible=true').first()).toBeVisible();

    // 4. Navigate to Syllabus Tab.
    //
    // Navigation here goes through getByRole. Tab labels are also rendered as
    // <h1> screen headings and hidden tabs stay mounted, so a plain text=
    // locator can resolve to a heading behind the current screen and then fail
    // on an intercepted click. A student has exactly four tabs: Dashboard,
    // Syllabus, Challenges, Profile.
    await page.getByRole('tab', { name: 'Syllabus' }).click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text="Curriculum Overview" >> visible=true')).toBeVisible();

    // Verify default 'All' view shows topic cards. Was /Grade/, which the card
    // no longer prints -- it renders the topic name over "<Subject> • <n> XP".
    // Matching the XP figure keeps this structural rather than tied to whichever
    // topics the curriculum seeder happens to produce. The bullet matters: the
    // dashboard's "200 XP to Level 2" stays mounted-but-hidden behind this tab,
    // and a bare /\d+ XP/ resolved to that instead.
    await expect(
      page.getByText(/• \d+ XP/).first()
    ).toBeVisible();

    // Click the Science filter chip. .first() is required: "Science" also
    // matches the section header rendered below the chips, and an unscoped
    // locator is a strict-mode violation rather than a real failure.
    await page.locator('text="Science" >> visible=true').first().click();
    await page.waitForLoadState('networkidle');
    // Live API returns Grade 6 Science topics (e.g. "The Wonderful World of Science")
    // Mock fallback shows "Food and Health" — assert either is present
    await expect(
      page.locator('text="The Wonderful World of Science" >> visible=true')
        .or(page.locator('text="Food and Health" >> visible=true'))
        .first()
    ).toBeVisible();



    // 5. Navigate to Attendance. It is no longer a tab: parents had eight tabs
    // and students six, so labels truncated to "Attend"/"Perfor", and five routes
    // moved to the dashboard's Quick Actions. The route still exists, so this
    // goes home first and enters it the way a student now does.
    await page.getByRole('tab', { name: 'Dashboard' }).click();
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: /Attendance/ }).click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text="Attendance Summary" >> visible=true')).toBeVisible();
    await expect(page.locator('text="Attendance History" >> visible=true')).toBeVisible();

    // 6. Navigate to Quests -- another of the five routes that moved off the tab
    // bar into Quick Actions, so it is reached from the dashboard too.
    await page.getByRole('tab', { name: 'Dashboard' }).click();
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: /Quests/ }).click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text="Parent Quests" >> visible=true')).toBeVisible();
    await expect(
      page.locator('text="No quests right now." >> visible=true').or(page.locator('text=/Status:/ >> visible=true').first())
    ).toBeVisible();
    
    await expect(page.locator('text=/Rewards/ >> visible=true').first()).toBeVisible();
    // A bare /XP/ matched the dashboard's own XP figures, which stay mounted but
    // hidden behind this screen, so .first() resolved to one of those and failed
    // the visibility check. Reward costs are the only place that renders a
    // negative XP figure, so match that shape instead.
    await expect(
      page.getByText('No rewards available yet.')
        .or(page.getByText(/-\d+ XP/).first())
    ).toBeVisible();

    // 7. Navigate to Challenges Tab
    await page.getByRole('tab', { name: 'Challenges' }).click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text="Active Challenges" >> visible=true')).toBeVisible();
    // A seeded student now HAS challenges: ScreenContentSeeder assigns four
    // teacher tasks per class, so this screen is no longer the empty state it
    // was written against. Accept either -- what matters is that the tab renders
    // rather than erroring. Challenge cards are the only place with a positive
    // "+n XP" badge (reward costs are negative, the dashboard has neither sign),
    // so that shape locates a card without naming a seeded task.
    await expect(
      page.getByText('No challenges assigned yet.')
        .or(page.getByText(/\+\d+ XP/).first())
    ).toBeVisible();

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
    await expect(page.locator('text="Hello, Ramesh" >> visible=true')
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
    
    // 4. Navigate to Syllabus Tab.
    //
    // Navigation here goes through getByRole. Tab labels are also rendered as
    // <h1> screen headings and hidden tabs stay mounted, so a plain text=
    // locator can resolve to a heading behind the current screen and then fail
    // on an intercepted click. A student has exactly four tabs: Dashboard,
    // Syllabus, Challenges, Profile.
    await page.getByRole('tab', { name: 'Syllabus' }).click();
    await page.waitForLoadState('networkidle');
    
    // The chips are built from whatever subjects the curriculum actually holds,
    // which is the point of the data-driven catalog -- so this asserts the
    // seeded Grade 6 subjects rather than a hardcoded list. It used to expect a
    // "Math" chip that the seeded CBSE curriculum has never contained.
    await expect(page.locator('text="All" >> visible=true').first()).toBeVisible();
    await expect(page.locator('text="Science" >> visible=true').first()).toBeVisible();
    await expect(page.locator('text="Social Science" >> visible=true').first()).toBeVisible();
    await expect(page.locator('text="English" >> visible=true').first()).toBeVisible();

    // Simulate clicking the 'Science' chip (.first(): the section header below
    // carries the same text)
    await page.locator('text="Science" >> visible=true').first().click();
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
    
    // The calendar colour-codes cells from the backend's PRESENT/ABSENT states.
    //
    // This used to pin exact hexes -- #22c55e on #14532d for present, #ef4444 on
    // #7f1d1d for absent. Those were the dark-background values a token codemod
    // later mapped onto *ink* (text) tokens, which took the cells to 1.45:1. So
    // the assertion was pinning a palette that was failing WCAG, and it broke the
    // moment that was fixed.
    //
    // Colour correctness now belongs to mobile-app/scripts/check-contrast.mjs,
    // which measures every colour pair in every style object against the WCAG
    // floor -- far stronger than one hardcoded hex here, and it cannot pass a
    // screen that rendered nothing. What this test is actually for is that the
    // grid renders a cell per status, so that is all it asserts.
    await expect(page.locator('text="✓" >> visible=true').first()).toBeVisible();
    await expect(page.locator('text="✗" >> visible=true').first()).toBeVisible();
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
