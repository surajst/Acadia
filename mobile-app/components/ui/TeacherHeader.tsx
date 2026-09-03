import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView } from 'expo-symbols';
import ProgressRing from './ProgressRing';
import T from '../../constants/theme';

/**
 * The teacher home header, built on the same chrome as the student's.
 *
 * Where a student's ring asks "how far through my level am I", a teacher's
 * asks the only question that matters before the first bell: how much of
 * today's register is still open. Same shape, different subject -- so a school
 * that has seen one surface has seen both.
 */

type Props = {
  firstName?: string;
  schoolName?: string | null;
  marked: number;
  total: number;
  unread?: number;
  onBellPress?: () => void;
};

const dateLine = () =>
  new Date()
    .toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'short' })
    .toUpperCase();

const greetingFor = (name?: string) => {
  const h = new Date().getHours();
  const part = h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
  return `${part}, ${name || 'Educator'}`;
};

export default function TeacherHeader({
  firstName, schoolName, marked, total, unread = 0, onBellPress,
}: Props) {
  const pct = total > 0 ? Math.round((marked / total) * 100) : 0;
  const left = Math.max(0, total - marked);
  const allDone = total > 0 && left === 0;

  const subtitle = total === 0
    ? 'No classes scheduled today'
    : allDone
      ? 'Every class marked — nothing left today'
      : `${left} class${left > 1 ? 'es' : ''} still to mark`;

  return (
    <LinearGradient
      colors={[T.brand, T.brand700]}
      start={{ x: 0.1, y: 0 }}
      end={{ x: 0.9, y: 1 }}
      style={s.header}
    >
      <View style={s.greetRow}>
        <View style={s.avatar}>
          <Text style={s.avatarText}>{(firstName ?? '?').charAt(0).toUpperCase()}</Text>
        </View>

        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={s.date}>{dateLine()}</Text>
          <Text style={s.greeting} numberOfLines={1}>{greetingFor(firstName)}</Text>
        </View>

        <TouchableOpacity
          style={s.bell}
          onPress={onBellPress}
          disabled={!onBellPress}
          accessibilityRole="button"
          accessibilityLabel={unread > 0 ? `${unread} unread notifications` : 'Notifications'}
        >
          <SymbolView
            name={{ ios: 'bell', android: 'notifications', web: 'notifications' }}
            tintColor="#FFFFFF"
            size={18}
          />
          {unread > 0 && (
            <View style={s.badge}>
              <Text style={s.badgeText}>{unread > 9 ? '9+' : unread}</Text>
            </View>
          )}
        </TouchableOpacity>
      </View>

      <View
        style={s.card}
        accessible
        accessibilityLabel={`Attendance: ${marked} of ${total} classes marked. ${subtitle}.`}
      >
        <ProgressRing
          value={marked}
          label={total > 0 ? `OF ${total}` : '—'}
          pct={pct}
          color={allDone ? T.xpParent : T.xpSchool}
        />

        <View style={{ flex: 1, minWidth: 0, gap: 9 }}>
          <View>
            <Text style={s.title}>Today’s register</Text>
            <Text style={s.sub}>{subtitle}</Text>
          </View>

          <View style={s.bar}>
            <View style={[s.barFill, { width: `${pct}%`, backgroundColor: allDone ? T.xpParent : T.xpSchool }]} />
          </View>

          <Text style={s.school} numberOfLines={1}>{schoolName || 'Your school'}</Text>
        </View>
      </View>
    </LinearGradient>
  );
}

const s = StyleSheet.create({
  header: {
    paddingTop: 14, paddingHorizontal: 20, paddingBottom: 22,
    borderBottomLeftRadius: 30, borderBottomRightRadius: 30,
  },

  greetRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  avatar: {
    width: 44, height: 44, borderRadius: 15,
    backgroundColor: 'rgba(255,255,255,0.16)',
    borderWidth: 1, borderColor: 'rgba(255,255,255,0.22)',
    alignItems: 'center', justifyContent: 'center',
  },
  avatarText: { fontSize: 17, fontWeight: '800', color: T.onBrand },
  date: { fontSize: 12, fontWeight: '600', letterSpacing: 0.5, color: 'rgba(255,255,255,0.62)' },
  greeting: { fontSize: 21, fontWeight: '800', letterSpacing: -0.4, color: T.onBrand, marginTop: 2 },

  bell: {
    width: 38, height: 38, borderRadius: T.pill,
    alignItems: 'center', justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.15)',
    borderWidth: 1, borderColor: 'rgba(255,255,255,0.2)',
  },
  badge: {
    position: 'absolute', top: -3, right: -3,
    minWidth: 18, height: 18, borderRadius: 9, paddingHorizontal: 4,
    backgroundColor: T.danger, alignItems: 'center', justifyContent: 'center',
  },
  badgeText: { fontSize: 10.5, fontWeight: '800', color: '#FFFFFF' },

  card: {
    flexDirection: 'row', alignItems: 'center', gap: 16,
    padding: 16, borderRadius: 22, marginTop: 16,
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderWidth: 1, borderColor: 'rgba(255,255,255,0.18)',
  },
  title: { fontSize: 15, fontWeight: '700', color: T.onBrand },
  sub: { fontSize: 12.5, fontWeight: '500', color: 'rgba(255,255,255,0.66)', marginTop: 1 },

  bar: {
    height: 7, borderRadius: T.pill,
    backgroundColor: 'rgba(255,255,255,0.18)', overflow: 'hidden',
  },
  barFill: { height: 7, borderRadius: T.pill },

  school: { fontSize: 11.5, fontWeight: '600', color: 'rgba(255,255,255,0.8)' },
});
