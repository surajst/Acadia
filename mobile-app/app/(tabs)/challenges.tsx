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

const TASK_TYPE_COLOR: Record<Challenge['taskType'], string> = {
  HOMEWORK: '#3b82f6',
  READING: '#a855f7',
  PROJECT: '#f59e0b',
};

const TASK_TYPE_BG: Record<Challenge['taskType'], string> = {
  HOMEWORK: '#1e3a8a',
  READING: '#581c87',
  PROJECT: '#78350f',
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
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#fff" />}
    >
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Active Challenges</Text>
        {loading ? (
          <ActivityIndicator color="#6366f1" style={{ marginTop: 20 }} />
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
                <View style={[styles.badge, { backgroundColor: TASK_TYPE_BG[item.taskType] || '#334155' }]}>
                  <Text style={[styles.badgeText, { color: TASK_TYPE_COLOR[item.taskType] || '#94a3b8' }]}>
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
    backgroundColor: '#0f172a',
    padding: 16,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    color: '#fff',
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 12,
  },
  card: {
    backgroundColor: '#1e293b',
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
    color: '#fff',
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
    color: '#94a3b8',
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
    borderTopColor: '#334155',
  },
  xpText: {
    color: '#4ade80',
    fontSize: 15,
    fontWeight: '700',
  },
  dateText: {
    color: '#64748b',
    fontSize: 12,
  },
  emptyCard: {
    height: 100,
    paddingVertical: 16,
    paddingHorizontal: 24,
    backgroundColor: '#1e293b',
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyTitle: {
    color: '#fff',
    fontSize: 15,
    fontWeight: '600',
    textAlign: 'center',
  },
  emptySubtext: {
    color: '#94a3b8',
    fontSize: 13,
    marginTop: 4,
    textAlign: 'center',
  },
});
