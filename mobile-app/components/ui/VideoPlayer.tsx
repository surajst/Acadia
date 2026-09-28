import React, { useCallback, useEffect, useMemo, useRef } from 'react';
import { Platform, View, StyleSheet } from 'react-native';

import { useTheme, type Theme } from '../../context/ThemeContext';

/**
 * A YouTube player that can tell us the video finished.
 *
 * <h2>The two parameters everything depends on</h2>
 *
 * <p>`enablejsapi=1` and `origin=` are not optional decoration. Without the first,
 * YouTube posts no messages at all. Without the second -- matching the embedding
 * page exactly -- it refuses to post to that page.
 *
 * <p>And they are not sufficient. There are two further steps, and each of them
 * shipped missing in turn, because each fails in the same invisible way -- the
 * video plays perfectly and the tick never comes.
 *
 * <p>First, the listening handshake: the parent posts
 * `{"event":"listening","id":n,"channel":"widget"}` into the iframe. (An earlier
 * note here claimed YouTube stays completely silent without it. Not so -- QA
 * observed onReady arriving unprompted. The handshake is still sent, since it is
 * what the protocol asks for, but it is not the thing that was withholding state.)
 *
 * <p>Second, and this is what actually kept "Watched" empty: onStateChange has to
 * be SUBSCRIBED to. Until the parent posts
 * `{"event":"command","func":"addEventListener","args":["onStateChange"],...}`,
 * YouTube never sends one. What it does send, unprompted, is `infoDelivery` with
 * `info.playerState` -- a full watch produced -1, 3, 1, 2, 0 -- and the app read
 * only onStateChange, so it discarded the end of every video.
 *
 * <p>So both are done now: subscribe on onReady, and read the state out of
 * infoDelivery too. Either alone can fail quietly, which is the entire history of
 * this component.
 *
 * <p>The tests earned none of the confidence they gave. Each version stubbed the
 * message the app already understood, so each passed against code that could not
 * work against the real player. The stub now replays a recorded playback.
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

/** What the parent posts to be sent state changes at all. */
const SUBSCRIBE = {
  event: 'command',
  func: 'addEventListener',
  args: ['onStateChange'],
  id: 1,
  channel: 'widget',
};

type PlayerMessage = {
  event?: string;
  info?: number | { playerState?: number } | null;
};

/**
 * The player's state, from whichever message carried it.
 *
 * <p>Three shapes, and the app previously understood only the first:
 *
 * <ul>
 *   <li>`onStateChange` with `info` as a bare number. Only ever arrives after
 *       the parent has subscribed.</li>
 *   <li>`onStateChange` with `info` as `{playerState}`. Some player versions.</li>
 *   <li>`infoDelivery` with `info.playerState`. This is what actually turns up
 *       during an ordinary playback, unprompted, and it is the one that was
 *       being dropped. A full watch produced -1, 3, 1, 2 then 0 and the app
 *       ignored every one of them.</li>
 * </ul>
 *
 * <p>Reading the state out of infoDelivery as well as onStateChange means the
 * tick no longer depends on the subscription having been accepted. Both are
 * done, because either alone has a way of failing quietly.
 */
function playerStateOf(payload: PlayerMessage): number | undefined {
  if (payload.event !== 'onStateChange' && payload.event !== 'infoDelivery') {
    return undefined;
  }
  if (typeof payload.info === 'number') {
    return payload.info;
  }
  return payload.info?.playerState;
}

const ALLOWED_SENDERS = [
  'https://www.youtube-nocookie.com',
  'https://www.youtube.com',
];

/** The origin the embed is served from, and so where the handshake is sent. */
const PLAYER_ORIGIN = 'https://www.youtube-nocookie.com';

export default function VideoPlayer({ youtubeId, embedUrl, title, onFinished }: Props) {
  const T = useTheme();
  const styles = useMemo(() => makeStyles(T), [T]);
  const finished = useRef(false);
  const frame = useRef<HTMLIFrameElement | null>(null);
  const heard = useRef(false);

  /** The address with this page's own origin on it. */
  const src = useMemo(() => {
    if (Platform.OS !== 'web' || typeof window === 'undefined') {
      return embedUrl;
    }
    const origin = window.location.origin;
    return `${embedUrl}&origin=${encodeURIComponent(origin)}`;
  }, [embedUrl]);

  /**
   * Ask to be told about state changes.
   *
   * <p>Separate from the listening handshake and easy to mistake for it. The
   * handshake gets the player talking; this asks it to talk about the one thing
   * we care about. Sent on onReady, when there is definitely something at the
   * other end to receive it.
   */
  const subscribe = useCallback(() => {
    if (Platform.OS !== 'web') {
      return;
    }
    frame.current?.contentWindow?.postMessage(
      JSON.stringify(SUBSCRIBE), PLAYER_ORIGIN,
    );
  }, []);

  useEffect(() => {
    if (Platform.OS !== 'web' || typeof window === 'undefined') {
      return undefined;
    }
    finished.current = false;

    const onMessage = (event: MessageEvent) => {
      if (!ALLOWED_SENDERS.includes(event.origin)) {
        return;
      }
      let payload: PlayerMessage | null = null;
      try {
        payload = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
      } catch {
        return; // not ours; YouTube also sends things that are not JSON
      }
      // Anything at all from the player means it is talking to us.
      heard.current = true;
      if (!payload) {
        return;
      }

      // onReady is not the end of the conversation, it is the start of it.
      // Subscribing is a separate request, and without it onStateChange never
      // comes -- see the note above the component.
      if (payload.event === 'onReady') {
        subscribe();
        return;
      }

      const state = playerStateOf(payload);
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
  }, [onFinished, youtubeId, subscribe]);

  /**
   * Tell the player we are listening.
   *
   * <p>Until this arrives, YouTube sends nothing at all -- not onReady, not a
   * single state change. It is the half that was missing, and its absence is
   * invisible: the video plays, and the tick never comes.
   *
   * <p>Repeated a few times because the iframe's load event and the player's own
   * readiness are not the same moment, and a handshake that arrives too early is
   * simply dropped. It stops as soon as anything is heard back, so the usual case
   * is one message.
   */
  const startListening = useCallback(() => {
    if (Platform.OS !== 'web') {
      return;
    }
    let attempts = 0;
    const send = () => {
      const target = frame.current?.contentWindow;
      if (!target) {
        return;
      }
      attempts += 1;
      target.postMessage(
        JSON.stringify({ event: 'listening', id: 1, channel: 'widget' }),
        PLAYER_ORIGIN,
      );
      target.postMessage(JSON.stringify(SUBSCRIBE), PLAYER_ORIGIN);
      if (attempts < 5 && !heard.current) {
        window.setTimeout(send, 400);
      }
    };
    send();
  }, []);

  return (
    <View style={styles.frame}>
      {React.createElement('iframe', {
        // Named so a test can find it, and so the two parameters above can be
        // asserted rather than assumed.
        'data-video-player': youtubeId,
        ref: frame,
        onLoad: startListening,
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

const makeStyles = (T: Theme) => StyleSheet.create({
  frame: {
    width: '100%',
    aspectRatio: 16 / 9,
    borderRadius: 12,
    overflow: 'hidden',
    backgroundColor: T.line,
  },
});
