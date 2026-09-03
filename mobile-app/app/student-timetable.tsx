import React, { useCallback, useEffect, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, RefreshControl } from 'react-native';
import { Stack } from 'expo-router';
import { getStudentTimetable } from '@/services/api';
import { ListSkeleton } from '@/components/ui/Skeleton';
import EmptyState from '@/components/ui/EmptyState';
import T from '@/constants/theme';

/**
 * The student's own class timetable. The teacher's timetable screen shows one
 * teacher's periods; a student needs the whole class day, every subject, which
 * is why this reads from the section rather than from subject assignments.
 */

// The backend stores three-letter codes (MON..SAT), not full day names.
const DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
const LABEL: Record<string, string> = {
  MON: 'Monday', TUE: 'Tuesday', WED: 'Wednesday',
  THU: 'Thursday', FRI: 'Friday', SAT: 'Saturday',
};

type Period = {
  id: string;
  dayOfWeek?: string;
  periodNumber?: number;
  startTime?: string;
  endTime?: string;
  subjectName?: string;
  roomNumber?: string;
};

export default function StudentTimetableScreen() {
  const [rows, setRows] = useState<Period[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    setRows((await getStudentTimetable()) as Period[]);
  }, []);

  useEffect(() => {
    (async () => {
      try { await load(); } catch { /* empty state covers it */ }
      finally { setLoading(false); }
    })();
  }, [load]);

  const onRefresh = async () => {
    setRefreshing(true);
    try { await load(); } catch { /* keep what is on screen */ }
    setRefreshing(false);
  };

  const today = DAYS[new Date().getDay() - 1];
  const byDay = DAYS
    .map((day) => ({
      day,
      periods: rows
        .filter((p) => (p.dayOfWeek ?? '').toUpperCase() === day)
        .sort((a, b) => (a.periodNumber ?? 0) - (b.periodNumber ?? 0)),
    }))
    .filter((d) => d.periods.length > 0);

  if (loading) {
    return (
      <View style={[s.page, s.content]}>
        <Stack.Screen options={{ title: 'Timetable' }} />
        <ListSkeleton rows={4} />
      </View>
    );
  }

  return (
    <ScrollView
      style={s.page}
      contentContainerStyle={s.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={T.brand} />}
    >
      <Stack.Screen options={{ title: 'Timetable' }} />

      {byDay.length === 0 ? (
        <EmptyState
          icon={{ ios: 'calendar', android: 'event', web: 'event' }}
          title="No timetable yet"
          body="Once your school sets the class schedule, every period of your week shows up here."
        />
      ) : (
        byDay.map(({ day, periods }) => (
          <View key={day} style={s.dayBlock}>
            <View style={s.dayHeader}>
              <Text style={s.dayName}>{LABEL[day] ?? day}</Text>
              {day === today && <Text style={s.todayChip}>Today</Text>}
            </View>

            {periods.map((p) => (
              <View key={p.id} style={s.row}>
                <View style={s.periodBadge}>
                  <Text style={s.periodNum}>{p.periodNumber ?? '–'}</Text>
                </View>
                <View style={{ flex: 1, minWidth: 0 }}>
                  <Text style={s.subject}>{p.subjectName || 'Subject'}</Text>
                  <Text style={s.meta}>
                    {[p.startTime && p.endTime ? `${p.startTime} – ${p.endTime}` : null, p.roomNumber]
                      .filter(Boolean)
                      .join(' · ')}
                  </Text>
                </View>
              </View>
            ))}
          </View>
        ))
      )}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  page: { flex: 1, backgroundColor: T.bg },
  content: { padding: T.space.lg, paddingBottom: T.space.xxl, gap: T.space.xl },

  dayBlock: { gap: T.space.sm },
  dayHeader: { flexDirection: 'row', alignItems: 'center', gap: T.space.sm, marginBottom: T.space.xs },
  dayName: { ...T.type.overline, color: T.text3, textTransform: 'uppercase' },
  todayChip: {
    ...T.type.caption, fontWeight: '700', color: T.brand,
    backgroundColor: T.brand50, borderRadius: T.pill,
    paddingHorizontal: T.space.sm, paddingVertical: 1,
  },

  row: {
    ...T.card,
    flexDirection: 'row', alignItems: 'center', gap: T.space.md,
    padding: T.space.md,
  },
  periodBadge: {
    width: 34, height: 34, borderRadius: T.rXs,
    backgroundColor: T.track, alignItems: 'center', justifyContent: 'center',
  },
  periodNum: { ...T.type.label, color: T.text2, fontVariant: ['tabular-nums'] },
  subject: { ...T.type.heading, color: T.text },
  meta: { ...T.type.caption, color: T.text3, marginTop: 1 },
});
