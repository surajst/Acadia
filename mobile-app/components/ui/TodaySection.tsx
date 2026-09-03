import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { SymbolView, SymbolViewProps } from 'expo-symbols';
import T from '../../constants/theme';

/**
 * The "Today" block: the one thing a student can claim right now, and the
 * lesson they are heading to. Both cards hide entirely when there is nothing
 * to show -- an empty "Today" heading reads as a school that has nothing going
 * on, which is worse than the space it would have filled.
 */

export function SectionLabel({ label, action, onAction }: {
  label: string;
  action?: string;
  onAction?: () => void;
}) {
  return (
    <View style={s.labelRow}>
      <Text style={s.label}>{label}</Text>
      {action && (
        <TouchableOpacity onPress={onAction} disabled={!onAction} accessibilityRole="button">
          <Text style={s.action}>{action}</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

export function QuestCard({ title, subtitle, cta, onPress, busy }: {
  title: string;
  subtitle: string;
  cta: string;
  onPress?: () => void;
  busy?: boolean;
}) {
  const done = !onPress;
  return (
    <TouchableOpacity
      style={s.quest}
      onPress={onPress}
      disabled={done || busy}
      activeOpacity={0.85}
      accessibilityRole="button"
      accessibilityLabel={`${title}. ${subtitle}`}
    >
      <View style={s.questIcon}>
        <SymbolView name={{ ios: 'star.fill', android: 'star', web: 'star' }} tintColor="#FFFFFF" size={20} />
      </View>
      <View style={{ flex: 1, minWidth: 0 }}>
        <Text style={s.questTitle}>{title}</Text>
        <Text style={s.questSub}>{subtitle}</Text>
      </View>
      <View style={[s.questCta, done && s.questCtaDone]}>
        <Text style={s.questCtaText}>{busy ? '…' : cta}</Text>
      </View>
    </TouchableOpacity>
  );
}

/**
 * Same row as the next-class card, but led by an icon instead of a period
 * badge -- for facts that have no period attached, like whether a child is in
 * school today.
 */
export function InfoCard({ icon, tint, title, subtitle, onPress }: {
  icon: SymbolViewProps['name'];
  tint?: string;
  title: string;
  subtitle?: string;
  onPress?: () => void;
}) {
  return (
    <TouchableOpacity
      style={s.next}
      onPress={onPress}
      disabled={!onPress}
      activeOpacity={0.85}
      accessibilityRole={onPress ? 'button' : undefined}
    >
      <View style={[s.nextIcon, tint ? { backgroundColor: tint } : null]}>
        <SymbolView name={icon} tintColor={T.brand} size={18} />
      </View>
      <View style={{ flex: 1, minWidth: 0 }}>
        <Text style={s.nextTitle}>{title}</Text>
        {!!subtitle && <Text style={s.nextSub}>{subtitle}</Text>}
      </View>
    </TouchableOpacity>
  );
}

export function NextClassCard({ period, time, subject, room, note, onPress }: {
  period?: number | string;
  time?: string;
  subject: string;
  room?: string;
  note?: string;
  onPress?: () => void;
}) {
  return (
    <TouchableOpacity
      style={s.next}
      onPress={onPress}
      disabled={!onPress}
      activeOpacity={0.85}
      accessibilityRole={onPress ? 'button' : undefined}
    >
      <View style={s.nextIcon}>
        <Text style={s.periodLabel}>P{period ?? '–'}</Text>
        <Text style={s.periodTime}>{time ?? ''}</Text>
      </View>
      <View style={{ flex: 1, minWidth: 0 }}>
        <Text style={s.nextTitle}>{subject}</Text>
        <Text style={s.nextSub}>{[room, note].filter(Boolean).join(' · ') || 'Up next'}</Text>
      </View>
    </TouchableOpacity>
  );
}

const s = StyleSheet.create({
  labelRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  label: { fontSize: 12, fontWeight: '800', letterSpacing: 1.1, color: T.text3 },
  action: { fontSize: 12.5, fontWeight: '700', color: T.brand },

  quest: {
    flexDirection: 'row', alignItems: 'center', gap: 14,
    padding: 16, borderRadius: 22,
    backgroundColor: T.quest50, borderWidth: 1, borderColor: T.quest200,
  },
  questIcon: {
    width: 42, height: 42, borderRadius: 14, backgroundColor: T.quest,
    alignItems: 'center', justifyContent: 'center',
  },
  questTitle: { fontSize: 15, fontWeight: '700', color: T.questInk },
  questSub: { fontSize: 12.5, fontWeight: '500', color: T.questInkMuted, marginTop: 1 },
  questCta: {
    paddingHorizontal: 14, paddingVertical: 9, borderRadius: T.pill,
    backgroundColor: T.questCta,
  },
  questCtaDone: { opacity: 0.85 },
  questCtaText: { fontSize: 12.5, fontWeight: '800', color: '#FFFFFF' },

  next: {
    flexDirection: 'row', alignItems: 'center', gap: 14,
    padding: 16, borderRadius: 22,
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.line,
  },
  nextIcon: {
    width: 42, height: 42, borderRadius: 14, backgroundColor: T.brand50,
    alignItems: 'center', justifyContent: 'center',
  },
  periodLabel: { fontSize: 9, fontWeight: '800', color: T.brand },
  periodTime: { fontSize: 12, fontWeight: '800', color: T.brandInk, fontVariant: ['tabular-nums'] },
  nextTitle: { fontSize: 15, fontWeight: '700', color: T.text },
  nextSub: { fontSize: 12.5, fontWeight: '500', color: T.text3, marginTop: 1 },
});
