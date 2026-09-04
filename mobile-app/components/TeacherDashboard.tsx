import React from 'react';
import { View, Text, StyleSheet, ScrollView, RefreshControl, TouchableOpacity } from 'react-native';
import { SymbolView, SymbolViewProps } from 'expo-symbols';
import { useRouter } from 'expo-router';
import TeacherHeader from './ui/TeacherHeader';
import { SectionLabel, QuestCard, NextClassCard } from './ui/TodaySection';
import T from '../constants/theme';

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
  const router = useRouter();

  const classes: any[] = Array.isArray(data.classes) ? data.classes : [];
  const tasks: any[] = Array.isArray(data.tasks) ? data.tasks : [];
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

        <View style={s.section}>
          <SectionLabel label="QUICK ACTIONS" />
          <View style={s.grid}>
            <Action
              to="/teacher"
              icon={{ ios: 'person.badge.clock', android: 'school', web: 'school' }}
              title="My Classes"
              metric={`${classes.length} ${classes.length === 1 ? 'class' : 'classes'}`}
            />
            <Action
              to="/tasks"
              icon={{ ios: 'checklist', android: 'task_alt', web: 'task_alt' }}
              title="Tasks"
              metric={tasks.length > 0 ? `${tasks.length} assigned` : 'None assigned'}
            />
            <Action
              to="/gradebook"
              icon={{ ios: 'chart.bar.doc.horizontal', android: 'grading', web: 'grading' }}
              title="Gradebook"
              metric="Enter scores"
            />
            <Action
              to="/timetable"
              icon={{ ios: 'clock', android: 'schedule', web: 'schedule' }}
              title="Timetable"
              metric={timetable.length ? `${timetable.length} periods today` : 'Your week'}
            />
          </View>

          {/* Assigning work is the thing a teacher came here to do, so it gets
              the full width rather than a quarter of the grid. */}
          <TouchableOpacity
            style={s.wide}
            onPress={() => router.push('/task-new')}
            activeOpacity={0.85}
            accessibilityRole="button"
          >
            <View style={s.wideIcon}>
              <SymbolView name={{ ios: 'plus', android: 'add', web: 'add' }} tintColor={T.onBrand} size={17} />
            </View>
            <View style={{ flex: 1, minWidth: 0 }}>
              <Text style={s.wideTitle}>Assign new task</Text>
              <Text style={s.wideSub}>Homework, practice or a project</Text>
            </View>
          </TouchableOpacity>
        </View>
      </View>
    </ScrollView>
  );
}

function Action({ to, icon, title, metric }: {
  to: string;
  icon: SymbolViewProps['name'];
  title: string;
  metric: string;
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
      <Text style={s.cardMetric}>{metric}</Text>
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
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.brand100,
  },
  wideIcon: {
    width: 34, height: 34, borderRadius: 11, backgroundColor: T.brand,
    alignItems: 'center', justifyContent: 'center',
  },
  wideTitle: { fontSize: 14.5, fontWeight: '700', color: T.text },
  wideSub: { fontSize: 12, fontWeight: '500', color: T.text3, marginTop: 2 },
});
