import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { SymbolView } from 'expo-symbols';
import GradientHeader, { HeaderCard, headerStyles as h } from './GradientHeader';
import ProgressRing from './ProgressRing';
import T from '../../constants/theme';

/**
 * The teacher home header, on the same chrome as the student's.
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

const greetingFor = (name?: string) => {
  const hour = new Date().getHours();
  const part = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
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
    <GradientHeader
      initial={firstName}
      greeting={greetingFor(firstName)}
      trailing={
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
      }
    >
      <HeaderCard label={`Attendance: ${marked} of ${total} classes marked. ${subtitle}.`}>
        <ProgressRing
          value={marked}
          label={total > 0 ? `OF ${total}` : '—'}
          pct={pct}
          color={allDone ? T.xpParent : T.xpSchool}
        />

        <View style={{ flex: 1, minWidth: 0, gap: 9 }}>
          <View>
            <Text style={h.title}>Today’s register</Text>
            <Text style={h.sub}>{subtitle}</Text>
          </View>

          <View style={h.bar}>
            <View style={[h.barFill, { width: `${pct}%`, backgroundColor: allDone ? T.xpParent : T.xpSchool }]} />
          </View>

          <Text style={h.legendText} numberOfLines={1}>{schoolName || 'Your school'}</Text>
        </View>
      </HeaderCard>
    </GradientHeader>
  );
}

const s = StyleSheet.create({
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
});
