import { View, Text, StyleSheet, TouchableOpacity, ActivityIndicator } from 'react-native';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { SymbolView } from 'expo-symbols';
import { useRouter } from 'expo-router';

import { useAuth } from '@/context/AuthContext';
import { getNotifications, markNotificationRead } from '../../services/api';
import { useTheme, type Theme } from '../../context/ThemeContext';

/**
 * What is waiting for you, under the wheel.
 *
 * <p>The wheel answers "where do I go"; this answers "what happened while I was
 * away". Tapping one marks it read and opens the screen it is about, so the
 * notification is a shortcut rather than a second inbox to work through.
 *
 * <p>Only unread rows appear, and the strip renders nothing at all when there
 * are none -- an empty "no notifications" card under the wheel would be a
 * permanent piece of furniture for the common case.
 */

type Notification = {
  id: string;
  title: string;
  body: string;
  type: string;
  read: boolean;
  createdAt: string;
};

/**
 * Where a tap goes, per type and role. A type with no entry for the role
 * stays tappable but only marks itself read -- better than routing a parent
 * into a screen that will refuse them.
 */
const DESTINATIONS: Record<string, Record<string, string>> = {
  // A pupil hands homework in; a teacher marks the submission of it.
  TASK:         { STUDENT: '/challenges', TEACHER: '/verification' },
  FEE:          { PARENT: '/fees' },
  ANNOUNCEMENT: { STUDENT: '/student-news', PARENT: '/announcements' },
  // A teacher's attendance notification is "you still have a register open",
  // which is answered from their class list.
  ATTENDANCE:   { STUDENT: '/student-attendance', PARENT: '/attendance', TEACHER: '/teacher' },
  MESSAGE:      { PARENT: '/messages', TEACHER: '/messages' },
};

const ICONS: Record<string, any> = {
  TASK:         { ios: 'checklist', android: 'task_alt', web: 'task_alt' },
  FEE:          { ios: 'creditcard', android: 'payments', web: 'payments' },
  ANNOUNCEMENT: { ios: 'megaphone', android: 'campaign', web: 'campaign' },
  ATTENDANCE:   { ios: 'calendar', android: 'event', web: 'event' },
  MESSAGE:      { ios: 'message', android: 'chat', web: 'chat' },
};

const FALLBACK_ICON = { ios: 'bell', android: 'notifications', web: 'notifications' };

export default function UnreadStrip() {
  const T = useTheme();
  const styles = useMemo(() => makeStyles(T), [T]);
  const router = useRouter();
  const { userRole: role } = useAuth();

  const [items, setItems] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const all = await getNotifications();
      setItems((Array.isArray(all) ? all : []).filter((n: Notification) => !n.read));
    } catch {
      // Nothing waiting is the same as not knowing, for this strip's purposes.
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, []);

  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { void load(); }, [load]);

  const open = async (n: Notification) => {
    // Clear it locally first: the tap navigates away, and waiting on the
    // server would leave the row looking unread until the screen is returned to.
    setItems((prev) => prev.filter((x) => x.id !== n.id));
    void markNotificationRead(n.id).catch(() => {});
    const route = role ? DESTINATIONS[n.type]?.[role] : undefined;
    if (route) router.push(route as never);
  };

  if (loading) {
    return <ActivityIndicator color={T.brand} style={{ marginTop: 20 }} />;
  }
  if (items.length === 0) return null;

  return (
    <View style={styles.wrap}>
      <Text style={styles.heading}>WAITING FOR YOU</Text>
      {items.slice(0, 5).map((n) => (
        <TouchableOpacity
          key={n.id}
          style={styles.row}
          onPress={() => open(n)}
          activeOpacity={0.85}
          accessibilityRole="button"
          accessibilityLabel={`${n.title}. ${n.body ?? ''}`}
          accessibilityHint="Opens the screen this is about"
        >
          <View style={styles.iconTile}>
            <SymbolView name={ICONS[n.type] ?? FALLBACK_ICON} tintColor={T.brand} size={17} />
          </View>
          <View style={{ flex: 1, minWidth: 0 }}>
            <Text style={styles.title} numberOfLines={1}>{n.title}</Text>
            {!!n.body && <Text style={styles.body} numberOfLines={1}>{n.body}</Text>}
          </View>
          <View style={styles.dot} />
        </TouchableOpacity>
      ))}
    </View>
  );
}

const makeStyles = (T: Theme) => StyleSheet.create({
  wrap: { gap: 10, marginTop: 4 },
  heading: {
    fontSize: 11, fontWeight: '700', color: T.text3,
    letterSpacing: 0.9, marginBottom: 2,
  },
  row: {
    flexDirection: 'row', alignItems: 'center', gap: 12,
    padding: 14, borderRadius: 16,
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.line,
  },
  iconTile: {
    width: 34, height: 34, borderRadius: 11,
    alignItems: 'center', justifyContent: 'center', backgroundColor: T.brand50,
  },
  title: { fontSize: 14, fontWeight: '700', color: T.text },
  body: { fontSize: 12.5, color: T.text3, marginTop: 2 },
  // The unread marker. quest, not danger: something waiting is not something wrong.
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: T.quest },
});
