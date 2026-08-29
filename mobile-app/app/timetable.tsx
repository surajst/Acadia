import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, ActivityIndicator } from 'react-native';
import { SymbolView } from 'expo-symbols';
import { getTimetableWeek, getTimetableToday } from '@/services/api';

const DAY_ORDER = ['MON', 'TUE', 'WED', 'THU', 'FRI'];
const DAY_LABELS: Record<string, string> = { MON: 'Monday', TUE: 'Tuesday', WED: 'Wednesday', THU: 'Thursday', FRI: 'Friday' };

export default function TimetableScreen() {
  const [today, setToday] = useState<any[]>([]);
  const [week, setWeek] = useState<Record<string, any[]> | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const [t, w] = await Promise.all([getTimetableToday(), getTimetableWeek()]);
        setToday(Array.isArray(t) ? t : []);
        setWeek(w);
      } catch (e) {
        console.error('Failed to fetch timetable:', e);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  return (
    <View style={styles.root}>
      <View style={styles.headerBand}>
        <View style={styles.headerIconWrap}>
          <SymbolView name={{ ios: 'calendar', android: 'event', web: 'event' }} tintColor="#4F46E5" size={26} />
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
              <View style={[styles.badge, { backgroundColor: '#05966922' }]}><Text style={[styles.badgeText, { color: '#059669' }]}>Marked</Text></View>
            ) : (
              <View style={[styles.badge, { backgroundColor: '#D9770622' }]}><Text style={[styles.badgeText, { color: '#D97706' }]}>Pending</Text></View>
            )}
          </View>
        ))
      )}

      <View style={styles.sectionLabelRow}>
        <Text style={styles.sectionLabel}>FULL WEEK</Text>
      </View>

      {loading ? (
        <ActivityIndicator size="large" color="#4F46E5" style={{ marginTop: 20 }} />
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
  root: { flex: 1, backgroundColor: '#F7F9FC' },
  headerBand: {
    flexDirection: 'row', alignItems: 'center',
    backgroundColor: '#FFFFFF', paddingHorizontal: 20, paddingVertical: 18,
    borderBottomWidth: 1, borderBottomColor: '#E7EAF2',
  },
  headerIconWrap: { width: 48, height: 48, borderRadius: 14, backgroundColor: '#4F46E520', justifyContent: 'center', alignItems: 'center' },
  headerTitle: { fontSize: 17, fontWeight: '700', color: '#0F172A' },
  headerSubtitle: { fontSize: 12, color: '#64748b', marginTop: 2 },
  sectionLabelRow: { paddingHorizontal: 20, paddingTop: 18, paddingBottom: 8 },
  sectionLabel: { fontSize: 11, fontWeight: '700', color: '#64748b', letterSpacing: 1 },
  emptyText: { fontSize: 13, color: '#64748b', paddingHorizontal: 20 },
  periodCard: {
    flexDirection: 'row', alignItems: 'center', backgroundColor: '#FFFFFF',
    borderRadius: 12, padding: 12, marginHorizontal: 16, marginBottom: 8,
    borderWidth: 1, borderColor: '#E7EAF2',
  },
  periodTime: { fontSize: 12, color: '#64748B', fontVariant: ['tabular-nums'] },
  periodSubject: { fontSize: 14, fontWeight: '700', color: '#0F172A' },
  periodClass: { fontSize: 11, color: '#64748b', marginTop: 2 },
  badge: { borderRadius: 8, paddingHorizontal: 8, paddingVertical: 4 },
  badgeText: { fontSize: 10, fontWeight: '700' },
  scroll: { flex: 1 },
  scrollContent: { paddingHorizontal: 16, paddingBottom: 32 },
  dayBlock: { marginBottom: 14 },
  dayLabel: { fontSize: 13, fontWeight: '700', color: '#0F172A', marginBottom: 6 },
  emptyDayText: { fontSize: 12, color: '#64748b' },
  weekRow: { flexDirection: 'row', gap: 10, paddingVertical: 4 },
  weekTime: { fontSize: 12, color: '#4F46E5', width: 50 },
  weekSubject: { fontSize: 12, color: '#0F172A', flex: 1 },
  weekClass: { fontSize: 11, color: '#64748b' },
});
