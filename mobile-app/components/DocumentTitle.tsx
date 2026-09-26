import Head from 'expo-router/head';
import { Platform } from 'react-native';
import { usePathname } from 'expo-router';

import { documentTitleFor } from '@/constants/screenTitles';

/**
 * Names the browser tab after the screen you are on.
 *
 * <p>R3-P2-4. Every tab showed the same title whatever screen was open, because
 * the only titles the app set were `options.title` on a Stack.Screen -- which
 * names the navigator's own header and does not touch `document.title`. So a
 * parent with Fees and Attendance open in two tabs could not tell them apart, and
 * a shared link arrived with nothing describing it.
 *
 * <p>Rendered once, at the root, keyed on the pathname. Per-screen `<Head>` tags
 * would be the more usual shape, but that is a tag in each of two dozen screens
 * and one more thing to forget in the next one; the titles already live in a
 * single list, so this reads the same list the headers do.
 *
 * <p>Web only, on purpose. There is no browser tab on a phone, and `Head` on
 * native is a different thing altogether -- it feeds Handoff and Siri indexing
 * and expects metadata this has nothing to say about. The finding is about
 * browser tabs, so this stays where browser tabs are.
 *
 * <p>`Head` rather than an effect that assigns `document.title`: the web build is
 * a static export, and Head puts the title into each route's pre-rendered HTML.
 * An effect would only ever correct the tab after the page had already loaded and
 * been shared with the wrong one. Expo Router mounts Head.Provider itself in its
 * entry, so no provider is needed here.
 */
export default function DocumentTitle() {
  const pathname = usePathname();

  if (Platform.OS !== 'web') {
    return null;
  }
  return (
    <Head>
      <title>{documentTitleFor(pathname)}</title>
    </Head>
  );
}
