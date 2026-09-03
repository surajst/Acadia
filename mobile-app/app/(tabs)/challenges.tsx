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
import { getStudentTasks } from '../../services/api';
import T from '../../constants/theme';

type Challenge = {
  id: string;
  title: string;
  description: string;
  subjectType: string;
  taskType: 'HOMEWORK' | 'READING' | 'PROJECT';
  xpReward: number;
  dueDate: string;
  taskStatus: string;
};

// One status family per type, ink on its own tint. The previous pairing was a
// dark-theme leftover: a near-black badge with a mid-bright label.
const TASK_TYPE_COLOR: Record<Challenge['taskType'], string> = {
  HOMEWORK: T.infoInk,
  READING: T.brandInk,
  PROJECT: T.warnInk,
};

const TASK_TYPE_BG: Record<Challenge['taskType'], string> = {
  HOMEWORK: T.info50,
  READING: T.brand50,
  PROJECT: T.warn50,
};

export default function ChallengesScreen() {
  const { refreshData } = useContext(DataContext);
  const [refreshing, setRefreshing] = useState(false);
  const [challenges, setChallenges] = useState<Challenge[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchChallenges = async () => {
    setLoading(true);
    try {
      const data = await getStudentTasks();
      setChallenges(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Failed to fetch student tasks/challenges:', err);
      setChallenges([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchChallenges();
  }, []);

  const onRefresh = async () => {
    setRefreshing(true);
    await refreshData();
    await fetchChallenges();
    setRefreshing(false);
  };

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={T.brand} />}
    >
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Active Challenges</Text>
        {loading ? (
          <ActivityIndicator color={T.brand} style={{ marginTop: 20 }} />
        ) : challenges.length === 0 ? (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyTitle}>No challenges assigned yet.</Text>
            <Text style={styles.emptySubtext}>Check back later!</Text>
          </View>
        ) : (
          challenges.map((item) => (
            <View key={item.id} style={styles.card}>
              <View style={styles.cardHeader}>
                <Text style={styles.cardTitle}>{item.title}</Text>
                <View style={[styles.badge, { backgroundColor: TASK_TYPE_BG[item.taskType] || T.line }]}>
                  <Text style={[styles.badgeText, { color: TASK_TYPE_COLOR[item.taskType] || T.text3 }]}>
                    {item.taskType}
                  </Text>
                </View>
              </View>
              <Text style={styles.cardDescription}>{item.description}</Text>
              <View style={styles.cardFooter}>
                <Text style={styles.xpText}>+{item.xpReward} XP</Text>
                {item.dueDate && (
                  <Text style={styles.dateText}>Due: {item.dueDate}</Text>
                )}
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
    backgroundColor: T.bg,
    padding: 16,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    color: T.text,
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 12,
  },
  card: {
    backgroundColor: T.surface,
    borderRadius: 14,
    padding: 16,
    marginBottom: 12,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: 8,
  },
  cardTitle: {
    color: T.text,
    fontSize: 16,
    fontWeight: '700',
    flex: 1,
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 8,
  },
  badgeText: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  cardDescription: {
    color: T.text3,
    fontSize: 14,
    marginTop: 8,
    lineHeight: 20,
  },
  cardFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: T.line,
  },
  xpText: {
    color: T.successInk,
    fontSize: 15,
    fontWeight: '700',
  },
  dateText: {
    color: T.text3,
    fontSize: 12,
  },
  emptyCard: {
    height: 100,
    paddingVertical: 16,
    paddingHorizontal: 24,
    backgroundColor: T.surface,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyTitle: {
    color: T.text,
    fontSize: 15,
    fontWeight: '600',
    textAlign: 'center',
  },
  emptySubtext: {
    color: T.text3,
    fontSize: 13,
    marginTop: 4,
    textAlign: 'center',
  },
});
