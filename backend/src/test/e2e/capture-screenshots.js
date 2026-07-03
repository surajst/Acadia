// One-off script (not a test) to capture reference screenshots of each module.
// Run with: node capture-screenshots.js
const { chromium } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

const BASE_URL = 'http://localhost:8080';
const OUT_DIR = path.join(__dirname, '..', '..', '..', '..', 'screenshots');
const PASSWORD = 'PilotLaunchSecure2026!';

async function login(page, username) {
  await page.goto(`${BASE_URL}/login`);
  await page.fill('#username', username);
  await page.fill('#password', PASSWORD);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

async function logout(page) {
  await page.goto(`${BASE_URL}/web/admin/dashboard`).catch(() => {});
  await page.context().clearCookies();
}

async function shot(page, name) {
  await page.waitForTimeout(600);
  await page.screenshot({ path: path.join(OUT_DIR, `${name}.png`), fullPage: true });
  console.log('Saved', name);
}

(async () => {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

  // Reset to clean seeded state
  await page.goto(`${BASE_URL}/test/reset`);

  // ── ADMIN ──────────────────────────────────────────────────────────────
  await login(page, 'admin@greenwood.com');
  await shot(page, '01-admin-dashboard');

  await page.goto(`${BASE_URL}/web/admin/subjects`);
  await shot(page, '02-subject-catalog');

  await page.goto(`${BASE_URL}/web/onboard/setup`);
  await shot(page, '03-onboarding-wizard');

  await page.goto(`${BASE_URL}/web/admin/audit-log`);
  await shot(page, '04-audit-log');

  await page.goto(`${BASE_URL}/web/admin/timetable`);
  await shot(page, '05-timetable-management');

  await page.goto(`${BASE_URL}/web/admin/fees`);
  await shot(page, '06-fee-management');

  await logout(page);

  // ── TEACHER ────────────────────────────────────────────────────────────
  await login(page, 'teacher@greenwood.com');
  await shot(page, '07-teacher-dashboard-verification-queue');

  await page.goto(`${BASE_URL}/web/teacher/assessments`);
  await page.waitForFunction(() => {
    const s = document.getElementById('classSelect');
    return s && s.options.length > 0;
  }, { timeout: 10000 }).catch(() => {});
  await shot(page, '08-gradebook');

  await page.goto(`${BASE_URL}/web/teacher/messages`);
  await shot(page, '09-teacher-messages');

  await logout(page);

  // ── PARENT ─────────────────────────────────────────────────────────────
  await login(page, 'ramesh@gmail.com');
  await shot(page, '10-parent-portal');

  await browser.close();
  console.log('All screenshots saved to', OUT_DIR);
})();
