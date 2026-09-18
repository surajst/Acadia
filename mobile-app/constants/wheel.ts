import type { SymbolViewProps } from 'expo-symbols';

/**
 * What sits on the rim of each role's wheel.
 *
 * <p>This is the one place a destination is added or removed. Before the wheel,
 * a role's destinations were split between the bottom tab bar -- capped at five,
 * so five of the thirteen tab routes were permanently hidden behind
 * `href: null` -- and a grid of tiles on the dashboard, with no rule for which
 * lived where. The lists below are the union of both, so nothing that was
 * reachable stopped being reachable.
 */
export type WheelOption = {
  /** Stable React key. */
  key: string;
  /** Shown under the icon. Keep to ~12 characters: the slot is 78dp wide and wraps to two lines. */
  label: string;
  icon: SymbolViewProps['name'];
  /** Typed loosely on purpose -- see the note on UserWheel's router.push. */
  route: string;
};

/**
 * Below this many spokes, a ring reads as a rendering fault rather than a
 * design. Roles under it put Profile and Settings on the rim instead of in the
 * header corners, so the wheel looks deliberate at every role.
 */
export const MIN_ORBIT = 3;

export const PROFILE_OPTION: WheelOption = {
  key: 'profile', label: 'Profile', route: '/profile',
  icon: { ios: 'person', android: 'person', web: 'person' },
};

export const SETTINGS_OPTION: WheelOption = {
  key: 'settings', label: 'Settings', route: '/settings',
  icon: { ios: 'gearshape', android: 'settings', web: 'settings' },
};

const STUDENT: WheelOption[] = [
  { key: 'timetable',  label: 'Timetable',  route: '/student-timetable',  icon: { ios: 'clock',        android: 'schedule',      web: 'schedule' } },
  { key: 'syllabus',   label: 'Syllabus',   route: '/syllabus',           icon: { ios: 'book',         android: 'book',          web: 'book' } },
  { key: 'results',    label: 'Results',    route: '/student-results',    icon: { ios: 'chart.bar',     android: 'bar_chart',     web: 'bar_chart' } },
  { key: 'attendance', label: 'Attendance', route: '/student-attendance', icon: { ios: 'calendar',      android: 'event',         web: 'event' } },
  { key: 'quests',     label: 'Quests',     route: '/quests',             icon: { ios: 'star',          android: 'star',          web: 'star' } },
  { key: 'challenges', label: 'Challenges', route: '/challenges',         icon: { ios: 'star.circle',   android: 'military_tech', web: 'military_tech' } },
  { key: 'rewards',    label: 'Rewards',    route: '/marketplace',        icon: { ios: 'gift',          android: 'redeem',        web: 'redeem' } },
  { key: 'news',       label: 'News',       route: '/student-news',       icon: { ios: 'megaphone',      android: 'campaign',      web: 'campaign' } },
];

const PARENT: WheelOption[] = [
  { key: 'attendance',  label: 'Attendance',  route: '/attendance',    icon: { ios: 'calendar',   android: 'event',             web: 'event' } },
  { key: 'fees',        label: 'Fees',        route: '/fees',          icon: { ios: 'creditcard', android: 'payments',          web: 'payments' } },
  { key: 'performance', label: 'Performance', route: '/performance',   icon: { ios: 'chart.bar',  android: 'bar_chart',         web: 'bar_chart' } },
  { key: 'messages',    label: 'Messages',    route: '/messages',      icon: { ios: 'message',    android: 'chat',              web: 'chat' } },
  { key: 'bus',         label: 'Bus',         route: '/bus',           icon: { ios: 'bus',        android: 'directions_bus',    web: 'directions_bus' } },
  { key: 'news',        label: 'News',        route: '/announcements', icon: { ios: 'megaphone',  android: 'campaign',          web: 'campaign' } },
  { key: 'recognition', label: 'Recognition', route: '/recognition',   icon: { ios: 'rosette',    android: 'workspace_premium', web: 'workspace_premium' } },
];

const TEACHER: WheelOption[] = [
  { key: 'classes',      label: 'My Classes', route: '/teacher',      icon: { ios: 'person.2',                    android: 'school',   web: 'school' } },
  { key: 'timetable',    label: 'Timetable',  route: '/timetable',    icon: { ios: 'clock',                       android: 'schedule', web: 'schedule' } },
  { key: 'tasks',        label: 'Tasks',      route: '/tasks',        icon: { ios: 'checklist',                   android: 'task_alt', web: 'task_alt' } },
  { key: 'gradebook',    label: 'Gradebook',  route: '/gradebook',    icon: { ios: 'chart.bar.doc.horizontal',    android: 'grading',  web: 'grading' } },
  { key: 'verification', label: 'Verify',     route: '/verification', icon: { ios: 'checkmark.seal',              android: 'verified', web: 'verified' } },
  { key: 'messages',     label: 'Messages',   route: '/messages',     icon: { ios: 'message',                     android: 'chat',     web: 'chat' } },
  { key: 'newTask',      label: 'New task',   route: '/task-new',     icon: { ios: 'plus',                        android: 'add',      web: 'add' } },
];

// One destination of their own. MIN_ORBIT lifts this to three.
const PRINCIPAL: WheelOption[] = [
  { key: 'approvals', label: 'Approvals', route: '/approvals', icon: { ios: 'checkmark.seal', android: 'verified', web: 'verified' } },
];

// None. A driver's job is the trip button, which stays on the home screen
// below the wheel: it is a stateful toggle, not a place to go.
const DRIVER: WheelOption[] = [];

const BY_ROLE: Record<string, WheelOption[]> = {
  STUDENT, PARENT, TEACHER, PRINCIPAL, DRIVER,
};

/**
 * The rim for a role, already padded to {@link MIN_ORBIT}.
 *
 * <p>ADMIN is deliberately absent: `(tabs)/_layout.tsx` returns the web-only
 * screen before the navigator mounts, so an admin never renders a wheel. The
 * empty fallback means that if that guard is ever removed, the result is a bare
 * hub rather than a crash.
 *
 * <p>`/notifications` is deliberately excluded for teachers -- it is an inbox,
 * not a place, and already has a badge-carrying bell in the header.
 */
export function wheelOptionsFor(role: string | null | undefined): WheelOption[] {
  const own = (role && BY_ROLE[role]) || [];
  if (own.length === 0 && !role) return [];
  return own.length < MIN_ORBIT ? [...own, PROFILE_OPTION, SETTINGS_OPTION] : own;
}

/**
 * Whether the header should draw its Profile/Settings corners. False for the
 * roles whose wheel already carries them, so they are never offered twice.
 */
export function showsHeaderCorners(role: string | null | undefined): boolean {
  const own = (role && BY_ROLE[role]) || [];
  return own.length >= MIN_ORBIT;
}
