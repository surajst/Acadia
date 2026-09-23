import React, { useMemo } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl } from 'react-native';
import { useRouter } from 'expo-router';
import TeacherHeader from './ui/TeacherHeader';
import HomeWheel from './HomeWheel';
import UnreadStrip from './ui/UnreadStrip';
import { useProfilePhoto } from '../context/ProfilePhotoContext';
import { SectionLabel, QuestCard, NextClassCard } from './ui/TodaySection';
import { useTheme, type Theme } from '../context/ThemeContext';

/**
 * The teacher home screen, on the same shape as the student's: gradient
 * header, a Today block of the things waiting, then a grid of everywhere else.
 *
 * The verification queue moved out of the grid and into Today, because it is
 * the one item with work sitting in it -- which also leaves the grid a clean
 * four, instead of five with one strandedon its own row.
 */

type Props = {
  data: any;
  firstName?: string | null;
  schoolName?: string | null;
  unreadCount: number;
  refreshing: boolean;
  onRefresh: () => void;
};

export default function TeacherDashboard({
  data, firstName, schoolName, unreadCount, refreshing, onRefresh,
}: Props) {
  const T = useTheme();
  const s = useMemo(() => makeStyles(T), [T]);
  const router = useRouter();
  const { photoUri } = useProfilePhoto();
  const timetable: any[] = Array.isArray(data.timetable) ? data.timetable : [];
  const summary = data.attendanceSummary ?? {};
  const marked = summary.markedToday ?? 0;
  const total = summary.totalClasses ?? 0;

  const pendingReviews =
    (data.queue?.pendingSubmissions?.length ?? 0) + (data.queue?.pendingProgress?.length ?? 0);

  // The period they are heading to: the first today that still needs marking,
  // else simply the first of the day.
  const next = timetable.find((p) => !p.attendanceMarked) ?? timetable[0];

  return (
    <ScrollView
      style={s.page}
      contentContainerStyle={s.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={T.brand} />}
    >
      <TeacherHeader
        photoUri={photoUri}
        onProfilePress={() => router.push('/profile' as never)}
        onSettingsPress={() => router.push('/settings' as never)}
        firstName={firstName ?? undefined}
        schoolName={schoolName}
        marked={marked}
        total={total}
        unread={unreadCount}
        onBellPress={() => router.push('/notifications')}
      />

      <View style={s.body}>
        {(pendingReviews > 0 || next) && (
          <View style={s.section}>
            <SectionLabel
              label="TODAY"
              action={timetable.length ? `${timetable.length} periods` : undefined}
              onAction={timetable.length ? () => router.push('/timetable') : undefined}
            />

            {pendingReviews > 0 && (
              <QuestCard
                title={`${pendingReviews} waiting on you`}
                subtitle="Milestone submissions and syllabus sign-offs"
                cta="Review"
                onPress={() => router.push('/verification')}
              />
            )}

            {next && (
              <NextClassCard
                period={next.periodNumber}
                time={next.startTime}
                subject={next.subjectName || 'Class'}
                room={next.roomNumber}
                note={next.attendanceMarked ? 'Attendance marked' : 'Attendance not marked'}
                onPress={() => router.push('/teacher')}
              />
            )}
          </View>
        )}

        {/* The wheel replaced the Quick Actions grid and the bottom tab
            bar both -- one hub, and one place a destination lives. */}
        <HomeWheel />

        {/* What happened while they were away. Renders nothing when
            there is nothing, so it is not permanent furniture. */}
        <UnreadStrip />

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
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.brand100,
  },
  wideIcon: {
    width: 34, height: 34, borderRadius: 11, backgroundColor: T.brand,
    alignItems: 'center', justifyContent: 'center',
  },
  wideTitle: { fontSize: 14.5, fontWeight: '700', color: T.text },
  wideSub: { fontSize: 12, fontWeight: '500', color: T.text3, marginTop: 2 },
});
