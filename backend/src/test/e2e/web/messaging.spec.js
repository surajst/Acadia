const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

test.describe('Sprint 6: Direct parent-teacher messaging', () => {
  test.setTimeout(120000);

  test('Teacher starts a conversation, parent replies, teacher sees the reply', async ({ page }) => {
    const suffix = Date.now();
    const openingMessage = `Homework check-in ${suffix}`;
    const replyMessage = `Thanks for the update ${suffix}`;

    // Teacher starts a conversation with Arjun Sharma's parent
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/teacher/messages');
    await page.waitForFunction(() => {
      const s = document.getElementById('newMessageStudent');
      return s && s.options.length > 0 && s.options[0].value !== '';
    }, { timeout: 10000 });
    const studentValue = await page.evaluate(() => {
      const s = document.getElementById('newMessageStudent');
      const opt = Array.from(s.options).find(o => o.textContent.includes('Arjun Sharma'));
      return opt ? opt.value : null;
    });
    await page.selectOption('#newMessageStudent', studentValue);
    await page.fill('#newMessageBody', openingMessage);
    await page.click('#sendNewMessageBtn');
    await expect(page.locator('#threadMessages')).toContainText(openingMessage, { timeout: 10000 });

    // Parent sees the conversation with an unread indicator and opens it
    await page.context().clearCookies();
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/parent/portal');
    await page.locator('#messages-section').scrollIntoViewIfNeeded();
    await page.waitForFunction((msg) => {
      const list = document.getElementById('msgConversationList');
      return list && list.textContent.includes(msg);
    }, openingMessage, { timeout: 10000 });

    const convItem = page.locator('#msgConversationList [data-conv-id]', { hasText: openingMessage });
    await expect(convItem).toBeVisible();
    await convItem.click();
    await expect(page.locator('#msgThreadMessages')).toContainText(openingMessage, { timeout: 10000 });

    // Parent replies
    await page.fill('#msgReplyBody', replyMessage);
    await page.click('#msgSendReplyBtn');
    await expect(page.locator('#msgThreadMessages')).toContainText(replyMessage, { timeout: 10000 });

    // Teacher sees the parent's reply
    await page.context().clearCookies();
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/teacher/messages');
    await page.waitForFunction((msg) => {
      const list = document.getElementById('conversationList');
      return list && list.textContent.includes(msg);
    }, replyMessage, { timeout: 10000 });

    const teacherConvItem = page.locator('#conversationList [data-id]', { hasText: 'Arjun Sharma' }).first();
    await teacherConvItem.click();
    await expect(page.locator('#threadMessages')).toContainText(replyMessage, { timeout: 10000 });
  });

  test('A parent not linked to the student cannot access that conversation', async ({ page }) => {
    const suffix = Date.now();
    const unrelatedParentEmail = `unrelated-parent-${suffix}@greenwood.com`;

    // Teacher starts a fresh conversation with Arjun Sharma's actual parent
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');
    const startRes = await page.request.post('/api/messages/conversations/start', {
      data: { studentId: '00000000-0000-0000-0000-000000000091', body: 'Access boundary check message' },
    });
    const conv = await startRes.json();

    // Create a parent in the same tenant with no link to Arjun
    await page.context().clearCookies();
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
    await page.evaluate(async (email) => {
      const params = new URLSearchParams({
        firstName: 'Unrelated', lastName: 'Parent', loginEmail: email, loginPassword: 'PilotLaunchSecure2026!',
      });
      await fetch('/web/admin/parent/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params.toString(),
      });
    }, unrelatedParentEmail);

    // That parent cannot read Arjun's conversation
    await page.context().clearCookies();
    await login(page, unrelatedParentEmail, 'PilotLaunchSecure2026!');
    const response = await page.request.get(`/api/messages/conversations/${conv.id}`);
    expect(response.status()).toBe(403);
  });

});
