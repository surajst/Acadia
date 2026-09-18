import React, { useMemo, useState } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl, Alert } from 'react-native';
import { useRouter } from 'expo-router';
import StudentHeader from './ui/StudentHeader';
import HomeWheel from './HomeWheel';
import { useProfilePhoto } from '../context/ProfilePhotoContext';
import { SectionLabel, QuestCard, NextClassCard } from './ui/TodaySection';
import BirthdayCard, { isBirthday, turningAge } from './ui/BirthdayCard';
import { claimQuest } from '../services/api';
import { useTheme, type Theme } from '../context/ThemeContext';

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
  const T = useTheme();
  const s = useMemo(() => makeStyles(T), [T]);
  const router = useRouter();
  const { photoUri } = useProfilePhoto();
  const [claiming, setClaiming] = useState<string | null>(null);

  const m = data.metrics ?? {};
  const schoolXp = m.schoolXp ?? 0;
  const parentXp = m.parentXp ?? 0;
  const today = data.today ?? {};
  const next = today.nextClass;

  const quests: any[] = Array.isArray(data.parentQuests) ? data.parentQuests : [];
  const claimable = quests.find((q) => (q.status ?? '').toUpperCase() === 'PENDING');

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
        photoUri={photoUri}
        onProfilePress={() => router.push('/profile' as never)}
        onSettingsPress={() => router.push('/settings' as never)}
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

        {/* The wheel replaced the Quick Actions grid and the bottom tab
            bar both -- one hub, and one place a destination lives. */}
        <HomeWheel />

      </View>
    </ScrollView>
  );
}


const makeStyles = (T: Theme) => StyleSheet.create({
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
