import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Platform } from 'react-native';

import { useTheme, type Theme } from '../../context/ThemeContext';
import { registerNoticeHost, type Notice, type Question } from '../../utils/notify';

/**
 * Where messages appear on the web build.
 *
 * <p>Mounted once at the root. On a phone nothing here renders: `notify` and
 * `ask` go to the platform Alert, which is what a phone user expects and what
 * their screen reader already announces.
 *
 * <h2>Errors do not fade</h2>
 *
 * <p>Success is a courtesy and can disappear on its own. An error is the thing
 * the user has to act on, and a message that vanishes before it is read is how
 * this app spent weeks failing silently. So errors stay until dismissed.
 *
 * <h2>The question modal</h2>
 *
 * <p>A question blocks. That means it has to be usable by someone who is not
 * holding a mouse:
 *
 * <ul>
 *   <li>focus moves into the dialog when it opens, and back to whatever had it
 *       when it closes -- otherwise a keyboard user is left at the top of the
 *       page with no idea where they are;</li>
 *   <li>Tab is trapped inside it, so the two answers cannot be skipped past
 *       into a page that is not supposed to be reachable;</li>
 *   <li>Escape cancels, because a dialog with no way out but a mouse click is
 *       a trap;</li>
 *   <li>dismissing -- Escape, or the backdrop -- answers NO. Never yes. These
 *       are used for destructive things, and an accidental dismissal must not
 *       be read as consent.</li>
 * </ul>
 */
export default function NoticeHost() {
  const T = useTheme();
  const styles = useMemo(() => makeStyles(T), [T]);

  const [notices, setNotices] = useState<Notice[]>([]);
  const [question, setQuestion] = useState<Question | null>(null);

  const dismiss = useCallback((id: number) => {
    setNotices((all) => all.filter((n) => n.id !== id));
  }, []);

  useEffect(() => registerNoticeHost(
    (notice) => {
      setNotices((all) => [...all, notice]);
      if (notice.kind !== 'error') {
        // Only the non-errors go on their own.
        setTimeout(() => {
          setNotices((all) => all.filter((n) => n.id !== notice.id));
        }, 4000);
      }
    },
    (q) => setQuestion(q),
  ), []);

  const close = useCallback((yes: boolean) => {
    setQuestion((current) => {
      current?.answer(yes);
      return null;
    });
  }, []);

  if (Platform.OS !== 'web') {
    return null;
  }

  return (
    <>
      {notices.length > 0 && (
        <View style={styles.stack} pointerEvents="box-none">
          {notices.map((notice) => (
            <View
              key={notice.id}
              style={[styles.notice, noticeTone(styles, notice.kind)]}
              // Errors interrupt; the rest are announced when convenient.
              accessibilityRole={notice.kind === 'error' ? 'alert' : 'text'}
              accessibilityLiveRegion={notice.kind === 'error' ? 'assertive' : 'polite'}
            >
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text style={styles.noticeTitle}>{notice.title}</Text>
                {!!notice.message && (
                  <Text style={styles.noticeBody}>{notice.message}</Text>
                )}
              </View>
              <TouchableOpacity
                onPress={() => dismiss(notice.id)}
                accessibilityRole="button"
                accessibilityLabel={`Dismiss: ${notice.title}`}
                hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}
              >
                <Text style={styles.dismiss}>✕</Text>
              </TouchableOpacity>
            </View>
          ))}
        </View>
      )}

      {!!question && <QuestionDialog question={question} onClose={close} styles={styles} />}
    </>
  );
}

