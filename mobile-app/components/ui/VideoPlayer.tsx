import React, { useEffect, useMemo, useRef } from 'react';
import { Platform, View, StyleSheet } from 'react-native';

import { useTheme, type Theme } from '../../context/ThemeContext';

/**
 * A YouTube player that can tell us the video finished.
 *
 * <h2>The two parameters everything depends on</h2>
 *
 * <p>`enablejsapi=1` and `origin=` are not optional decoration. Without the first,
 * YouTube posts no messages at all. Without the second -- matching the embedding
 * page exactly -- it refuses to post to that page. Either missing and "Watched ✓"
 * simply never appears, silently, behind a player that looks and plays perfectly
 * normally. That is a bug nobody notices until a teacher asks who has watched.
 *
 * <p>The server sends the address as far as `enablejsapi=1`, so the decisions about
 * youtube-nocookie and `rel` live in one place; the origin is added here, because
 * only the page knows what its own origin is. It differs between the deployed web
 * build, localhost and the native webview, which is precisely why a server cannot
 * guess it.
 *
 * <h2>Listening</h2>
 *
 * <p>The IFrame API's own JS is not loaded. It would want to own the element and
 * bring a script tag with it; all that is needed is the message it relays, which is
 * a plain `postMessage` of `{"event":"onStateChange","info":0}` -- 0 being ENDED.
 * Listening directly is less machinery and one fewer third-party script on a page
 * children use.
 *
 * <p>The origin of each message is checked. A page that accepts `onStateChange`
 * from anywhere accepts it from any iframe on it.
 */

type Props = {
  youtubeId: string;
  /** From the server: nocookie, rel=0, enablejsapi=1. Origin is appended here. */
  embedUrl: string;
  title: string;
  onFinished: () => void;
};

/** YouTube's player states. 0 is ENDED; the rest are not interesting here. */
const ENDED = 0;

const ALLOWED_SENDERS = [
  'https://www.youtube-nocookie.com',
  'https://www.youtube.com',
];

export default function VideoPlayer({ youtubeId, embedUrl, title, onFinished }: Props) {
  const T = useTheme();
  const styles = useMemo(() => makeStyles(T), [T]);
  const finished = useRef(false);

  /** The address with this page's own origin on it. */
  const src = useMemo(() => {
    if (Platform.OS !== 'web' || typeof window === 'undefined') {
      return embedUrl;
    }
    const origin = window.location.origin;
    return `${embedUrl}&origin=${encodeURIComponent(origin)}`;
  }, [embedUrl]);

  useEffect(() => {
    if (Platform.OS !== 'web' || typeof window === 'undefined') {
      return undefined;
    }
    finished.current = false;

    const onMessage = (event: MessageEvent) => {
      if (!ALLOWED_SENDERS.includes(event.origin)) {
        return;
      }
      let payload: { event?: string; info?: unknown } | null = null;
      try {
        payload = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
      } catch {
        return; // not ours; YouTube also sends things that are not JSON
      }
      if (!payload || payload.event !== 'onStateChange') {
        return;
      }
      // info is the state, sometimes wrapped in an object depending on the
      // player's version. Both shapes have meant ENDED at one time or another.
      const state = typeof payload.info === 'number'
        ? payload.info
        : (payload.info as { playerState?: number } | null)?.playerState;
      if (state !== ENDED || finished.current) {
        return;
      }
      // Once per mount. The player fires ENDED again if the child rewinds and
      // watches the last minute twice; the server is idempotent as well, but there
      // is no reason to make the request at all.
      finished.current = true;
      onFinished();
    };

    window.addEventListener('message', onMessage);
    return () => window.removeEventListener('message', onMessage);
  }, [onFinished, youtubeId]);

  if (Platform.OS === 'web') {
    return (
      <View style={styles.frame}>
        {React.createElement('iframe', {
          // Named so a test can find it, and so the two parameters above can be
          // asserted rather than assumed.
          'data-video-player': youtubeId,
          src,
          title,
          width: '100%',
          height: '100%',
          frameBorder: '0',
          allow: 'accelerometer; clipboard-write; encrypted-media; gyroscope; picture-in-picture',
          allowFullScreen: true,
          style: { border: 0, display: 'block', width: '100%', height: '100%' },
        })}
      </View>
    );
  }

  // Native. react-native-webview is already a dependency of the Expo runtime used
  // here; the same postMessage contract applies inside it, relayed to the page.
  const { WebView } = require('react-native-webview');
  return (
    <View style={styles.frame}>
      <WebView
        source={{ uri: src }}
        style={{ flex: 1, backgroundColor: 'transparent' }}
        allowsInlineMediaPlayback
        mediaPlaybackRequiresUserAction={false}
        // The webview has no window.postMessage listener of its own, so the page
        // inside it is asked to forward what YouTube sends.
        injectedJavaScript={`
          window.addEventListener('message', function (e) {
            try { window.ReactNativeWebView.postMessage(typeof e.data === 'string' ? e.data : JSON.stringify(e.data)); }
            catch (err) {}
          });
          true;
        `}
        onMessage={(event: { nativeEvent: { data: string } }) => {
          try {
            const payload = JSON.parse(event.nativeEvent.data);
            if (payload?.event === 'onStateChange' && payload?.info === ENDED && !finished.current) {
              finished.current = true;
              onFinished();
            }
          } catch {
            // not ours
          }
        }}
      />
    </View>
  );
}

const makeStyles = (T: Theme) => StyleSheet.create({
  frame: {
    width: '100%',
    aspectRatio: 16 / 9,
    borderRadius: 12,
    overflow: 'hidden',
    backgroundColor: T.line,
  },
});
