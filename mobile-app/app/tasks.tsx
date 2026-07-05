import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, ActivityIndicator, TouchableOpacity, Platform } from 'react-native';
import { SymbolView } from 'expo-symbols';
import { useAuth } from '@/context/AuthContext';
import { getApiHost } from '../services/api';

interface Task {
  id: string;
  title?: string;
  taskDescription?: string;
  taskType?: string;
  status?: string;
  createdAt?: string;
}

const TYPE_COLORS: Record<string, string> = {
  HOMEWORK:  '#6366f1',
  PRACTICE:  '#22c55e',
  PROJECT:   '#f59e0b',
  READING:   '#06b6d4',
};

const TYPE_BG: Record<string, string> = {
  HOMEWORK:  '#6366f122',
  PRACTICE:  '#22c55e22',
  PROJECT:   '#f59e0b22',
  READING:   '#06b6d422',
};

export default function TasksScreen() {
  const { userToken } = useAuth();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);

  const BASE_HOST = getApiHost();

  useEffect(() => {
    const fetchTasks = async () => {
      try {
        const resp = await fetch(`${BASE_HOST}/api/teacher/tasks/my-tasks`, {
          headers: { Authorization: `Bearer ${userToken}` }
        });
        const data = await resp.json();
        // handle both array and {value: [...]} shapes
        setTasks(Array.isArray(data) ? data : (data.value ?? []));
      } catch (e) {
        console.error('Failed to fetch tasks:', e);
        setTasks([]);
      } finally {
        setLoading(false);
      }
    };
    if (userToken) fetchTasks();
    else setLoading(false);
  }, [userToken]);

  return (
    <View style={styles.root}>
      <View style={styles.headerBand}>
        <View style={styles.headerIconWrap}>
          <SymbolView
            name={{ ios: 'checklist', android: 'checklist', web: 'checklist' }}
            tintColor="#6366f1"
            size={26}
          />
        </View>
        <View style={{ flex: 1, marginLeft: 14 }}>
          <Text style={styles.headerTitle}>My Tasks</Text>
          <Text style={styles.headerSubtitle}>Tasks you've assigned to students</Text>
        </View>
      </View>

      <View style={styles.sectionLabelRow}>
        <Text style={styles.sectionLabel}>ALL TASKS</Text>
        {tasks.length > 0 && (
          <View style={styles.sectionBadge}>
            <Text style={styles.sectionBadgeText}>{tasks.length} Tasks</Text>
          </View>
        )}
      </View>

      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator size="large" color="#6366f1" />
          <Text style={styles.loadingText}>Loading tasks...</Text>
        </View>
      ) : tasks.length === 0 ? (
        <View style={styles.center}>
          <SymbolView
            name={{ ios: 'checklist', android: 'checklist', web: 'checklist' }}
            tintColor="#334155"
            size={48}
          />
          <Text style={styles.emptyTitle}>No tasks yet</Text>
          <Text style={styles.emptySubtext}>Tasks you create will appear here</Text>
        </View>
      ) : (
        <ScrollView
          style={styles.scroll}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
        >
          {tasks.map((task) => {
            const type = task.taskType ?? 'HOMEWORK';
            const color = TYPE_COLORS[type] ?? '#6366f1';
            const bg = TYPE_BG[type] ?? '#6366f122';
            return (
              <View key={task.id} style={styles.card}>
                <View style={styles.cardLeft}>
                  <View style={[styles.typeBadge, { backgroundColor: bg }]}>
                    <Text style={[styles.typeText, { color }]}>{type}</Text>
                  </View>
                  <Text style={styles.cardTitle}>
                    {task.title ?? task.taskDescription ?? 'Untitled Task'}
                  </Text>
                  {task.createdAt && (
                    <Text style={styles.cardDate}>
                      {new Date(task.createdAt).toLocaleDateString('en-IN')}
                    </Text>
                  )}
                </View>
                <View style={[styles.statusDot, { backgroundColor: color }]} />
              </View>
            );
          })}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0f172a' },
  headerBand: {
    flexDirection: 'row', alignItems: 'center',
    backgroundColor: '#1e293b',
    paddingHorizontal: 20, paddingVertical: 18,
    borderBottomWidth: 1, borderBottomColor: '#334155',
  },
  headerIconWrap: {
    width: 48, height: 48, borderRadius: 14,
    backgroundColor: '#6366f115',
    justifyContent: 'center', alignItems: 'center',
  },
  headerTitle: { fontSize: 17, fontWeight: '700', color: '#f1f5f9', letterSpacing: 0.2 },
  headerSubtitle: { fontSize: 12, color: '#64748b', marginTop: 2 },
  sectionLabelRow: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: 20, paddingTop: 20, paddingBottom: 10, gap: 10,
  },
  sectionLabel: { fontSize: 11, fontWeight: '700', color: '#64748b', letterSpacing: 1.2 },
  sectionBadge: {
    backgroundColor: '#6366f122', borderRadius: 20,
    paddingHorizontal: 8, paddingVertical: 2,
  },
  sectionBadgeText: { fontSize: 11, color: '#6366f1', fontWeight: '600' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 20 },
  loadingText: { marginTop: 12, fontSize: 14, color: '#94a3b8' },
  emptyTitle: { fontSize: 16, fontWeight: '600', color: '#f1f5f9', marginTop: 16 },
  emptySubtext: { fontSize: 13, color: '#64748b', marginTop: 6, textAlign: 'center' },
  scroll: { flex: 1 },
  scrollContent: { paddingHorizontal: 16, paddingBottom: 32, gap: 12 },
  card: {
    backgroundColor: '#1e293b', borderRadius: 14, padding: 16,
    borderWidth: 1, borderColor: '#334155',
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
  },
  cardLeft: { flex: 1, marginRight: 12 },
  typeBadge: { alignSelf: 'flex-start', borderRadius: 8, paddingHorizontal: 8, paddingVertical: 3, marginBottom: 8 },
  typeText: { fontSize: 10, fontWeight: '700', letterSpacing: 0.5 },
  cardTitle: { fontSize: 14, fontWeight: '600', color: '#f1f5f9', lineHeight: 20 },
  cardDate: { fontSize: 11, color: '#64748b', marginTop: 4 },
  statusDot: { width: 8, height: 8, borderRadius: 4 },
});