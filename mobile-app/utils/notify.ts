import { Alert, Platform } from 'react-native';

/**
 * Telling the user something, on every platform.
 *
 * <h2>Why this exists</h2>
 *
 * <p>`Alert.alert` is a **no-op on React Native Web**. It does not throw and it
 * does not warn -- it returns, and nothing appears. So every message delivered
 * through it was silent on the web build, which is the build QA and the pilot
 * parents actually use. A profile photo upload could fail, be caught, be
 * reported, and be shown to nobody. A gradebook assessment could fail to be
 * created and the screen would carry on as if it had.
 *
 * <h2>Two kinds of message, and they are not interchangeable</h2>
 *
 * <p>{@link notify} is a statement: something happened, here is what. It does
 * not block, and the user can carry on.
 *
 * <p>{@link ask} is a question: nothing proceeds until it is answered. Those
 * cannot be toasts. A toast for "send this back to the student?" either does
 * nothing or does something irreversible while the user is reading it.
 *
 * <h2>How it reaches the screen</h2>
 *
 * <p>A module-level listener rather than a React context, so that a call site
 * is a one-line swap from `Alert.alert` and code that is not a component --
 * a service, a context's catch block -- can use it too. `NoticeHost` subscribes
 * once at the root.
 */

export type NoticeKind = 'error' | 'success' | 'info';

export type Notice = {
  id: number;
  kind: NoticeKind;
  title: string;
  message?: string;
};

export type Question = {
  id: number;
  title: string;
  message?: string;
  confirmLabel: string;
  destructive: boolean;
  answer: (yes: boolean) => void;
};

type NoticeListener = (notice: Notice) => void;
type QuestionListener = (question: Question) => void;

let noticeListener: NoticeListener | null = null;
let questionListener: QuestionListener | null = null;
let nextId = 1;

/** Called by NoticeHost when it mounts. Returns an unsubscribe. */
export function registerNoticeHost(
  onNotice: NoticeListener, onQuestion: QuestionListener,
): () => void {
  noticeListener = onNotice;
  questionListener = onQuestion;
  return () => {
    noticeListener = null;
    questionListener = null;
  };
}

/**
 * Say something. Does not block.
 *
 * <p>On a phone this is the platform Alert, which is what a phone user expects.
 * On the web it is an in-page notice: errors stay until dismissed, because an
 * error that vanishes before it is read is the problem this file exists to fix;
 * anything else fades on its own.
 */
export function notify(title: string, message?: string, kind: NoticeKind = 'error'): void {
  if (Platform.OS !== 'web') {
    Alert.alert(title, message);
    return;
  }
  if (!noticeListener) {
    // Only reachable before the host has mounted. Ugly beats silent, which is
    // the whole point -- but if this ever fires in practice, the host is
    // mounted too late and that is the bug to fix.
    console.warn('notify() before NoticeHost mounted:', title, message);
    return;
  }
  noticeListener({ id: nextId++, kind, title, message });
}

/** Shorthand for the happy path, which is allowed to fade. */
export function notifySuccess(title: string, message?: string): void {
  notify(title, message, 'success');
}

/**
 * Ask something, and wait for the answer.
 *
 * <p>Resolves true for the confirming action and false for cancel, including
 * when the user dismisses it with Escape or by tapping outside -- dismissal is
 * cancellation, never confirmation. Anything destructive has to be chosen
 * deliberately.
 */
export function ask(
  title: string,
  message?: string,
  options: { confirmLabel?: string; destructive?: boolean } = {},
): Promise<boolean> {
  const confirmLabel = options.confirmLabel ?? 'OK';
  const destructive = options.destructive ?? false;

  if (Platform.OS !== 'web') {
    return new Promise((resolve) => {
      Alert.alert(title, message, [
        { text: 'Cancel', style: 'cancel', onPress: () => resolve(false) },
        {
          text: confirmLabel,
          style: destructive ? 'destructive' : 'default',
          onPress: () => resolve(true),
        },
      ], { onDismiss: () => resolve(false) });
    });
  }

  if (!questionListener) {
    // Refusing is the safe answer. A question nobody can see must not be
    // treated as a yes.
    console.warn('ask() before NoticeHost mounted:', title);
    return Promise.resolve(false);
  }

  return new Promise((resolve) => {
    questionListener!({
      id: nextId++, title, message, confirmLabel, destructive, answer: resolve,
    });
  });
}
