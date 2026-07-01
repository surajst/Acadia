import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  RefreshControl,
  ActivityIndicator,
  TouchableOpacity,
} from 'react-native';
import { useContext, useState, useEffect } from 'react';
import { DataContext } from './_layout';
import { getParentAttendance } from '../../services/api';

// ─── Types ────────────────────────────────────────────────────────────────────
type AttendanceRecord = {
  date: string;       // ISO yyyy-MM-dd
  status: 'PRESENT' | 'ABSENT' | 'LATE' | 'HOLIDAY';
};

// ─── Mock data — mirrors the backend Attendance entity structure ───────────────
// Covers the current month; falls back to last month if no past weekdays exist yet
function buildMockRecords(): AttendanceRecord[] {
  function recordsForMonth(year: number, month: number): AttendanceRecord[] {
    const today = new Date();
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const records: AttendanceRecord[] = [];
    for (let d = 1; d <= daysInMonth; d++) {
      const date = new Date(year, month, d);
      const day = date.getDay();
      if (day === 0 || day === 6) continue; // skip weekends
      if (date > today) continue;           // skip future
      const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const status: AttendanceRecord['status'] = d % 7 === 3 ? 'ABSENT' : 'PRESENT';
      records.push({ date: dateStr, status });
    }
    return records;
  }

  const now = new Date();
  let records = recordsForMonth(now.getFullYear(), now.getMonth());
  // If current month has no past weekdays yet, use the previous month
  if (records.length === 0) {
    const prev = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    records = recordsForMonth(prev.getFullYear(), prev.getMonth());
  }
  return records;
}

const MOCK_RECORDS = buildMockRecords();

// ─── Helpers ──────────────────────────────────────────────────────────────────
const STATUS_COLOR: Record<AttendanceRecord['status'], string> = {
  PRESENT: '#22c55e',
  ABSENT: '#ef4444',
  LATE: '#f59e0b',
  HOLIDAY: '#475569',
};

const STATUS_BG: Record<AttendanceRecord['status'], string> = {
  PRESENT: '#14532d',
  ABSENT: '#7f1d1d',
  LATE: '#78350f',
  HOLIDAY: '#1e293b',
};

function getMonthLabel(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00');
  return d.toLocaleString('default', { month: 'long', year: 'numeric' });
}

function getDayLabel(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00');
  return String(d.getDate());
}

// ─── Sub-components ───────────────────────────────────────────────────────────
function AttendanceDayCell({ record }: { record: AttendanceRecord }) {
  return (
    <View style={[styles.dayCell, { backgroundColor: STATUS_BG[record.status] }]}>
      <Text style={[styles.dayNumber, { color: STATUS_COLOR[record.status] }]}>
        {getDayLabel(record.date)}
      </Text>
      <Text style={[styles.dayStatus, { color: STATUS_COLOR[record.status] }]}>
        {record.status === 'PRESENT' ? '✓' : record.status === 'ABSENT' ? '✗' : record.status.charAt(0)}
      </Text>
    </View>
  );
}

function AttendanceSummary({ records }: { records: AttendanceRecord[] }) {
  const total = records.length;
  const present = records.filter(r => r.status === 'PRESENT').length;
  const absent = records.filter(r => r.status === 'ABSENT').length;
  const pct = total > 0 ? Math.round((present / total) * 100) : 0;

  return (
    <View style={styles.summaryRow}>
      <View style={[styles.summaryBox, { borderColor: '#22c55e' }]}>
        <Text style={styles.summaryNum}>{present}</Text>
        <Text style={styles.summaryLabel}>Present</Text>
      </View>
      <View style={[styles.summaryBox, { borderColor: '#ef4444' }]}>
        <Text style={styles.summaryNum}>{absent}</Text>
        <Text style={styles.summaryLabel}>Absent</Text>
      </View>
      <View style={[styles.summaryBox, { borderColor: '#6366f1' }]}>
        <Text style={styles.summaryNum}>{pct}%</Text>
        <Text style={styles.summaryLabel}>Attendance</Text>
      </View>
    </View>
  );
}

// ─── Child selector ───────────────────────────────────────────────────────────
function ChildBadge({ name, grade, section }: { name: string; grade: string; section: string }) {
  return (
    <View style={styles.childBadge}>
      <View style={styles.childAvatar}>
        <Text style={styles.childAvatarText}>{name.charAt(0)}</Text>
      </View>
      <View>
        <Text style={styles.childName}>{name}</Text>
        <Text style={styles.childGrade}>{grade} – {section}</Text>
      </View>
    </View>
  );
}

