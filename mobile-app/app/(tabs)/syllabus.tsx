import {
  View, Text, StyleSheet, ScrollView, RefreshControl,
  TouchableOpacity, ActivityIndicator,
} from 'react-native';
import { useContext, useState, useEffect, useCallback } from 'react';
import { DataContext } from './_layout';
import { getStudentSyllabus } from '../../services/api';

interface Topic {
  id: string;
  topicName: string;
  subjectType: string;
  standard: number;
  xpReward: number;
  topicOrder: number;
  completed: boolean;
}

// Display labels for subject enum values
const SUBJECT_DISPLAY: Record<string, string> = {
  SCIENCE:        'Science',
  SOCIAL_SCIENCE: 'Social Science',
  ENGLISH:        'English',
  MATHEMATICS:    'Math',
};

// Accent colour per subject (used on group headers)
const SUBJECT_ACCENT: Record<string, string> = {
  SCIENCE:        '#22c55e',
  SOCIAL_SCIENCE: '#f59e0b',
  ENGLISH:        '#3b82f6',
  MATHEMATICS:    '#a855f7',
};

// Chip labels → backend enum (MATHEMATICS omitted — 0 topics in DB)
const CHIP_LABELS = ['All', 'Science', 'Social Science', 'English'];
const CHIP_TO_ENUM: Record<string, string> = {
  'Science':        'SCIENCE',
  'Social Science': 'SOCIAL_SCIENCE',
  'English':        'ENGLISH',
};

// Preferred display order for subject groups
const SUBJECT_ORDER = ['SCIENCE', 'SOCIAL_SCIENCE', 'ENGLISH', 'MATHEMATICS'];

