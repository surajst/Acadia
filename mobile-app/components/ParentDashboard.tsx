import React, { useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, RefreshControl, TouchableOpacity } from 'react-native';
import { useRouter } from 'expo-router';
import ParentHeader from './ui/ParentHeader';
import HomeWheel from './HomeWheel';
import { SectionLabel, QuestCard, InfoCard } from './ui/TodaySection';
import { useTheme, type Theme } from '../context/ThemeContext';

/**
 * The parent home screen, on the same shape as the other two.
 *
 * Today carries the two things a parent acts on: money the school is waiting
 * for, and whether their child is in school. Recognition keeps its place
 * underneath, because "shared the blue crayons with Zoya" is the part that
 * gets talked about at home -- the XP number above it means nothing on its own.
 */

type Props = {
  data: any;
  parentFirstName?: string | null;
  refreshing: boolean;
  onRefresh: () => void;
  selectedChildId: string | null;
  selectChild: (id: string) => void;
};

const money = (n: any) =>
  n == null ? '' : `₹ ${Number(n).toLocaleString('en-IN', { maximumFractionDigits: 0 })}`;

export default function ParentDashboard({
  data, parentFirstName, refreshing, onRefresh, selectedChildId, selectChild,
}: Props) {
  const T = useTheme();
  const s = useMemo(() => makeStyles(T), [T]);
  const router = useRouter();

  const m = data.metrics ?? {};
  const child = data.student ?? {};
  const children: any[] = Array.isArray(data.children) ? data.children : [];
  const awards: any[] = Array.isArray(data.awards) ? data.awards : [];
  const fees = data.fees;
  const attendance = data.attendanceStatus;

  const overdue = (fees?.overdueCount ?? 0) > 0;
  const owes = fees && !fees.settled && (fees.totalDue ?? 0) > 0;

  return (
    <ScrollView
      style={s.page}
      contentContainerStyle={s.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={T.brand} />}
    >
      <ParentHeader
        onProfilePress={() => router.push('/profile' as never)}
        onSettingsPress={() => router.push('/settings' as never)}
        parentName={parentFirstName ?? undefined}
        childName={child.firstName}
        level={m.scholarLevel ?? 1}
        schoolXp={m.schoolXp ?? 0}
        parentXp={m.parentXp ?? 0}
        levelProgress={m.levelProgress ?? 0}
        xpToNextLevel={m.xpToNextLevel ?? 500}
      />

      <View style={s.body}>
        {children.length > 1 && (
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={s.childRow}>
            {children.map((c) => {
              const on = c.id === selectedChildId;
              return (
                <TouchableOpacity
                  key={c.id}
                  style={[s.childChip, on && s.childChipOn]}
                  onPress={() => selectChild(c.id)}
                  accessibilityRole="button"
                  accessibilityState={{ selected: on }}
                >
                  <Text style={[s.childChipText, on && s.childChipTextOn]}>{c.firstName}</Text>
                </TouchableOpacity>
              );
            })}
          </ScrollView>
        )}

        {(owes || attendance) && (
          <View style={s.section}>
            <SectionLabel label="TODAY" />

            {owes && (
              <QuestCard
                title={overdue
                  ? `${fees.overdueCount} instalment${fees.overdueCount > 1 ? 's' : ''} overdue`
                  : fees.nextDueAmount != null
                    ? `${money(fees.nextDueAmount)} due next`
                    : `${money(fees.totalDue)} outstanding`}
                subtitle={[fees.nextDueLabel, money(fees.totalDue) && `${money(fees.totalDue)} outstanding`]
                  .filter(Boolean).join(' · ')}
                cta="View"
                onPress={() => router.push('/fees')}
              />
            )}

            {attendance && (
              <InfoCard
                icon={{ ios: 'calendar', android: 'event', web: 'event' }}
                title={`${child.firstName || 'Your child'} is ${String(attendance).toLowerCase()} today`}
                subtitle={child.gradeName ? `${child.gradeName} ${child.sectionName ?? ''}`.trim() : undefined}
                onPress={() => router.push('/attendance')}
              />
            )}
          </View>
        )}

        {/* The wheel replaced the Quick Actions grid and the bottom tab
            bar both -- one hub, and one place a destination lives. */}
        <HomeWheel />

        {/* Recognition keeps its place below the wheel: it is something to
            read, not somewhere to go, and "See all" still reaches the full
            list -- which is also a spoke. */}
        {awards.length > 0 && (
          <View style={s.section}>
            <SectionLabel label="RECOGNISED BY THEIR TEACHER" action="See all" onAction={() => router.push('/recognition')} />
            {awards.slice(0, 3).map((a: any, i: number) => (
              <View key={a.id ?? `award-${i}`} style={s.award}>
                <Text style={s.awardEmoji}>{a.emoji || '🏅'}</Text>
                <View style={{ flex: 1, minWidth: 0 }}>
                  <Text style={s.awardLabel}>
                    {a.label} <Text style={s.awardPoints}>+{a.points}</Text>
                  </Text>
                  {!!a.reason && <Text style={s.awardReason}>{a.reason}</Text>}
                </View>
              </View>
            ))}
          </View>
        )}
      </View>
    </ScrollView>
  );
}


const makeStyles = (T: Theme) => StyleSheet.create({
  page: { flex: 1, backgroundColor: T.bg },
  content: { paddingBottom: 40 },
  body: { paddingHorizontal: 20, paddingTop: 20, gap: 22 },
  section: { gap: 12 },

  childRow: { gap: 8, paddingRight: 4 },
  childChip: {
    minHeight: 38, justifyContent: 'center', paddingHorizontal: 14,
    borderRadius: T.pill, backgroundColor: T.surface,
    borderWidth: 1, borderColor: T.line,
  },
  childChipOn: { backgroundColor: T.brand50, borderColor: T.brand },
  childChipText: { fontSize: 13, fontWeight: '600', color: T.text2 },
  childChipTextOn: { color: T.brand },

  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  card: {
    width: '47.5%', padding: 15, borderRadius: 20,
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.line,
  },
  iconTile: {
    width: 34, height: 34, borderRadius: 11, backgroundColor: T.brand50,
    alignItems: 'center', justifyContent: 'center',
  },
  cardTitle: { fontSize: 14.5, fontWeight: '700', color: T.text, marginTop: 13 },
  cardMetric: { fontSize: 12, fontWeight: '600', color: T.text3, marginTop: 3 },

  award: {
    flexDirection: 'row', alignItems: 'center', gap: 12,
    padding: 14, borderRadius: 16,
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.line,
  },
  awardEmoji: { fontSize: 22 },
  awardLabel: { fontSize: 14, fontWeight: '600', color: T.text },
  awardPoints: { color: T.successInk, fontWeight: '700' },
  awardReason: { fontSize: 12.5, color: T.text3, marginTop: 2 },
});
