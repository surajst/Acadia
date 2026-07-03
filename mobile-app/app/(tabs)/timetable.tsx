import React, { useContext, useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, ActivityIndicator } from 'react-native';
import { SymbolView } from 'expo-symbols';
import { DataContext } from './_layout';
import { getTimetableWeek } from '@/services/api';

const DAY_ORDER = ['MON', 'TUE', 'WED', 'THU', 'FRI'];
const DAY_LABELS: Record<string, string> = { MON: 'Monday', TUE: 'Tuesday', WED: 'Wednesday', THU: 'Thursday', FRI: 'Friday' };

export default function TimetableScreen() {
  const { data } = useContext(DataContext);
  const [week, setWeek] = useState<Record<string, any[]> | null>(null);
  const [loading, setLoading] = useState(true);

  const today = (data?.timetable ?? []) as any[];

  useEffect(() => {
    (async () => {
      try {
        const w = await getTimetableWeek();
        setWeek(w);
      } catch (e) {
        console.error('Failed to fetch week timetable:', e);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  return (
    <View style={styles.root}>
      <View style={styles.headerBand}>
        <View style={styles.headerIconWrap}>
          <SymbolView name={{ ios: 'calendar', android: 'event', web: 'event' }} tintColor="#6366f1" size={26} />
        </View>
        <View style={{ flex: 1, marginLeft: 14 }}>
          <Text style={styles.headerTitle}>Timetable</Text>
          <Text style={styles.headerSubtitle}>Your class schedule</Text>
        </View>
      </View>

      <View style={styles.sectionLabelRow}>
        <Text style={styles.sectionLabel}>TODAY</Text>
      </View>

      {today.length === 0 ? (
        <Text style={styles.emptyText}>No periods scheduled for today.</Text>
      ) : (
        today.map((p: any, idx: number) => (
          <View key={idx} style={styles.periodCard}>
            <Text style={styles.periodTime}>{p.startTime}–{p.endTime}</Text>
            <View style={{ flex: 1, marginLeft: 12 }}>
              <Text style={styles.periodSubject}>{p.subjectName}</Text>
              <Text style={styles.periodClass}>{p.className} · {p.roomNumber || ''}</Text>
            </View>
            {p.attendanceMarked ? (
              <View style={[styles.badge, { backgroundColor: '#22c55e22' }]}><Text style={[styles.badgeText, { color: '#22c55e' }]}>Marked</Text></View>
            ) : (
              <View style={[styles.badge, { backgroundColor: '#f59e0b22' }]}><Text style={[styles.badgeText, { color: '#f59e0b' }]}>Pending</Text></View>
            )}
          </View>
        ))
      )}

      <View style={styles.sectionLabelRow}>
        <Text style={styles.sectionLabel}>FULL WEEK</Text>
      </View>

      {loading ? (
        <ActivityIndicator size="large" color="#6366f1" style={{ marginTop: 20 }} />
      ) : (
        <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent}>
          {DAY_ORDER.map((day) => (
            <View key={day} style={styles.dayBlock}>
              <Text style={styles.dayLabel}>{DAY_LABELS[day]}</Text>
              {(week?.[day] ?? []).length === 0 ? (
                <Text style={styles.emptyDayText}>No periods</Text>
              ) : (
                (week?.[day] ?? []).map((p: any, idx: number) => (
                  <View key={idx} style={styles.weekRow}>
                    <Text style={styles.weekTime}>{p.startTime}</Text>
                    <Text style={styles.weekSubject}>{p.subjectName}</Text>
                    <Text style={styles.weekClass}>{p.className}</Text>
                  </View>
                ))
              )}
            </View>
          ))}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0f172a' },
  headerBand: {
    flexDirection: 'row', alignItems: 'center',
    backgroundColor: '#1e293b', paddingHorizontal: 20, paddingVertical: 18,
    borderBottomWidth: 1, borderBottomColor: '#334155',
  },
  headerIconWrap: { width: 48, height: 48, borderRadius: 14, backgroundColor: '#6366f120', justifyContent: 'center', alignItems: 'center' },
  headerTitle: { fontSize: 17, fontWeight: '700', color: '#f1f5f9' },
  headerSubtitle: { fontSize: 12, color: '#64748b', marginTop: 2 },
  sectionLabelRow: { paddingHorizontal: 20, paddingTop: 18, paddingBottom: 8 },
  sectionLabel: { fontSize: 11, fontWeight: '700', color: '#64748b', letterSpacing: 1 },
  emptyText: { fontSize: 13, color: '#64748b', paddingHorizontal: 20 },
  periodCard: {
    flexDirection: 'row', alignItems: 'center', backgroundColor: '#1e293b',
    borderRadius: 12, padding: 12, marginHorizontal: 16, marginBottom: 8,
    borderWidth: 1, borderColor: '#334155',
  },
  periodTime: { fontSize: 12, color: '#94a3b8', fontVariant: ['tabular-nums'] },
  periodSubject: { fontSize: 14, fontWeight: '700', color: '#f1f5f9' },
  periodClass: { fontSize: 11, color: '#64748b', marginTop: 2 },
  badge: { borderRadius: 8, paddingHorizontal: 8, paddingVertical: 4 },
  badgeText: { fontSize: 10, fontWeight: '700' },
  scroll: { flex: 1 },
  scrollContent: { paddingHorizontal: 16, paddingBottom: 32 },
  dayBlock: { marginBottom: 14 },
  dayLabel: { fontSize: 13, fontWeight: '700', color: '#f1f5f9', marginBottom: 6 },
  emptyDayText: { fontSize: 12, color: '#64748b' },
  weekRow: { flexDirection: 'row', gap: 10, paddingVertical: 4 },
  weekTime: { fontSize: 12, color: '#6366f1', width: 50 },
  weekSubject: { fontSize: 12, color: '#f1f5f9', flex: 1 },
  weekClass: { fontSize: 11, color: '#64748b' },
});
