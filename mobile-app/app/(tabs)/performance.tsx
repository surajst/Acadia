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
import { getSubjectPerformance } from '../../services/api';

// ─── Types ────────────────────────────────────────────────────────────────────
type SubjectPerformance = {
  subjectType: string;
  averagePercentage: number;
  trend: number[];
};

// ─── Helpers ──────────────────────────────────────────────────────────────────
const SUBJECT_LABEL: Record<string, string> = {
  MATHEMATICS: 'Mathematics',
  SCIENCE: 'Science',
  SOCIAL_SCIENCE: 'Social Science',
  ENGLISH: 'English',
  LANGUAGE: 'Language',
};

function scoreColor(pct: number): string {
  if (pct >= 80) return '#22c55e';
  if (pct >= 60) return '#f59e0b';
  return '#ef4444';
}

// ─── Sub-components ───────────────────────────────────────────────────────────
function SubjectCard({ subject }: { subject: SubjectPerformance }) {
  const color = scoreColor(subject.averagePercentage);
  const label = SUBJECT_LABEL[subject.subjectType] ?? subject.subjectType;

  return (
    <View style={styles.subjectCard}>
      <View style={styles.subjectHeader}>
        <Text style={styles.subjectName}>{label}</Text>
        <Text style={[styles.subjectPct, { color }]}>{subject.averagePercentage}%</Text>
      </View>
      <View style={styles.progressTrack}>
        <View
          style={[
            styles.progressFill,
            { width: `${Math.min(100, subject.averagePercentage)}%`, backgroundColor: color },
          ]}
        />
      </View>
      {subject.trend.length > 1 && (
        <View style={styles.trendRow}>
          {subject.trend.map((pct, i) => (
            <View key={i} style={[styles.trendDot, { backgroundColor: scoreColor(pct) }]} />
          ))}
          <Text style={styles.trendLabel}>Last {subject.trend.length} assessments</Text>
        </View>
      )}
    </View>
  );
}

// ─── Main Screen ─────────────────────────────────────────────────────────────
export default function PerformanceScreen() {
  const { role, data, refreshData } = useContext(DataContext);
  const [refreshing, setRefreshing] = useState(false);
  const [subjects, setSubjects] = useState<SubjectPerformance[] | null>(
    data?.subjectPerformance ?? null
  );
  const [loading, setLoading] = useState(false);

  const studentId = data?.student?.id;

  const fetchPerformance = async () => {
    if (!studentId) { setSubjects(null); return; }
    setLoading(true);
    try {
      const result = await getSubjectPerformance(studentId);
      setSubjects(Array.isArray(result) ? result : null);
    } catch {
      setSubjects(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (role === 'PARENT' && !data?.subjectPerformance) fetchPerformance();
  }, [role, studentId]);

  const onRefresh = async () => {
    setRefreshing(true);
    await refreshData();
    await fetchPerformance();
    setRefreshing(false);
  };

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
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Subject-wise Performance</Text>

        {loading ? (
          <ActivityIndicator color="#6366f1" style={{ marginTop: 20 }} />
        ) : subjects && subjects.length > 0 ? (
          subjects.map((s) => <SubjectCard key={s.subjectType} subject={s} />)
        ) : (
          <View style={styles.emptyState}>
            <Text style={styles.emptyText}>No graded assessments yet.</Text>
            <Text style={styles.emptySub}>Scores will appear here once teachers enter marks.</Text>
          </View>
        )}
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

  subjectCard: {
    backgroundColor: '#1e293b',
    borderRadius: 14,
    padding: 16,
    marginBottom: 12,
  },
  subjectHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  subjectName: { color: '#fff', fontSize: 16, fontWeight: '700' },
  subjectPct: { fontSize: 18, fontWeight: 'bold' },

  progressTrack: {
    height: 8,
    borderRadius: 4,
    backgroundColor: '#334155',
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 4,
  },

  trendRow: { flexDirection: 'row', alignItems: 'center', gap: 4, marginTop: 10 },
  trendDot: { width: 8, height: 8, borderRadius: 4 },
  trendLabel: { color: '#94a3b8', fontSize: 12, marginLeft: 6 },

  emptyState: {
    backgroundColor: '#1e293b',
    borderRadius: 14,
    padding: 24,
    alignItems: 'center',
  },
  emptyText: { color: '#fff', fontSize: 15, fontWeight: '600', marginBottom: 4 },
  emptySub: { color: '#94a3b8', fontSize: 13, textAlign: 'center' },
});
