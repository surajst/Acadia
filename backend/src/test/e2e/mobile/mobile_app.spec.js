const { test, expect } = require('./fixtures');

test.describe('Native Web App E2E Tests', () => {
  // Use the Expo Web URL instead of the backend URL
  test.use({ baseURL: 'http://localhost:8081' });

  test('Student logs in and reaches every screen from the wheel', async ({ page }) => {
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

    // 4. Navigate to Syllabus.
    //
    // There is no tab bar any more: every destination is a spoke on the home
    // screen's wheel, and each spoke is a button labelled "<Name>, <i> of <n>"
    // so a screen reader can say where it sits on the rim. Anchoring the match
    // at the start keeps it off the screen headings of the same name, which are
    // still mounted behind the current screen.
    await page.getByRole('button', { name: /^Syllabus/ }).click();
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



    // 5. Navigate to Attendance. Getting back to the wheel is a back
    // navigation now rather than a tab press -- which is worth exercising,
    // since the whole point of making (tabs) a Stack was that destinations
    // push and return.
    await page.goBack();
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: /^Attendance/ }).click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text="Attendance Summary" >> visible=true')).toBeVisible();
    await expect(page.locator('text="Attendance History" >> visible=true')).toBeVisible();

    // 6. Navigate to Quests, another spoke.
    await page.goBack();
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: /^Quests/ }).click();
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
    // "No rewards available yet." never existed in the app, so this only ever
    // passed through the negative-XP branch -- which was satisfied by a
    // hardcoded "Extra Screen Time -100 XP" card standing in for the empty
    // state. That invented card is gone, so the test was resting on data no
    // school had. It now accepts the real empty state or a real reward cost.
    await expect(
      page.getByText('Nothing to claim yet')
        .or(page.getByText('No rewards set up'))
        .or(page.getByText(/-\d+ XP/).first())
    ).toBeVisible();

    // 7. Navigate to Challenges, another spoke.
    await page.goBack();
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', { name: /^Challenges/ }).click();
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

    // 8. Navigate to Profile. It was a visible tab for every role; it is now
    // the avatar in the home screen's top-left corner, which carries the
    // accessible name but no visible text -- so this matches the button's
    // label rather than looking for the word on screen. Worth having as a
    // test: that corner is the only way to Profile now, and therefore the
    // only way to Log Out.
    await page.goBack();
    await page.waitForLoadState('networkidle');
    // exact: true matters. getByRole's name is a case-insensitive SUBSTRING
    // match by default, and the wheel's hub is labelled "Add a profile
    // picture" -- which contains "profile", so a loose match finds two buttons
    // and fails strict mode.
    await page.getByRole('button', { name: 'Profile', exact: true }).click();
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
    // on an intercepted click. Syllabus is a spoke on the home wheel.
    await page.getByRole('button', { name: /^Syllabus/ }).click();
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
    await page.goto('/attendance');
    await page.waitForLoadState('networkidle');

    // Same intent as before -- a pupil must not reach their parent's attendance
    // record -- but the answer changed with R3-P1-5. This screen used to render a
    // "Parent View Only" lock card, and the route guard now turns the pupil round
    // at the door instead, so the lock card never gets the chance to draw.
    //
    // Sent home, which is the stronger outcome: a locked screen is still a screen
    // that is not theirs, with the app's furniture around it.
    expect(await page.evaluate(() => window.location.pathname)).toBe('/');
    // And none of the parent's record came with them.
    await expect(page.locator('text="Parent View Only" >> visible=true')).toHaveCount(0);
    await expect(page.locator('text="Attendance Calendar" >> visible=true')).toHaveCount(0);
  });

  test('Unread notifications appear under the wheel and open what they are about', async ({ page }) => {
    // /test/reset seeds three unread notifications for this teacher --
    // ATTENDANCE, TASK and ANNOUNCEMENT.
    await page.goto('http://localhost:8080/test/reset');

    await page.goto('/');
    await page.getByPlaceholder('Email / Username').fill('teacher@greenwood.com');
    await page.getByPlaceholder('Password').fill('PilotLaunchSecure2026!');
    await page.getByText('Log In').click();
    await page.waitForLoadState('networkidle');

    // 1. The strip renders what is waiting, under the wheel.
    await expect(page.locator('text="WAITING FOR YOU" >> visible=true')).toBeVisible();
    await expect(page.locator('text="Attendance Reminder" >> visible=true')).toBeVisible();

    // 2. Tapping one opens the screen it is about. A teacher's attendance
    //    notification means "you still have a register open", which is
    //    answered from their class list.
    await page.locator('text="Attendance Reminder" >> visible=true').click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text="My Classes" >> visible=true').first()).toBeVisible();

    // 3. And it is marked read: coming back, that row is gone while the
    //    two that were not tapped remain.
    await page.goBack();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text="Attendance Reminder" >> visible=true')).toHaveCount(0);
    await expect(page.locator('text="New Task Submitted" >> visible=true')).toBeVisible();
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
