import { Redirect } from 'expo-router';

/**
 * The teacher menu calls this "Verify", so people type /verify -- and got the
 * not-found screen, because the route is /verification. Redirecting is better
 * than renaming: /verification is already linked from several screens, and a
 * rename would turn every one of those into the same 404 from the other side.
 */
export default function VerifyRedirect() {
  return <Redirect href="/verification" />;
}
