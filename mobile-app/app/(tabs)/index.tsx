import { View, Text, StyleSheet, ScrollView, RefreshControl, TouchableOpacity, Platform, Alert } from 'react-native';
import { useAuth } from '@/context/AuthContext';
import { useContext, useState, useEffect } from 'react';
import { DataContext } from './_layout';
import { useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { getUnreadNotificationCount } from '../../services/api';
import { startTrip, stopTrip, isTripActive } from '../../services/driverLocationTask';

interface ParentQuest {
  taskDescription: string;
  xpBounty: number;
}

export default function DashboardScreen() {
  const ctx = useContext(DataContext);
  const role = ctx?.role ?? null;
  const data = ctx?.data ?? {};
  const refreshData = ctx?.refreshData ?? (async () => {});
  const selectedChildId = ctx?.selectedChildId ?? null;
  const selectChild = ctx?.selectChild ?? (() => {});
  const { firstName, schoolName } = useAuth();
  const [refreshing, setRefreshing] = useState(false);
  const router = useRouter();
  const [unreadCount, setUnreadCount] = useState(0);
  const [tripActive, setTripActive] = useState(false);
  const [tripBusy, setTripBusy] = useState(false);

  useEffect(() => {
    if (role === 'TEACHER') {
      getUnreadNotificationCount().then(setUnreadCount).catch(() => setUnreadCount(0));
    }
    if (role === 'DRIVER') {
      isTripActive().then(setTripActive).catch(() => setTripActive(false));
    }
  }, [role]);

  const handleToggleTrip = async () => {
    setTripBusy(true);
    try {
      if (tripActive) {
        await stopTrip();
        setTripActive(false);
      } else {
        await startTrip();
        setTripActive(true);
      }
      await refreshData();
    } catch (e: any) {
      Alert.alert('Trip sharing', e?.message ?? 'Could not update trip status.');
    } finally {
      setTripBusy(false);
    }
  };

  useEffect(() => {
    if (role !== 'DRIVER' || !tripActive) return;
    const interval = setInterval(() => {
      refreshData();
    }, 15000);
    return () => clearInterval(interval);
  }, [role, tripActive]);

  const onRefresh = async () => {
    setRefreshing(true);
    await refreshData();
    if (role === 'TEACHER') {
      try {
        const count = await getUnreadNotificationCount();
        setUnreadCount(count);
      } catch (e) {
        setUnreadCount(0);
      }
    }
    setRefreshing(false);
  };

  const getGreeting = () => {
    const hour = new Date().getHours();
    const timeGreeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
    if (role === 'TEACHER') return `${timeGreeting}, ${firstName || 'Educator'}!`;
    if (role === 'DRIVER') return `${timeGreeting}, ${firstName || 'Driver'}!`;
    if (role === 'PARENT') return `Hello, ${data.parent?.firstName || 'Guardian'}!`;
    return `Hello, ${data.student?.firstName || 'Scholar'}!`;
  };

  const getSubGreeting = () => {
    if (role === 'TEACHER') return `${schoolName || 'Your School'} · Staff Dashboard`;
    if (role === 'DRIVER') return `${schoolName || 'Your School'} · Bus Driver`;
    if (role === 'PARENT') return 'Parent Portal';
    return `${data.student?.gradeName || 'Grade N/A'} - ${data.student?.sectionName || 'N/A'}`;
  };

  const attendanceMarked = data.attendanceSummary?.markedToday ?? 0;
  const attendanceTotal = data.attendanceSummary?.totalClasses ?? 0;
  const attendancePending = data.attendanceSummary?.pendingToday ?? 0;
  const classes = Array.isArray(data.classes) ? data.classes : [];
  const tasks = Array.isArray(data.tasks) ? data.tasks : [];
  const parentQuests = Array.isArray(data.parentQuests) ? data.parentQuests : [];
  const children = Array.isArray(data.children) ? data.children : [];

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#fff" />}
    >
      {role === 'TEACHER' && (
        <View style={styles.notificationHeaderRow}>
          <TouchableOpacity onPress={() => router.push('/notifications')} style={styles.bellButton}>
            <SymbolView name={{ ios: 'bell', android: 'notifications', web: 'notifications' }} tintColor="#fff" size={24} />
            {unreadCount > 0 && (
              <View style={styles.badge}>
                <Text style={styles.badgeText}>{unreadCount > 9 ? '9+' : unreadCount}</Text>
              </View>
            )}
          </TouchableOpacity>
        </View>
      )}

      <View style={styles.infoCard}>
        <Text style={styles.greeting}>{getGreeting()}</Text>
        <Text style={styles.subGreeting}>{getSubGreeting()}</Text>
        {role === 'TEACHER' && (
          <View style={styles.dateChip}>
            <Text style={styles.dateChipText}>
              {new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'short', year: 'numeric' })}
            </Text>
          </View>
        )}
      </View>

      {role === 'PARENT' && children.length > 1 && (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.childSwitcherRow} contentContainerStyle={{ paddingHorizontal: 16 }}>
          {children.map((child: any) => {
            const active = child.id === selectedChildId;
            return (
              <TouchableOpacity
                key={child.id}
                onPress={() => selectChild(child.id)}
                style={[styles.childChip, active && styles.childChipActive]}
              >
                <Text style={[styles.childChipText, active && styles.childChipTextActive]}>
                  {child.firstName} {child.lastName}
                </Text>
              </TouchableOpacity>
            );
          })}
        </ScrollView>
      )}

      {role === 'TEACHER' ? (
        <>
          <View style={styles.metricsContainer}>
            <View style={styles.metricBox}>
              <Text style={styles.metricLabel}>Classes</Text>
              <Text style={styles.metricValue}>{classes.length}</Text>
            </View>
            <View style={styles.metricBox}>
              <Text style={styles.metricLabel}>Attendance</Text>
              <Text style={[styles.metricValue, {
                color: attendancePending > 0 ? '#f59e0b' : '#22c55e'
              }]}>
                {attendanceMarked}/{attendanceTotal}
              </Text>
            </View>
            <View style={styles.metricBox}>
              <Text style={[styles.metricLabel, {
                color: tasks.length > 0 ? '#f59e0b' : '#94a3b8'
              }]}>
                Pending Tasks
              </Text>
              <Text style={styles.metricValue}>{tasks.length}</Text>
            </View>
          </View>

          {attendancePending > 0 && (
            <View style={styles.alertCard}>
              <View style={styles.alertDot} />
              <Text style={styles.alertText}>
                {attendancePending} class{attendancePending > 1 ? 'es' : ''} still need attendance marked today
              </Text>
            </View>
          )}

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Quick Actions</Text>
            <View style={styles.cardGrid}>
              <TouchableOpacity style={styles.navCard} onPress={() => router.push('/teacher')}>
                <View style={[styles.navCardIcon, { backgroundColor: '#6366f120' }]}>
                  <SymbolView name={{ ios: 'person.badge.clock', android: 'school', web: 'school' }} tintColor="#818cf8" size={24} />
                </View>
                <Text style={styles.navCardTitle}>My Classes</Text>
                <Text style={styles.navCardSubtitle}>{classes.length} classes</Text>
              </TouchableOpacity>

              <TouchableOpacity style={styles.navCard} onPress={() => router.push('/tasks')}>
                <View style={[styles.navCardIcon, { backgroundColor: '#f59e0b20' }]}>
                  <SymbolView name={{ ios: 'checklist', android: 'task_alt', web: 'task_alt' }} tintColor="#f59e0b" size={24} />
                </View>
                <Text style={styles.navCardTitle}>Tasks</Text>
                <Text style={styles.navCardSubtitle}>{tasks.length} pending</Text>
              </TouchableOpacity>

              <TouchableOpacity style={styles.navCard} onPress={() => router.push('/gradebook')}>
                <View style={[styles.navCardIcon, { backgroundColor: '#22c55e20' }]}>
                  <SymbolView name={{ ios: 'chart.bar.doc.horizontal', android: 'grading', web: 'grading' }} tintColor="#4ade80" size={24} />
                </View>
                <Text style={styles.navCardTitle}>Gradebook</Text>
                <Text style={styles.navCardSubtitle}>Enter scores</Text>
              </TouchableOpacity>

              <TouchableOpacity style={styles.navCard} onPress={() => router.push('/timetable')}>
                <View style={[styles.navCardIcon, { backgroundColor: '#38bdf820' }]}>
                  <SymbolView name={{ ios: 'calendar', android: 'event', web: 'event' }} tintColor="#38bdf8" size={24} />
                </View>
                <Text style={styles.navCardTitle}>Timetable</Text>
                <Text style={styles.navCardSubtitle}>Today's schedule</Text>
              </TouchableOpacity>
            </View>
          </View>

          {Array.isArray(data.timetable) && data.timetable.length > 0 && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Today's Schedule</Text>
              {data.timetable.map((period: any) => (
                <View key={period.id} style={styles.periodItem}>
                  <View style={[styles.periodTimeBadge, { backgroundColor: period.attendanceMarked ? '#05966920' : '#f59e0b20' }]}>
                    <Text style={[styles.periodTime, { color: period.attendanceMarked ? '#059669' : '#f59e0b' }]}>
                      P{period.periodNumber}
                    </Text>
                  </View>
                  <View style={styles.periodInfo}>
                    <Text style={styles.periodSubject}>{period.subjectName}</Text>
                    <Text style={styles.periodMeta}>{period.startTime} – {period.endTime} · {period.roomNumber}</Text>
                  </View>
                  <View style={[styles.periodStatus, { backgroundColor: period.attendanceMarked ? '#05966915' : '#f59e0b15' }]}>
                    <Text style={{ fontSize: 11, fontWeight: '600', color: period.attendanceMarked ? '#059669' : '#f59e0b' }}>
                      {period.attendanceMarked ? 'Done' : 'Pending'}
                    </Text>
                  </View>
                </View>
              ))}
            </View>
          )}

          {tasks.length > 0 && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Recent Tasks</Text>
              {tasks.slice(0, 3).map((task: any, i: number) => (
                <View key={i} style={styles.listItem}>
                  <Text style={styles.itemTitle}>{task.title ?? task.taskDescription ?? 'Task'}</Text>
                  <View style={[styles.statusBadge, { backgroundColor: '#f59e0b22' }]}>
                    <Text style={{ color: '#f59e0b', fontSize: 12, fontWeight: '600' }}>Pending</Text>
                  </View>
                </View>
              ))}
            </View>
          )}
        </>
      ) : role === 'DRIVER' ? (
        <>
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{data.route?.assigned ? data.route.routeName : 'No route assigned'}</Text>
            {data.route?.assigned ? (
              <Text style={styles.infoText}>
                {data.route.lastPingAt
                  ? `Last location shared ${new Date(data.route.lastPingAt).toLocaleTimeString()}`
                  : 'No location shared yet'}
              </Text>
            ) : (
              <Text style={styles.infoText}>Ask your school admin to assign you to a bus route.</Text>
            )}
          </View>

          {Platform.OS === 'web' ? (
            <View style={styles.alertCard}>
              <View style={styles.alertDot} />
              <Text style={styles.alertText}>
                Live location sharing is only available in the native app, not the web version.
              </Text>
            </View>
          ) : (
            <TouchableOpacity
              style={[styles.tripButton, tripActive && styles.tripButtonActive]}
              disabled={!data.route?.assigned || tripBusy}
              onPress={handleToggleTrip}
            >
              <Text style={styles.tripButtonText}>
                {tripBusy ? 'Please wait…' : tripActive ? 'Stop Trip' : 'Start Trip'}
              </Text>
            </TouchableOpacity>
          )}
        </>
      ) : (
        <View style={styles.metricsContainer}>
          <View style={styles.metricBox}>
            <Text style={styles.metricLabel}>School XP</Text>
            <Text style={styles.metricValue}>{data.metrics?.schoolXp ?? 0}</Text>
          </View>
          <View style={styles.metricBox}>
            <Text style={styles.metricLabel}>Parent XP</Text>
            <Text style={styles.metricValue}>{data.metrics?.parentXp ?? 0}</Text>
          </View>
          <View style={styles.metricBox}>
            <Text style={styles.metricLabel}>Streak</Text>
            <Text style={styles.metricValue}>{data.metrics?.activeStreak ?? 0}</Text>
          </View>
        </View>
      )}

      {role !== 'TEACHER' && role !== 'DRIVER' && (
        <View style={styles.levelCard}>
          <Text style={styles.levelTitle}>Scholar Level {data.metrics?.scholarLevel ?? 1}</Text>
          <View style={styles.progressBarBg}>
            <View style={[styles.progressBarFill, { width: `${data.metrics?.levelProgress ?? 0}%` }]} />
          </View>
          <Text style={styles.levelHint}>{data.metrics?.xpToNextLevel ?? 500} XP to next level</Text>
        </View>
      )}

      {role === 'PARENT' && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>
            Child: {data.student?.firstName || 'Unknown'} {data.student?.lastName || 'Student'}
          </Text>
          <Text style={styles.infoText}>
            Attendance Today: {data.attendanceStatus || 'NOT MARKED'}
          </Text>
        </View>
      )}

      {role === 'STUDENT' && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Pending Quests</Text>
          {parentQuests.length === 0 ? (
            <Text style={styles.infoText}>No active quests.</Text>
          ) : (
            parentQuests.map((q: ParentQuest, i: number) => (
              <View key={i} style={styles.listItem}>
                <Text style={styles.itemTitle}>{q.taskDescription}</Text>
                <Text style={styles.itemReward}>+{q.xpBounty} XP</Text>
              </View>
            ))
          )}
        </View>
      )}

      <View style={{ height: 40 }} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, backgroundColor: '#0f172a', justifyContent: 'center', alignItems: 'center' },
  container: { flex: 1, backgroundColor: '#0f172a', padding: 16 },
  errorText: { color: '#ef4444', fontSize: 16 },
  infoCard: { backgroundColor: '#1e293b', padding: 20, borderRadius: 16, marginBottom: 16 },
  childSwitcherRow: { marginHorizontal: -16, marginBottom: 16 },
  childChip: { backgroundColor: '#1e293b', borderRadius: 20, paddingHorizontal: 16, paddingVertical: 8, marginRight: 8, borderWidth: 1, borderColor: '#334155' },
  childChipActive: { backgroundColor: '#6366f1', borderColor: '#6366f1' },
  childChipText: { color: '#94a3b8', fontSize: 14, fontWeight: '600' },
  childChipTextActive: { color: '#fff' },
  greeting: { color: '#fff', fontSize: 24, fontWeight: 'bold' },
  subGreeting: { color: '#94a3b8', fontSize: 16, marginTop: 4 },
  metricsContainer: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 16 },
  metricBox: { backgroundColor: '#1e293b', padding: 16, borderRadius: 16, flex: 1, marginHorizontal: 4, alignItems: 'center' },
  metricLabel: { color: '#94a3b8', fontSize: 12, marginBottom: 4 },
  metricValue: { color: '#fff', fontSize: 20, fontWeight: 'bold' },
  alertCard: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#f59e0b15', borderRadius: 12, padding: 12, marginBottom: 16, borderWidth: 1, borderColor: '#f59e0b30', gap: 8 },
  alertDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: '#f59e0b' },
  alertText: { color: '#f59e0b', fontSize: 13, flex: 1 },
  levelCard: { backgroundColor: '#1e293b', padding: 20, borderRadius: 16, marginBottom: 24 },
  levelTitle: { color: '#fff', fontSize: 16, fontWeight: 'bold', marginBottom: 12 },
  progressBarBg: { height: 8, backgroundColor: '#334155', borderRadius: 4, marginBottom: 8 },
  progressBarFill: { height: '100%', backgroundColor: '#6366f1', borderRadius: 4 },
  levelHint: { color: '#94a3b8', fontSize: 12, textAlign: 'right' },
  section: { marginBottom: 24 },
  sectionTitle: { color: '#fff', fontSize: 18, fontWeight: 'bold', marginBottom: 12 },
  infoText: { color: '#94a3b8', fontSize: 14 },
  listItem: { backgroundColor: '#1e293b', padding: 16, borderRadius: 12, marginBottom: 8, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  itemTitle: { color: '#fff', fontSize: 16, fontWeight: '600' },
  itemSubtitle: { color: '#64748b', fontSize: 12, marginTop: 4 },
  itemReward: { color: '#4ade80', fontWeight: 'bold' },
  statusBadge: { borderRadius: 20, paddingHorizontal: 10, paddingVertical: 4 },
  periodItem: { backgroundColor: '#1e293b', borderRadius: 12, padding: 12, marginBottom: 8, flexDirection: 'row', alignItems: 'center', gap: 12 },
  periodTimeBadge: { width: 40, height: 40, borderRadius: 10, justifyContent: 'center', alignItems: 'center' },
  periodTime: { fontSize: 13, fontWeight: '700' },
  periodInfo: { flex: 1 },
  periodSubject: { color: '#f1f5f9', fontSize: 14, fontWeight: '600' },
  periodMeta: { color: '#64748b', fontSize: 12, marginTop: 2 },
  periodStatus: { borderRadius: 8, paddingHorizontal: 8, paddingVertical: 4 },
  dateChip: { alignSelf: 'flex-start', backgroundColor: '#6366f120', borderRadius: 20, paddingHorizontal: 10, paddingVertical: 4, marginTop: 10, borderWidth: 1, borderColor: '#6366f130' },
  dateChipText: { color: '#818cf8', fontSize: 12, fontWeight: '500' },
  notificationHeaderRow: { flexDirection: 'row', justifyContent: 'flex-end', marginBottom: 8 },
  cardGrid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between' },
  navCard: { width: '48%', backgroundColor: '#1e293b', borderRadius: 16, padding: 16, marginBottom: 12 },
  navCardIcon: { width: 44, height: 44, borderRadius: 12, justifyContent: 'center', alignItems: 'center', marginBottom: 10 },
  navCardTitle: { color: '#fff', fontSize: 15, fontWeight: '600' },
  navCardSubtitle: { color: '#64748b', fontSize: 12, marginTop: 2 },
  tripButton: { backgroundColor: '#6366f1', borderRadius: 16, paddingVertical: 16, alignItems: 'center', marginBottom: 16 },
  tripButtonActive: { backgroundColor: '#ef4444' },
  tripButtonText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  bellButton: { padding: 8, position: 'relative' },
  badge: { position: 'absolute', top: 2, right: 2, backgroundColor: '#ef4444', borderRadius: 10, minWidth: 18, height: 18, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 4 },
  badgeText: { color: '#fff', fontSize: 10, fontWeight: '700' },
});