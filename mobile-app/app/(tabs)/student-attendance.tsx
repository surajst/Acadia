import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  RefreshControl,
  ActivityIndicator,
} from 'react-native';
import { useContext, useState, useEffect } from 'react';
import { DataContext } from './_layout';
import { getStudentAttendance } from '../../services/api';

type AttendanceRecord = {
  date: string;
  status: 'PRESENT' | 'ABSENT' | 'TARDY' | 'LATE';
};

const STATUS_COLOR: Record<string, string> = {
  PRESENT: '#059669',
  ABSENT: '#ef4444',
  TARDY: '#D97706',
  LATE: '#D97706',
};

const STATUS_BG: Record<string, string> = {
  PRESENT: '#14532d',
  ABSENT: '#7f1d1d',
  TARDY: '#78350f',
  LATE: '#78350f',
};

function getMonthLabel(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00');
  return d.toLocaleString('default', { month: 'long', year: 'numeric' });
}

function getDayLabel(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00');
  return String(d.getDate());
}

export default function StudentAttendanceScreen() {
  const { refreshData } = useContext(DataContext);
  const [refreshing, setRefreshing] = useState(false);
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchAttendance = async () => {
    setLoading(true);
    try {
      const data = await getStudentAttendance();
      setRecords(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Failed to fetch student attendance:', err);
      setRecords([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAttendance();
  }, []);

  const onRefresh = async () => {
    setRefreshing(true);
    await refreshData();
    await fetchAttendance();
    setRefreshing(false);
  };

  const total = records.length;
  const present = records.filter(r => r.status === 'PRESENT').length;
  const absent = records.filter(r => r.status === 'ABSENT').length;
  const late = records.filter(r => r.status === 'TARDY' || r.status === 'LATE').length;
  const pct = total > 0 ? Math.round((present / total) * 100) : 0;

  // Group records by month
  const byMonth: Record<string, AttendanceRecord[]> = {};
  records.forEach(r => {
    const label = getMonthLabel(r.date);
    if (!byMonth[label]) byMonth[label] = [];
    byMonth[label].push(r);
  });

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#4F46E5" />}
    >
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Attendance Summary</Text>
        <View style={styles.summaryRow}>
          <View style={[styles.summaryBox, { borderColor: '#059669' }]}>
            <Text style={styles.summaryNum}>{present}</Text>
            <Text style={styles.summaryLabel}>Present</Text>
          </View>
          <View style={[styles.summaryBox, { borderColor: '#ef4444' }]}>
            <Text style={styles.summaryNum}>{absent}</Text>
            <Text style={styles.summaryLabel}>Absent</Text>
          </View>
          <View style={[styles.summaryBox, { borderColor: '#D97706' }]}>
            <Text style={styles.summaryNum}>{late}</Text>
            <Text style={styles.summaryLabel}>Late</Text>
          </View>
          <View style={[styles.summaryBox, { borderColor: '#4F46E5' }]}>
            <Text style={styles.summaryNum}>{pct}%</Text>
            <Text style={styles.summaryLabel}>Rate</Text>
          </View>
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Attendance History</Text>
        {loading ? (
          <ActivityIndicator color="#4F46E5" style={{ marginTop: 20 }} />
        ) : records.length === 0 ? (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyTitle}>No attendance records</Text>
            <Text style={styles.emptySubtext}>No attendance has been logged yet.</Text>
          </View>
        ) : (
          Object.entries(byMonth).map(([monthLabel, monthRecords]) => (
            <View key={monthLabel} style={styles.monthBlock}>
              <Text style={styles.monthLabel}>{monthLabel}</Text>
              <View style={styles.calendarGrid}>
                {monthRecords.map(r => (
                  <View key={r.date} style={[styles.dayCell, { backgroundColor: STATUS_BG[r.status] || '#FFFFFF' }]}>
                    <Text style={[styles.dayNumber, { color: STATUS_COLOR[r.status] || '#fff' }]}>
                      {getDayLabel(r.date)}
                    </Text>
                    <Text style={[styles.dayStatus, { color: STATUS_COLOR[r.status] || '#fff' }]}>
                      {r.status === 'PRESENT' ? '✓' : r.status === 'ABSENT' ? '✗' : r.status.charAt(0)}
                    </Text>
                  </View>
                ))}
              </View>
            </View>
          ))
        )}
      </View>

      <View style={{ height: 40 }} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F7F9FC',
    padding: 16,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    color: '#0F172A',
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 12,
  },
  summaryRow: {
    flexDirection: 'row',
    gap: 8,
  },
  summaryBox: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 10,
    alignItems: 'center',
    borderWidth: 1.5,
  },
  summaryNum: {
    color: '#0F172A',
    fontSize: 20,
    fontWeight: 'bold',
  },
  summaryLabel: {
    color: '#64748B',
    fontSize: 11,
    marginTop: 2,
  },
  monthBlock: {
    marginBottom: 20,
  },
  monthLabel: {
    color: '#64748B',
    fontSize: 13,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 1,
    marginBottom: 10,
  },
  calendarGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  dayCell: {
    width: 44,
    height: 52,
    borderRadius: 10,
    justifyContent: 'center',
    alignItems: 'center',
  },
  dayNumber: {
    fontSize: 14,
    fontWeight: '700',
  },
  dayStatus: {
    fontSize: 12,
    marginTop: 2,
  },
  emptyCard: {
    height: 100,
    paddingVertical: 16,
    paddingHorizontal: 24,
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyTitle: {
    color: '#0F172A',
    fontSize: 15,
    fontWeight: '600',
    textAlign: 'center',
  },
  emptySubtext: {
    color: '#64748B',
    fontSize: 13,
    marginTop: 4,
    textAlign: 'center',
  },
});
