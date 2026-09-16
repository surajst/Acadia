import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
// T here is only for the frozen module-scope styles below, which use
// non-brand constants (T.pill) that never change with the chosen theme.
// Inside each component, a locally-shadowing `const T = useTheme()` is used
// instead for anything that does -- see the note on Theme in ThemeContext.tsx
// for why the identifier name has to stay `T` either way.
import T from '../../constants/theme';
import { useTheme } from '../../context/ThemeContext';

/**
 * The chrome every role's home screen shares: the gradient block, the avatar,
 * the date line, the greeting, and a slot on the right for whatever that role
 * needs there -- a streak, a bell, nothing.
 *
 * Extracted once the student and teacher headers turned out to differ only in
 * that slot and the card underneath. The card is passed as children, so each
 * role composes its own summary without a third copy of this.
 */

export const headerDateLine = () =>
  new Date()
    .toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'short' })
    .toUpperCase();

export default function GradientHeader({ initial, greeting, trailing, children }: {
  initial?: string;
  greeting: string;
  trailing?: React.ReactNode;
  children?: React.ReactNode;
}) {
  const T = useTheme();
  return (
    <LinearGradient
      colors={[T.brand, T.brand700]}
      start={{ x: 0.1, y: 0 }}
      end={{ x: 0.9, y: 1 }}
      style={s.header}
    >
      <View style={s.greetRow}>
        <View style={s.avatar}>
          <Text style={[s.avatarText, { color: T.onBrand }]}>{(initial ?? '?').charAt(0).toUpperCase()}</Text>
        </View>

        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={s.date}>{headerDateLine()}</Text>
          <Text style={[s.greeting, { color: T.onBrand }]} numberOfLines={1}>{greeting}</Text>
        </View>

        {trailing}
      </View>

      {children}
    </LinearGradient>
  );
}

/** The translucent card that sits inside the gradient. */
export function HeaderCard({ children, label }: { children: React.ReactNode; label?: string }) {
  return (
    <View style={s.card} accessible accessibilityLabel={label}>
      {children}
    </View>
  );
}

export const headerStyles = StyleSheet.create({
  // color is deliberately absent -- onBrand changes with the chosen theme, so
  // every consumer applies it inline: style={[h.title, { color: T.onBrand }]}
  // with a theme-hook-sourced T, not the module-level static one.
  title: { fontSize: 15, fontWeight: '700' },
  sub: { fontSize: 12.5, fontWeight: '500', color: 'rgba(255,255,255,0.66)', marginTop: 1 },
  bar: {
    height: 7, borderRadius: T.pill,
    backgroundColor: 'rgba(255,255,255,0.18)', overflow: 'hidden',
  },
  barFill: { height: 7, borderRadius: T.pill },
  legend: { flexDirection: 'row', gap: 14 },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  swatch: { width: 7, height: 7, borderRadius: 2 },
  legendText: { fontSize: 11.5, fontWeight: '600', color: 'rgba(255,255,255,0.8)' },
  pill: {
    flexDirection: 'row', alignItems: 'center', gap: 6,
    paddingVertical: 7, paddingLeft: 9, paddingRight: 11,
    borderRadius: T.pill,
    backgroundColor: 'rgba(255,255,255,0.15)',
    borderWidth: 1, borderColor: 'rgba(255,255,255,0.2)',
  },
});

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
  // color absent on both -- same reason as headerStyles.title above.
  avatarText: { fontSize: 17, fontWeight: '800' },
  date: { fontSize: 12, fontWeight: '600', letterSpacing: 0.5, color: 'rgba(255,255,255,0.62)' },
  greeting: { fontSize: 21, fontWeight: '800', letterSpacing: -0.4, marginTop: 2 },
  card: {
    flexDirection: 'row', alignItems: 'center', gap: 16,
    padding: 16, borderRadius: 22, marginTop: 16,
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderWidth: 1, borderColor: 'rgba(255,255,255,0.18)',
  },
});
