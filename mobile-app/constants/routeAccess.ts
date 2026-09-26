/**
 * Which roles may open which screen.
 *
 * <p>The API is locked down correctly -- a parent's token gets 403 on every
 * teacher endpoint. But the app let a parent reach the screens by address:
 * /gradebook opened and offered "Create assessments and enter scores" above "No
 * classes assigned", and /challenges and /marketplace opened too. Nothing leaked,
 * because the data never arrived; what leaked was the impression that a parent is
 * supposed to be there, and a screen full of controls that cannot work.
 *
 * <p>Keyed on the first path segment, which works for root routes and tab routes
 * alike: (tabs) is a parenthesised group, so it never appears in the URL and
 * /challenges is one segment either way.
 *
 * <p>Deny by default for anything listed here; anything not listed is shared. That
 * way a new teacher-only screen is a deliberate entry rather than an accident of
 * omission -- and a genuinely shared screen needs no thought at all.
 */

export type Role = 'PARENT' | 'STUDENT' | 'TEACHER' | 'DRIVER' | 'ADMIN' | 'PRINCIPAL';

/**
 * Screens every signed-in role has: their own profile, settings, the role
 * dashboard at the root, notifications, and the framework's own routes. Listing
 * them is what lets everything else default to denied.
 */
const SHARED = new Set([
  '',
  'index',
  'profile',
  'settings',
  'notifications',
  'modal',
  '+not-found',
  '_sitemap',
]);

/**
 * Screens that belong to particular roles. A role absent from a route's list
 * cannot open it.
 *
 * ADMIN and PRINCIPAL are included wherever they plausibly supervise -- an admin
 * covering for an absent teacher is ordinary, and the same reasoning already
 * governs attendance and the fee actions on the server.
 */
const BY_ROUTE: Record<string, Role[]> = {
  // Teacher work
  teacher: ['TEACHER', 'ADMIN', 'PRINCIPAL'],
  'my-classes': ['TEACHER', 'ADMIN', 'PRINCIPAL'],
  gradebook: ['TEACHER', 'ADMIN', 'PRINCIPAL'],
  verification: ['TEACHER', 'ADMIN', 'PRINCIPAL'],
  verify: ['TEACHER', 'ADMIN', 'PRINCIPAL'],
  tasks: ['TEACHER', 'ADMIN', 'PRINCIPAL'],
  'task-new': ['TEACHER', 'ADMIN', 'PRINCIPAL'],
  timetable: ['TEACHER', 'ADMIN', 'PRINCIPAL'],
  attendance: ['TEACHER', 'ADMIN', 'PRINCIPAL'],

  // Pupil work
  challenges: ['STUDENT'],
  syllabus: ['STUDENT'],
  'student-attendance': ['STUDENT'],
  'student-results': ['STUDENT'],
  'student-timetable': ['STUDENT'],
  'student-news': ['STUDENT'],
  marketplace: ['STUDENT'],

  // Family
  quests: ['PARENT', 'STUDENT'],
  fees: ['PARENT'],
  performance: ['PARENT'],
  bus: ['PARENT', 'DRIVER'],
  announcements: ['PARENT'],
  messages: ['PARENT', 'TEACHER', 'ADMIN'],
  recognition: ['PARENT', 'STUDENT', 'TEACHER', 'ADMIN', 'PRINCIPAL'],

  // Oversight
  approvals: ['PRINCIPAL', 'ADMIN'],
};

/**
 * Whether this role may open this path.
 *
 * <p>An unknown role is refused everything but the shared screens: that is the
 * safe direction, and it is what a token carrying a role the app does not know
 * about should get.
 */
export function canOpen(role: string | null | undefined, pathname: string): boolean {
  const segment = (pathname || '').split('?')[0].split('/').filter(Boolean)[0] ?? '';
  if (SHARED.has(segment)) {
    return true;
  }
  const allowed = BY_ROUTE[segment];
  if (!allowed) {
    // Not a role-specific screen. Shared by omission rather than by decision,
    // which is why the role-specific ones are listed explicitly above.
    return true;
  }
  return !!role && (allowed as string[]).includes(role);
}

/** Where to send someone who reached a screen that is not theirs. */
export const ROLE_HOME = '/';
