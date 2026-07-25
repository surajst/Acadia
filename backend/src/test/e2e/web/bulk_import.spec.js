const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

// Upload a CSV into the Students panel's (hidden) file input and submit for preview.
async function uploadStudentCsv(page, csv) {
  await page.goto('/web/management/upload');
  await page.locator('section[data-panel="students"] input[type="file"]').setInputFiles({
    name: 'roster.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(csv),
  });
  await page.click('button:has-text("Preview import")');
}

test.describe('Bulk student import: preview / confirm / cancel', () => {
  test.setTimeout(120000);

  test('preview is a dry run, confirm commits, cancel discards', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');

    const suffix = Date.now();
    const rollA = `PW${suffix}A`;
    const rollB = `PW${suffix}B`;
    // Row set: 1 valid, 1 valid, 1 intra-file duplicate roll (skip), 1 missing name (error), 1 bad phone (error)
    const csv =
      'FirstName,LastName,RollNumber,Grade,Section,ParentName,ParentPhone\n' +
      `Aarav,Mehta,${rollA},Grade 5,A,Rohan Mehta,+91 9812345670\n` +
      `Isha,Verma,${rollB},Grade 5,B,Sunil Verma,+91 9812345671\n` +
      `Dup,Row,${rollA},Grade 5,A,Someone Else,+91 9812345672\n` +
      `,NoName,PW${suffix}C,Grade 6,C,Parent X,+91 9812345673\n` +
      `Bad,Phone,PW${suffix}D,Grade 6,C,Parent Y,abc\n`;

    // ---- STEP 1: preview (dry run) ----
    await uploadStudentCsv(page, csv);

    // Preview banner + summary; nothing committed yet.
    await expect(page.locator('text=Preview only')).toBeVisible();
    await expect(page.locator('text=2 to create, 1 to skip, 2 with errors')).toBeVisible();

    // Exact-text match so the badges are counted, not the summary sentence
    // ("2 to create, 1 to skip, 2 with errors") which also contains these words.
    const previewPanel = page.locator('section[data-panel="students"]');
    await expect(previewPanel.getByText('Create', { exact: true })).toHaveCount(2);
    await expect(previewPanel.getByText('Skip', { exact: true })).toHaveCount(1);
    await expect(previewPanel.getByText('Error', { exact: true })).toHaveCount(2);

    // The confirm/cancel controls are present.
    await expect(page.locator('form[action="/web/management/upload/confirm"] button')).toBeVisible();
    await expect(page.locator('form[action="/web/management/upload/cancel"] button')).toBeVisible();

    // ---- STEP 2: confirm commits exactly the previewed rows ----
    await page.click('form[action="/web/management/upload/confirm"] button');
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('text=2 students imported')).toBeVisible();
    const resultPanel = page.locator('section[data-panel="students"]');
    await expect(resultPanel.getByText('Created', { exact: true })).toHaveCount(2);

    // ---- STEP 3: a second confirm has nothing pending (session cleared) ----
    // Re-upload the same file: the two committed rolls now already exist -> both skip.
    await uploadStudentCsv(page, csv);
    await expect(page.locator('text=0 to create, 3 to skip, 2 with errors')).toBeVisible();

    // ---- STEP 4: cancel discards the pending preview ----
    await page.click('form[action="/web/management/upload/cancel"] button');
    await page.waitForURL('**/web/management/upload');
    await expect(page.locator('text=Preview only')).toHaveCount(0);
    await expect(page.locator('section[data-panel="students"]')).toBeVisible();
  });

  test('tab switcher toggles Students and Staff panels', async ({ page }) => {
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/management/upload');

    const students = page.locator('section[data-panel="students"]');
    const staff = page.locator('section[data-panel="staff"]');

    // Students is the default panel.
    await expect(students).toBeVisible();
    await expect(staff).toBeHidden();

    // Switch to Staff.
    await page.click('button.tab-btn:has-text("Staff")');
    await expect(staff).toBeVisible();
    await expect(students).toBeHidden();

    // Switch back to Students.
    await page.click('button.tab-btn:has-text("Students")');
    await expect(students).toBeVisible();
    await expect(staff).toBeHidden();
  });
});