function QuestionDialog({
  question, onClose, styles,
}: {
  question: Question;
  onClose: (yes: boolean) => void;
  styles: ReturnType<typeof makeStyles>;
}) {
  const card = useRef<any>(null);

  useEffect(() => {
    if (Platform.OS !== 'web' || typeof document === 'undefined') {
      return undefined;
    }
    const previous = document.activeElement as HTMLElement | null;
    const node = card.current as unknown as HTMLElement | null;

    const focusable = () => Array.from(
      node?.querySelectorAll<HTMLElement>(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
      ) ?? [],
    ).filter((el) => !el.hasAttribute('disabled'));

    // Into the dialog, so a keyboard user is where the question is.
    focusable()[0]?.focus();

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose(false); // dismissal is never consent
        return;
      }
      if (e.key !== 'Tab') {
        return;
      }
      const items = focusable();
      if (items.length === 0) {
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      // Wrap at both ends: without this, Tab walks out of the dialog into a
      // page the user is not supposed to be able to reach yet.
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKey, true);
    return () => {
      document.removeEventListener('keydown', onKey, true);
      previous?.focus?.();
    };
  }, [onClose]);

  return (
    <View style={styles.backdrop}>
      <TouchableOpacity
        style={StyleSheet.absoluteFill}
        activeOpacity={1}
        onPress={() => onClose(false)}
        accessibilityRole="button"
        accessibilityLabel="Cancel"
      />
      <View
        ref={card}
        style={styles.dialog}
        accessibilityRole="alert"
        accessibilityViewIsModal
        accessibilityLabel={question.title}
      >
        <Text style={styles.dialogTitle}>{question.title}</Text>
        {!!question.message && <Text style={styles.dialogBody}>{question.message}</Text>}
        <View style={styles.dialogButtons}>
          <TouchableOpacity
            onPress={() => onClose(false)}
            style={styles.cancel}
            accessibilityRole="button"
          >
            <Text style={styles.cancelText}>Cancel</Text>
          </TouchableOpacity>
          <TouchableOpacity
            onPress={() => onClose(true)}
            style={[styles.confirm, question.destructive && styles.confirmDestructive]}
            accessibilityRole="button"
          >
            <Text style={styles.confirmText}>{question.confirmLabel}</Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
}

function noticeTone(styles: ReturnType<typeof makeStyles>, kind: Notice['kind']) {
  if (kind === 'success') return styles.noticeSuccess;
  if (kind === 'info') return styles.noticeInfo;
  return styles.noticeError;
}

const makeStyles = (T: Theme) => StyleSheet.create({
  stack: {
    position: 'absolute', top: 12, left: 0, right: 0, zIndex: 9999,
    alignItems: 'center', gap: 8, paddingHorizontal: 16,
  },
  notice: {
    flexDirection: 'row', alignItems: 'flex-start', gap: 12,
    maxWidth: 560, width: '100%',
    paddingVertical: 12, paddingHorizontal: 16,
    borderRadius: 12, borderWidth: 1,
  },
  noticeError: { backgroundColor: T.danger50, borderColor: T.danger200 },
  noticeSuccess: { backgroundColor: T.success50, borderColor: T.success200 },
  noticeInfo: { backgroundColor: T.brand50, borderColor: T.brand100 },
  noticeTitle: { fontWeight: '700', color: T.text, fontSize: 14 },
  noticeBody: { color: T.text2, fontSize: 13, marginTop: 2 },
  dismiss: { color: T.text2, fontSize: 16, fontWeight: '700' },

  backdrop: {
    position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, zIndex: 10000,
    alignItems: 'center', justifyContent: 'center',
    backgroundColor: T.scrim, paddingHorizontal: 16,
  },
  dialog: {
    width: '100%', maxWidth: 420, borderRadius: 16, padding: 20,
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.line,
  },
  dialogTitle: { fontSize: 16, fontWeight: '800', color: T.text },
  dialogBody: { fontSize: 14, color: T.text2, marginTop: 8, lineHeight: 20 },
  dialogButtons: { flexDirection: 'row', justifyContent: 'flex-end', gap: 10, marginTop: 20 },
  cancel: { paddingVertical: 10, paddingHorizontal: 16, borderRadius: 10 },
  cancelText: { color: T.text2, fontWeight: '700', fontSize: 14 },
  confirm: {
    paddingVertical: 10, paddingHorizontal: 16, borderRadius: 10,
    backgroundColor: T.brand,
  },
  confirmDestructive: { backgroundColor: T.danger },
  confirmText: { color: T.onBrand, fontWeight: '700', fontSize: 14 },
});
