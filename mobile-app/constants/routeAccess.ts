import { wheelOptionsFor, type WheelOption } from './wheel';

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
 * <h2>Derived from the wheel, not written out again</h2>
 *
 * <p>The first version of this file listed every route and its roles by hand, and
 * got {@code /attendance} backwards -- it read as the teacher's register, but
 * {@code (tabs)/attendance.tsx} is the parent's attendance calendar and is on the
 * parent's wheel. The guard then turned away the very people the screen is for,
 * and two long-standing tests caught it.
 *
 * <p>So the roles come from {@link wheelOptionsFor}, which is already the one
 * place a role's destinations are declared. A guard that reads the same list the
 * navigation is built from cannot disagree with it: putting a screen on a role's
 * wheel grants that role the screen, and taking it off withdraws it. Only the
 * handful of screens that are on nobody's wheel are named here, and each one says
 * why.
 *
 * <p>Keyed on the first path segment, which works for root routes and tab routes
 * alike: (tabs) is a parenthesised group, so it never appears in the URL and
 * /challenges is one segment either way.
 */

export type Role = 'PARENT' | 'STUDENT' | 'TEACHER' | 'DRIVER' | 'ADMIN' | 'PRINCIPAL';

/**
 * Screens every signed-in role has: their own profile, settings, the role
 * dashboard at the root, notifications, and the framework's own routes.
 */
const SHARED = new Set([
  '',
  'index',
  'profile',
  'settings',
  'notifications',
  'modal',
  '+not-found',
  '+html',
  '_sitemap',
]);

/**
 * Routes that only redirect somewhere else, and so carry whatever access their
 * destination has. Both exist because the menus name the screen one thing and the
 * router another (see app/verify.tsx and app/my-classes.tsx); denying them while
 * allowing their targets would restore exactly the 404 they were added to fix.
 */
const ALIASES: Record<string, string> = {
  'my-classes': 'teacher',
  verify: 'verification',
};

/**
 * Screens on nobody's wheel, with the reason.
 *
 * <p>A principal's approvals queue is reached from their home screen rather than a
 * wheel -- with one destination there is no wheel to be on.
 */
const OFF_WHEEL: Partial<Record<Role, string[]>> = {
  PRINCIPAL: ['approvals'],
  ADMIN: ['approvals'],
};

/**
 * Roles that may open a teacher's screens as well as their own.
 *
 * <p>An admin covering for an absent teacher is ordinary, and the same reasoning
 * already governs attendance and the fee actions on the server. Neither role has
 * a wheel of its own -- {@code (tabs)/_layout.tsx} sends both to the web console
 * on mobile -- so without this they would reach nothing but the shared screens.
 */
const SUPERVISORS: Role[] = ['ADMIN', 'PRINCIPAL'];

/** The first segment of a path, which is what a screen is keyed on. */
function firstSegment(pathname: string): string {
  return (pathname || '').split('?')[0].split('/').filter(Boolean)[0] ?? '';
}

function allowedFor(role: Role): Set<string> {
  const allowed = new Set(SHARED);
  const add = (o: WheelOption) => {
    const segment = firstSegment(o.route);
    if (segment) allowed.add(segment);
  };

  wheelOptionsFor(role).forEach(add);
  if (SUPERVISORS.includes(role)) {
    wheelOptionsFor('TEACHER').forEach(add);
  }
  (OFF_WHEEL[role] ?? []).forEach((segment) => allowed.add(segment));

  // An alias is only as open as what it redirects to.
  for (const [alias, target] of Object.entries(ALIASES)) {
    if (allowed.has(target)) allowed.add(alias);
  }
  return allowed;
}

/** Worked out once per role; the wheel is a module constant, so it cannot change. */
const cache = new Map<string, Set<string>>();

/**
 * Whether this role may open this path.
 *
 * <p>Anything not reachable for the role is refused, rather than allowed for want
 * of an entry: a new screen nobody can open is a visible mistake, where a new
 * screen everybody can open is the bug this file exists to stop. Put it on a
 * role's wheel and the role has it.
 *
 * <p>An unknown role gets the shared screens and nothing else -- the safe
 * direction, and what a token carrying a role the app does not know about should
 * get.
 */
export function canOpen(role: string | null | undefined, pathname: string): boolean {
  const segment = firstSegment(pathname);
  if (SHARED.has(segment)) {
    return true;
  }
  if (!role) {
    return false;
  }
  let allowed = cache.get(role);
  if (!allowed) {
    allowed = allowedFor(role as Role);
    cache.set(role, allowed);
  }
  return allowed.has(segment);
}

/** Where to send someone who reached a screen that is not theirs. */
export const ROLE_HOME = '/';
