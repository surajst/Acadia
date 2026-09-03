import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import T from '../../constants/theme';

/**
 * A birthday greeting, shown on the child's own dashboard on the day.
 *
 * Deliberately narrow: it appears only on the date itself and only when the
 * school actually recorded a date of birth, and it says who it is from. A
 * greeting that shows up on the wrong day, or that a child cannot tell came
 * from their school, is worse than none.
 */

export function isBirthday(dateOfBirth?: string | null, now: Date = new Date()): boolean {
  if (!dateOfBirth) return false;
  // The API sends a plain yyyy-mm-dd; parsing that as a Date would shift it
  // across a timezone, so compare the calendar fields as written.
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(dateOfBirth));
  if (!m) return false;
  return Number(m[2]) === now.getMonth() + 1 && Number(m[3]) === now.getDate();
}

export function turningAge(dateOfBirth?: string | null, now: Date = new Date()): number | null {
  if (!dateOfBirth) return null;
  const m = /^(\d{4})/.exec(String(dateOfBirth));
  if (!m) return null;
  const age = now.getFullYear() - Number(m[1]);
  return age > 0 && age < 120 ? age : null;
}

export default function BirthdayCard({ firstName, schoolName, age }: {
  firstName?: string;
  schoolName?: string;
  age?: number | null;
}) {
  return (
    <View style={s.card} accessibilityRole="summary">
      <Text style={s.emoji}>🎂</Text>
      <Text style={s.title}>
        Happy birthday{firstName ? `, ${firstName}` : ''}!
      </Text>
      <Text style={s.body}>
        {age ? `Turning ${age} today. ` : ''}
        {schoolName ? `Everyone at ${schoolName} hopes you have a brilliant day.` : 'Hope you have a brilliant day.'}
      </Text>
    </View>
  );
}

const s = StyleSheet.create({
  card: {
    ...T.card,
    backgroundColor: T.brand,
    borderColor: T.brand,
    padding: T.space.xl,
    alignItems: 'center',
    marginBottom: T.space.lg,
  },
  emoji: { fontSize: 40, marginBottom: T.space.sm },
  title: { ...T.type.title, color: T.onBrand, textAlign: 'center' },
  body: {
    ...T.type.body, color: T.onBrandMuted, textAlign: 'center',
    marginTop: T.space.sm, maxWidth: 280,
  },
});