export default function SyllabusScreen() {
  const { role } = useContext(DataContext);
  const [refreshing, setRefreshing]       = useState(false);
  const [activeSubject, setActiveSubject] = useState('All');
  const [topics, setTopics]               = useState<Topic[]>([]);
  const [loading, setLoading]             = useState(true);
  const [error, setError]                 = useState<string | null>(null);

  const fetchSyllabus = useCallback(async () => {
    try {
      setError(null);
      const data = await getStudentSyllabus();
      setTopics(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Syllabus fetch error:', err);
      setError('Could not load syllabus. Pull down to retry.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchSyllabus(); }, [fetchSyllabus]);

  const onRefresh = async () => {
    setRefreshing(true);
    await fetchSyllabus();
    setRefreshing(false);
  };

  // Filter by active chip
  const filtered = activeSubject === 'All'
    ? topics
    : topics.filter(t => t.subjectType === CHIP_TO_ENUM[activeSubject]);

  // Group by subjectType, sort groups by preferred display order
  const grouped: Record<string, Topic[]> = {};
  filtered.forEach(t => {
    if (!grouped[t.subjectType]) grouped[t.subjectType] = [];
    grouped[t.subjectType].push(t);
  });
  const subjectKeys = Object.keys(grouped).sort(
    (a, b) => SUBJECT_ORDER.indexOf(a) - SUBJECT_ORDER.indexOf(b),
  );

  if (loading) {
    return (
      <View style={[styles.container, styles.center]}>
        <ActivityIndicator size="large" color="#6366f1" />
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#fff" />}
    >
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Curriculum Overview</Text>

        {/* Subject filter chips */}
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipContainer}>
          {CHIP_LABELS.map(label => (
            <TouchableOpacity
              key={label}
              style={[styles.chip, activeSubject === label && styles.chipActive]}
              onPress={() => setActiveSubject(label)}
            >
              <Text style={[styles.chipText, activeSubject === label && styles.chipTextActive]}>
                {label}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>

        <View style={{ height: 16 }} />

        {error ? (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyText}>{error}</Text>
          </View>
        ) : filtered.length === 0 ? (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyText}>No topics available for this subject.</Text>
          </View>
        ) : (
          subjectKeys.map(subjectKey => {
            const subjectTopics = grouped[subjectKey];
            const completedCount = subjectTopics.filter(t => t.completed).length;
            const accent = SUBJECT_ACCENT[subjectKey] ?? '#6366f1';

            return (
              <View key={subjectKey} style={styles.subjectGroup}>

                {/* Group header — only shown in "All" view */}
                {activeSubject === 'All' && (
                  <View style={[styles.subjectHeader, { borderLeftColor: accent }]}>
                    <Text style={styles.subjectName}>
                      {SUBJECT_DISPLAY[subjectKey] ?? subjectKey}
                    </Text>
                    <Text style={[styles.subjectProgress, { color: accent }]}>
                      {completedCount}/{subjectTopics.length} completed
                    </Text>
                  </View>
                )}

                {subjectTopics.map(topic => (
                  <View key={topic.id} style={styles.card}>
                    <View style={styles.topicRow}>
                      <View style={[styles.indicator, topic.completed && styles.indicatorDone]}>
                        <Text style={[styles.indicatorText, topic.completed && styles.indicatorTextDone]}>
                          {topic.completed ? '✓' : String(topic.topicOrder)}
                        </Text>
                      </View>
                      <View style={styles.topicTextContainer}>
                        <Text style={styles.itemTitle}>{topic.topicName}</Text>
                        <Text style={styles.itemSubtitle}>
                          {SUBJECT_DISPLAY[topic.subjectType] ?? topic.subjectType} • {topic.xpReward} XP
                        </Text>
                      </View>
                      {topic.completed && (
                        <View style={styles.doneBadge}>
                          <Text style={styles.doneBadgeText}>Done</Text>
                        </View>
                      )}
                    </View>
                  </View>
                ))}
              </View>
            );
          })
        )}
      </View>
      <View style={{ height: 40 }} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container:          { flex: 1, backgroundColor: '#0f172a', padding: 16 },
  center:             { justifyContent: 'center', alignItems: 'center' },
  section:            { marginBottom: 24 },
  sectionTitle:       { color: '#fff', fontSize: 20, fontWeight: 'bold', marginBottom: 12 },
  chipContainer:      { flexDirection: 'row', gap: 8, paddingBottom: 8 },
  chip:               { paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20,
                        backgroundColor: '#1e293b', borderWidth: 1, borderColor: '#334155' },
  chipActive:         { backgroundColor: '#6366f1', borderColor: '#6366f1' },
  chipText:           { color: '#94a3b8', fontWeight: '600' },
  chipTextActive:     { color: '#ffffff' },
  subjectGroup:       { marginBottom: 20 },
  subjectHeader:      { flexDirection: 'row', justifyContent: 'space-between',
                        alignItems: 'center', paddingLeft: 10,
                        borderLeftWidth: 3, marginBottom: 10 },
  subjectName:        { color: '#e2e8f0', fontSize: 15, fontWeight: '700' },
  subjectProgress:    { fontSize: 13, fontWeight: '600' },
  card:               { backgroundColor: '#1e293b', padding: 14, borderRadius: 12,
                        marginBottom: 8, borderWidth: 1, borderColor: '#334155' },
  topicRow:           { flexDirection: 'row', alignItems: 'center', gap: 12 },
  indicator:          { width: 28, height: 28, borderRadius: 14, borderWidth: 2,
                        borderColor: '#475569', justifyContent: 'center',
                        alignItems: 'center', backgroundColor: '#0f172a' },
  indicatorDone:      { borderColor: '#22c55e', backgroundColor: 'rgba(34,197,94,0.15)' },
  indicatorText:      { color: '#94a3b8', fontSize: 11, fontWeight: 'bold' },
  indicatorTextDone:  { color: '#22c55e', fontSize: 14 },
  topicTextContainer: { flex: 1 },
  itemTitle:          { color: '#fff', fontSize: 15, fontWeight: '600', marginBottom: 3 },
  itemSubtitle:       { color: '#94a3b8', fontSize: 13 },
  doneBadge:          { backgroundColor: 'rgba(34,197,94,0.15)', borderRadius: 8,
                        paddingHorizontal: 8, paddingVertical: 3,
                        borderWidth: 1, borderColor: 'rgba(34,197,94,0.3)' },
  doneBadgeText:      { color: '#22c55e', fontSize: 11, fontWeight: '700' },
  emptyCard:          { backgroundColor: '#1e293b', padding: 16, borderRadius: 12,
                        borderWidth: 1, borderColor: '#334155',
                        justifyContent: 'center', alignItems: 'center', height: 90 },
  emptyText:          { color: '#94a3b8', fontSize: 14, textAlign: 'center' },
});
