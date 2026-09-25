import { Redirect } from 'expo-router';

/**
 * "My Classes" in the menu, /teacher in the router. Same reasoning as
 * app/verify.tsx: the guessable address people actually type should land
 * somewhere, and the existing route keeps working.
 */
export default function MyClassesRedirect() {
  return <Redirect href="/teacher" />;
}
