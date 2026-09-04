import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, ActivityIndicator } from 'react-native';
import { SymbolView } from 'expo-symbols';
import { useAuth } from '@/context/AuthContext';
import { getApiHost } from '../services/api';
import T from '../constants/theme';

interface Task {
  id: string;
  title?: string;
  taskDescription?: string;
  taskType?: string;
  status?: string;
  createdAt?: string;
}

const TYPE_COLORS: Record<string, string> = {
  HOMEWORK:  T.brand,
  PRACTICE:  T.success,
  PROJECT:   T.warn,
  READING:   '#06b6d4',
};

const TYPE_BG: Record<string, string> = {
  HOMEWORK:  T.brand50,
  PRACTICE:  T.success50,
  PROJECT:   T.warn50,
  READING:   '#06b6d422',
};

export default function TasksScreen() {
  const { userToken } = useAuth();
  // See teacher.tsx: `null` is "not fetched yet", so loading is derived from the
  // data rather than stored in a flag the effect had to correct on first render.
  const [tasks, setTasks] = useState<Task[] | null>(null);
  const loading = Boolean(userToken) && tasks === null;

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
      }
    };
    if (userToken) fetchTasks();
  }, [userToken, BASE_HOST]);

  return (
    <View style={styles.root}>
      <View style={styles.headerBand}>
        <View style={styles.headerIconWrap}>
          <SymbolView
            name={{ ios: 'checklist', android: 'checklist', web: 'checklist' }}
            tintColor={T.brand}
            size={26}
          />
        </View>
        <View style={{ flex: 1, marginLeft: 14 }}>
          <Text style={styles.headerTitle}>My Tasks</Text>
          <Text style={styles.headerSubtitle}>Tasks you’ve assigned to students</Text>
        </View>
      </View>

      <View style={styles.sectionLabelRow}>
        <Text style={styles.sectionLabel}>ALL TASKS</Text>
        {(tasks ?? []).length > 0 && (
          <View style={styles.sectionBadge}>
            <Text style={styles.sectionBadgeText}>{(tasks ?? []).length} Tasks</Text>
          </View>
        )}
      </View>

      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator size="large" color={T.brand} />
          <Text style={styles.loadingText}>Loading tasks...</Text>
        </View>
      ) : (tasks ?? []).length === 0 ? (
        <View style={styles.center}>
          <SymbolView
            name={{ ios: 'checklist', android: 'checklist', web: 'checklist' }}
            tintColor={T.text2}
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
          {(tasks ?? []).map((task) => {
            const type = task.taskType ?? 'HOMEWORK';
            const color = TYPE_COLORS[type] ?? T.brand;
            const bg = TYPE_BG[type] ?? T.brand50;
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
  root: { flex: 1, backgroundColor: T.bg },
  headerBand: {
    flexDirection: 'row', alignItems: 'center',
    backgroundColor: T.surface,
    paddingHorizontal: 20, paddingVertical: 18,
    borderBottomWidth: 1, borderBottomColor: T.line,
  },
  headerIconWrap: {
    width: 48, height: 48, borderRadius: 14,
    backgroundColor: T.brand50,
    justifyContent: 'center', alignItems: 'center',
  },
  headerTitle: { fontSize: 17, fontWeight: '700', color: T.text, letterSpacing: 0.2 },
  headerSubtitle: { fontSize: 12, color: T.text3, marginTop: 2 },
  sectionLabelRow: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: 20, paddingTop: 20, paddingBottom: 10, gap: 10,
  },
  sectionLabel: { fontSize: 11, fontWeight: '700', color: T.text3, letterSpacing: 1.2 },
  sectionBadge: {
    backgroundColor: T.brand50, borderRadius: 20,
    paddingHorizontal: 8, paddingVertical: 2,
  },
  sectionBadgeText: { fontSize: 11, color: T.brand, fontWeight: '600' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 20 },
  loadingText: { marginTop: 12, fontSize: 14, color: T.text3 },
  emptyTitle: { fontSize: 16, fontWeight: '600', color: T.text, marginTop: 16 },
  emptySubtext: { fontSize: 13, color: T.text3, marginTop: 6, textAlign: 'center' },
  scroll: { flex: 1 },
  scrollContent: { paddingHorizontal: 16, paddingBottom: 32, gap: 12 },
  card: {
    backgroundColor: T.surface, borderRadius: 14, padding: 16,
    borderWidth: 1, borderColor: T.line,
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
  },
  cardLeft: { flex: 1, marginRight: 12 },
  typeBadge: { alignSelf: 'flex-start', borderRadius: 8, paddingHorizontal: 8, paddingVertical: 3, marginBottom: 8 },
  typeText: { fontSize: 10, fontWeight: '700', letterSpacing: 0.5 },
  cardTitle: { fontSize: 14, fontWeight: '600', color: T.text, lineHeight: 20 },
  cardDate: { fontSize: 11, color: T.text3, marginTop: 4 },
  statusDot: { width: 8, height: 8, borderRadius: 4 },
});