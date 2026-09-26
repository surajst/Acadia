/**
 * What each screen is called.
 *
 * <p>One list, read twice: the stack header uses it for the title above the
 * screen, and DocumentTitle uses it for the browser tab. Those were separate
 * concerns until R3-P2-4 found that only the first was ever set -- Expo Router's
 * `options.title` names the navigator's header and does not touch
 * `document.title`, so every tab in the browser showed the same thing whatever
 * screen you were on.
 *
 * <p>Declared here rather than inline in the two layouts so a new screen cannot
 * get one and not the other. It is `as const` and read by property, not by
 * lookup, so a misspelled name is a compile error instead of an undefined title.
 *
 * <p>Two layouts because the routes are in two groups: the root stack and the
 * (tabs) group. A route's key is its file name, which is also its first URL
 * segment -- (tabs) is parenthesised, so it never appears in the address.
 */

/** Matching the portal, which has titled every page "Screen · ACADIA" all along. */
export const APP_NAME = 'ACADIA';

export const SCREEN_TITLES = {
  // Root stack
  notifications: 'Notifications',
  teacher: 'My Classes',
  verification: 'Verification Queue',
  tasks: 'Tasks',
  'task-new': 'New task',
  gradebook: 'Gradebook',
  timetable: 'Timetable',
  'student-timetable': 'Timetable',
  'student-news': 'School News',
  'student-results': 'My Results',
  marketplace: 'Rewards',
  recognition: 'Recognition',
  'my-classes': 'My Classes',
  verify: 'Verification Queue',

  // (tabs)
  syllabus: 'Syllabus',
  'student-attendance': 'Attendance',
  challenges: 'Challenges',
  quests: 'Quests',
  attendance: 'Attendance',
  performance: 'Performance',
  approvals: 'Approvals',
  fees: 'Fees',
  bus: 'Bus',
  announcements: 'News',
  messages: 'Messages',
  profile: 'Profile',
  settings: 'Settings',
} as const;

export type ScreenName = keyof typeof SCREEN_TITLES;

/**
 * The browser tab's text for a path.
 *
 * <p>The home screen gets the brand alone: it is the one screen whose name would
 * be a tautology, and "ACADIA" is what somebody scanning a row of tabs needs to
 * see. Anything unlisted gets the same, which is the safe direction -- a screen
 * nobody titled shows the app's name rather than a stale one left by the screen
 * before it, which is what an effect-free title does on a client-side route
 * change.
 */
export function documentTitleFor(pathname: string): string {
  const segment = (pathname || '').split('?')[0].split('/').filter(Boolean)[0] ?? '';
  const screen = (SCREEN_TITLES as Record<string, string>)[segment];
  return screen ? `${screen} · ${APP_NAME}` : APP_NAME;
}
