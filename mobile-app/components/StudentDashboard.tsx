import React, { useState } from 'react';
import { View, Text, StyleSheet, ScrollView, RefreshControl, Alert, TouchableOpacity } from 'react-native';
import { SymbolView, SymbolViewProps } from 'expo-symbols';
import { useRouter } from 'expo-router';
import StudentHeader from './ui/StudentHeader';
import { SectionLabel, QuestCard, NextClassCard } from './ui/TodaySection';
import BirthdayCard, { isBirthday, turningAge } from './ui/BirthdayCard';
import { claimQuest } from '../services/api';
import T from '../constants/theme';

/**
 * The student home screen.
 *
 * Lifted out of the shared dashboard because it no longer shares that screen's
 * shape: the header is full-bleed and gradient, the body is a "Today" block
 * over a quick-actions grid, and none of it uses the greeting card or stat
 * tiles the other roles still render.
 */

type Props = {
  data: any;
  schoolName?: string | null;
  refreshing: boolean;
  onRefresh: () => void;
  refreshData: () => Promise<void>;
};

export default function StudentDashboard({ data, schoolName, refreshing, onRefresh, refreshData }: Props) {
  const router = useRouter();
  const [claiming, setClaiming] = useState<string | null>(null);

  const m = data.metrics ?? {};
  const schoolXp = m.schoolXp ?? 0;
  const parentXp = m.parentXp ?? 0;
  const stats = data.quickStats ?? {};
  const today = data.today ?? {};
  const next = today.nextClass;

  const quests: any[] = Array.isArray(data.parentQuests) ? data.parentQuests : [];
  const claimable = quests.find((q) => (q.status ?? '').toUpperCase() === 'PENDING');
  const spendable = schoolXp + parentXp;

  const onClaim = async (questId: string) => {
    setClaiming(questId);
    try {
      await claimQuest(questId);
      await refreshData();
    } catch (e: any) {
      Alert.alert('Could not claim', e?.response?.data?.error ?? 'Please try again.');
    } finally {
      setClaiming(null);
    }
  };

  return (
    <ScrollView
      style={s.page}
      contentContainerStyle={s.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={T.brand} />}
    >
      <StudentHeader
        firstName={data.student?.firstName}
        streak={m.activeStreak ?? 0}
        level={m.scholarLevel ?? 1}
        schoolXp={schoolXp}
        parentXp={parentXp}
        levelProgress={m.levelProgress ?? 0}
        xpToNextLevel={m.xpToNextLevel ?? 500}
      />

      <View style={s.body}>
        {isBirthday(data.student?.dateOfBirth) && (
          <BirthdayCard
            firstName={data.student?.firstName}
            schoolName={schoolName ?? undefined}
            age={turningAge(data.student?.dateOfBirth)}
          />
        )}

        {/* Today only appears when there is something in it. */}
        {(claimable || next) && (
          <View style={s.section}>
            <SectionLabel
              label="TODAY"
              action={today.periodsToday ? `${today.periodsToday} periods` : undefined}
              onAction={today.periodsToday ? () => router.push('/student-timetable') : undefined}
            />
            {claimable && (
              <QuestCard
                title={claimable.taskDescription || 'Quest ready'}
                subtitle={`Set by your family · earns ${claimable.xpBounty ?? 0} XP`}
                cta="Claim"
                busy={claiming === claimable.id}
                onPress={() => onClaim(claimable.id)}
              />
            )}
            {/* roomNumber already reads "Room 204"; prefixing gave "Room Room 204". */}
            {next && (
              <NextClassCard
                period={next.periodNumber}
                time={next.startTime}
                subject={next.subjectName || 'Class'}
                room={next.roomNumber}
                onPress={() => router.push('/student-timetable')}
              />
            )}
          </View>
        )}

        <View style={s.section}>
          <SectionLabel label="QUICK ACTIONS" />
          <View style={s.grid}>
            <Action
              to="/student-attendance"
              icon={{ ios: 'calendar', android: 'event', web: 'event' }}
              title="Attendance"
              metric={stats.attendancePct != null ? `${stats.attendancePct}% this term` : 'No record yet'}
              tone={stats.attendancePct != null && stats.attendancePct >= 85 ? T.success : undefined}
            />
            <Action
              to="/student-results"
              icon={{ ios: 'chart.bar', android: 'bar_chart', web: 'bar_chart' }}
              title="My results"
              metric={stats.averageMark != null
                ? `Avg ${stats.averageMark} · ${stats.subjectCount} subjects`
                : 'No marks yet'}
            />
            <Action
              to="/student-timetable"
              icon={{ ios: 'clock', android: 'schedule', web: 'schedule' }}
              title="Timetable"
              metric={today.periodsToday ? `${today.periodsToday} periods today` : 'Your class week'}
            />
            <Action
              to="/marketplace"
              icon={{ ios: 'gift', android: 'redeem', web: 'redeem' }}
              title="Rewards"
              metric={`${spendable} XP to spend`}
              tone={T.brand}
            />
          </View>

          <TouchableOpacity
            style={s.wide}
            onPress={() => router.push('/student-news')}
            activeOpacity={0.85}
            accessibilityRole="button"
          >
            <View style={s.iconTile}>
              <SymbolView name={{ ios: 'megaphone', android: 'campaign', web: 'campaign' }} tintColor={T.brand} size={17} />
            </View>
            <View style={{ flex: 1, minWidth: 0 }}>
              <Text style={s.wideTitle}>School news</Text>
              <Text style={s.wideSub}>Notices for your class</Text>
            </View>
          </TouchableOpacity>
        </View>
      </View>
    </ScrollView>
  );
}

function Action({ to, icon, title, metric, tone }: {
  to: string;
  icon: SymbolViewProps['name'];
  title: string;
  metric: string;
  tone?: string;
}) {
  const router = useRouter();
  return (
    <TouchableOpacity
      style={s.card}
      onPress={() => router.push(to as never)}
      activeOpacity={0.85}
      accessibilityRole="button"
      accessibilityLabel={`${title}, ${metric}`}
    >
      <View style={s.iconTile}>
        <SymbolView name={icon} tintColor={T.brand} size={17} />
      </View>
      <Text style={s.cardTitle}>{title}</Text>
      <Text style={[s.cardMetric, tone ? { color: tone } : null]}>{metric}</Text>
    </TouchableOpacity>
  );
}

const s = StyleSheet.create({
  page: { flex: 1, backgroundColor: T.bg },
  content: { paddingBottom: 40 },
  body: { paddingHorizontal: 20, paddingTop: 20, gap: 22 },
  section: { gap: 12 },

  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  card: {
    width: '47.5%',
    padding: 15, borderRadius: 20,
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.line,
  },
  iconTile: {
    width: 34, height: 34, borderRadius: 11, backgroundColor: T.brand50,
    alignItems: 'center', justifyContent: 'center',
  },
  cardTitle: { fontSize: 14.5, fontWeight: '700', color: T.text, marginTop: 13 },
  cardMetric: { fontSize: 12, fontWeight: '600', color: T.text3, marginTop: 3 },

  wide: {
    flexDirection: 'row', alignItems: 'center', gap: 14,
    padding: 15, borderRadius: 20,
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.line,
  },
  wideTitle: { fontSize: 14.5, fontWeight: '700', color: T.text },
  wideSub: { fontSize: 12, fontWeight: '500', color: T.text3, marginTop: 2 },
});
