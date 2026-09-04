import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import T from '../../constants/theme';

/**
 * A metric tile is mostly typography: one figure, one label, and colour only
 * where the colour means something. No gradient, no icon in a tinted circle --
 * on eight tiles that is wallpaper, and it makes the numbers harder to compare,
 * not easier.
 *
 * `tone` is semantic and always paired with a written cue elsewhere on the
 * screen, because colour on its own is not a signal every reader receives.
 */

type Tone = 'neutral' | 'attention' | 'good';

const TONE: Record<Tone, string> = {
  neutral: T.text,
  attention: T.warn,
  good: T.success,
};

export function Stat({ label, value, tone = 'neutral' }: {
  label: string;
  value: string | number;
  tone?: Tone;
}) {
  return (
    <View style={s.box}>
      {/* Two lines, not one: three tiles across a phone leaves ~80px of label,
          and a silently truncated "Attenda..." costs more than a wrap. */}
      <Text style={s.label} numberOfLines={2}>{label}</Text>
      <Text style={[s.value, { color: TONE[tone] }]}>{value}</Text>
    </View>
  );
}

/** Tiles in a row share one type scale and one alignment, so the row scans. */
export function StatRow({ children }: { children: React.ReactNode }) {
  return <View style={s.row}>{children}</View>;
}

const s = StyleSheet.create({
  row: { flexDirection: 'row', gap: T.space.md, marginBottom: T.space.lg },
  box: {
    ...T.card,
    flex: 1,
    paddingVertical: T.space.lg,
    paddingHorizontal: T.space.sm + 2,
    justifyContent: 'space-between',
    minHeight: 84,
  },
  // 11px, not 12: three tiles across a 414pt phone leave ~78pt of label, and
  // "Attendance" at 12px breaks mid-word.
  label: { ...T.type.caption, fontSize: 11, lineHeight: 15, color: T.text3 },
  value: { ...T.type.metric, marginTop: T.space.xs },
});