// ─── Main Screen ─────────────────────────────────────────────────────────────
export default function AttendanceScreen() {
  const { role, data, refreshData } = useContext(DataContext);
  const [refreshing, setRefreshing] = useState(false);
  const [records, setRecords] = useState<AttendanceRecord[] | null>(null);
  const [loading, setLoading] = useState(false);

  const studentId = data?.student?.id;
  const studentName = `${data?.student?.firstName ?? 'Child'} ${data?.student?.lastName ?? ''}`.trim();
  const gradeName = data?.student?.gradeName ?? 'Grade';
  const sectionName = data?.student?.sectionName ?? 'Section';

  const fetchAttendance = async () => {
    if (!studentId) { setRecords(null); return; }
    setLoading(true);
    try {
      const result = await getParentAttendance(studentId);
      // Guard: only store if API returned a real array of records
      const safeResult = Array.isArray(result) && result.length > 0 ? result : null;
      setRecords(safeResult);
    } catch {
      setRecords(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (role === 'PARENT') fetchAttendance();
  }, [role, studentId]);

  const onRefresh = async () => {
    setRefreshing(true);
    await refreshData();
    await fetchAttendance();
    setRefreshing(false);
  };

  // Double-guard: ensure we always have a real array before iterating
  const displayRecords: AttendanceRecord[] = Array.isArray(records) && records.length > 0
    ? records
    : Array.isArray(MOCK_RECORDS) ? MOCK_RECORDS : [];

  // Group records by month label
  const byMonth: Record<string, AttendanceRecord[]> = {};
  displayRecords.forEach(r => {
    const label = getMonthLabel(r.date);
    if (!byMonth[label]) byMonth[label] = [];
    byMonth[label].push(r);
  });

  if (role !== 'PARENT') {
    return (
      <View style={styles.restrictedContainer}>
        <Text style={styles.restrictedIcon}>🔒</Text>
        <Text style={styles.restrictedTitle}>Parent View Only</Text>
        <Text style={styles.restrictedSub}>This section is available for parent accounts.</Text>
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#fff" />}
    >
      {/* Child Selector Header */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Monitoring</Text>
        <ChildBadge name={studentName} grade={gradeName} section={sectionName} />
      </View>

      {/* Summary Stats */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>
          {records ? 'This Month' : 'This Month (Sample)'} · Attendance Summary
        </Text>
        <AttendanceSummary records={displayRecords} />
      </View>

      {/* Calendar Grid */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Attendance Calendar</Text>

        {loading ? (
          <ActivityIndicator color="#6366f1" style={{ marginTop: 20 }} />
        ) : (
          Object.entries(byMonth).map(([monthLabel, monthRecords]) => (
            <View key={monthLabel} style={styles.monthBlock}>
              <Text style={styles.monthLabel}>{monthLabel}</Text>
              <View style={styles.calendarGrid}>
                {monthRecords.map(r => (
                  <AttendanceDayCell key={r.date} record={r} />
                ))}
              </View>
            </View>
          ))
        )}
      </View>

      {/* Legend */}
      <View style={styles.legendRow}>
        {(['PRESENT', 'ABSENT', 'LATE'] as const).map(s => (
          <View key={s} style={styles.legendItem}>
            <View style={[styles.legendDot, { backgroundColor: STATUS_COLOR[s] }]} />
            <Text style={styles.legendText}>{s.charAt(0) + s.slice(1).toLowerCase()}</Text>
          </View>
        ))}
      </View>

      <View style={{ height: 40 }} />
    </ScrollView>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0f172a',
    padding: 16,
  },
  restrictedContainer: {
    flex: 1,
    backgroundColor: '#0f172a',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 32,
  },
  restrictedIcon: { fontSize: 48, marginBottom: 16 },
  restrictedTitle: { color: '#fff', fontSize: 20, fontWeight: 'bold', marginBottom: 8 },
  restrictedSub: { color: '#94a3b8', fontSize: 14, textAlign: 'center' },

  section: { marginBottom: 24 },
  sectionTitle: {
    color: '#fff',
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 12,
  },

  // Child badge
  childBadge: {
    backgroundColor: '#1e293b',
    borderRadius: 14,
    padding: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  childAvatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: '#6366f1',
    justifyContent: 'center',
    alignItems: 'center',
  },
  childAvatarText: { color: '#fff', fontSize: 20, fontWeight: 'bold' },
  childName: { color: '#fff', fontSize: 16, fontWeight: '700' },
  childGrade: { color: '#94a3b8', fontSize: 13 },

  // Summary boxes
  summaryRow: { flexDirection: 'row', gap: 10 },
  summaryBox: {
    flex: 1,
    backgroundColor: '#1e293b',
    borderRadius: 14,
    padding: 14,
    alignItems: 'center',
    borderWidth: 1.5,
  },
  summaryNum: { color: '#fff', fontSize: 24, fontWeight: 'bold' },
  summaryLabel: { color: '#94a3b8', fontSize: 12, marginTop: 2 },

  // Month block
  monthBlock: { marginBottom: 20 },
  monthLabel: {
    color: '#94a3b8',
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
  dayNumber: { fontSize: 14, fontWeight: '700' },
  dayStatus: { fontSize: 12, marginTop: 2 },

  // Legend
  legendRow: { flexDirection: 'row', gap: 16, paddingHorizontal: 4, marginBottom: 8 },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  legendDot: { width: 10, height: 10, borderRadius: 5 },
  legendText: { color: '#94a3b8', fontSize: 13 },
});
