import React from 'react';
import { Text, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SymbolView, SymbolViewProps } from 'expo-symbols';
import { useRouter } from 'expo-router';
import T from '../../constants/theme';

/**
 * One tile in the Quick Actions grid.
 *
 * The icon is monochrome by default and the tile only takes colour when it has
 * something waiting -- so a teacher opening the app sees which square wants
 * them, instead of five equally loud squares. `urgent` always changes the
 * subtitle text too, never the colour alone.
 */

export default function NavCard({ to, icon, title, subtitle, urgent = false }: {
  to: string;
  icon: SymbolViewProps['name'];
  title: string;
  subtitle: string;
  urgent?: boolean;
}) {
  const router = useRouter();

  return (
    <TouchableOpacity
      style={[s.card, urgent && s.cardUrgent]}
      onPress={() => router.push(to as never)}
      accessibilityRole="button"
      accessibilityLabel={`${title}, ${subtitle}`}
      activeOpacity={0.75}
    >
      <View style={s.iconRow}>
        <SymbolView name={icon} tintColor={urgent ? T.warn : T.text3} size={20} />
      </View>
      <Text style={s.title}>{title}</Text>
      <Text style={[s.subtitle, urgent && s.subtitleUrgent]}>{subtitle}</Text>
    </TouchableOpacity>
  );
}

const s = StyleSheet.create({
  card: {
    ...T.card,
    width: '47%',
    minHeight: 96,
    padding: T.space.lg,
    justifyContent: 'space-between',
  },
  cardUrgent: { borderColor: T.warn200, backgroundColor: T.warn50 },
  iconRow: { marginBottom: T.space.md },
  title: { ...T.type.label, color: T.text },
  subtitle: { ...T.type.caption, color: T.text3, marginTop: 2 },
  subtitleUrgent: { color: T.warn, fontWeight: '600' },
});
