import { View, Text, StyleSheet, ScrollView, RefreshControl, TouchableOpacity, Platform, Alert } from 'react-native';
import { useAuth } from '@/context/AuthContext';
import { useContext, useState, useEffect, useMemo } from 'react';
import { DataContext } from './_layout';
import { getUnreadNotificationCount } from '../../services/api';
import { startTrip, stopTrip, isTripActive } from '../../services/driverLocationTask';
import StudentDashboard from '../../components/StudentDashboard';
import HomeWheel from '../../components/HomeWheel';
import TeacherDashboard from '../../components/TeacherDashboard';
import ParentDashboard from '../../components/ParentDashboard';
import { useTheme, type Theme } from '../../context/ThemeContext';

export default function DashboardScreen() {
  const T = useTheme();
  const styles = useMemo(() => makeStyles(T), [T]);
  const ctx = useContext(DataContext);
  const role = ctx?.role ?? null;
  const data = ctx?.data ?? {};
  const refreshData = ctx?.refreshData ?? (async () => {});
  const selectedChildId = ctx?.selectedChildId ?? null;
  const selectChild = ctx?.selectChild ?? (() => {});
  const { firstName, schoolName } = useAuth();
  const [refreshing, setRefreshing] = useState(false);
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
      } catch {
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

  if (role === 'TEACHER') {
    return (
      <TeacherDashboard
        data={data}
        firstName={firstName}
        schoolName={schoolName}
        unreadCount={unreadCount}
        refreshing={refreshing}
        onRefresh={onRefresh}
      />
    );
  }

  if (role === 'PARENT') {
    return (
      <ParentDashboard
        data={data}
        parentFirstName={data.parent?.firstName ?? firstName}
        refreshing={refreshing}
        onRefresh={onRefresh}
        selectedChildId={selectedChildId}
        selectChild={selectChild}
      />
    );
  }

  if (role === 'STUDENT') {
    return (
      <StudentDashboard
        data={data}
        schoolName={schoolName}
        refreshing={refreshing}
        onRefresh={onRefresh}
        refreshData={refreshData}
      />
    );
  }

  // Only DRIVER and PRINCIPAL reach here -- every other role returned above.
  // What used to follow was ~350 lines branching on TEACHER and PARENT, all of
  // it unreachable, plus two NavCard grids that no role could open.
  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={T.brand} />}
    >
      <View style={styles.infoCard}>
        <Text style={styles.greeting}>{getGreeting()}</Text>
        <Text style={styles.subGreeting}>{getSubGreeting()}</Text>
      </View>

      {/* Both roles carry Profile and Settings on the rim rather than in header
          corners -- with one destination and none respectively, a ring of two
          would read as a rendering fault. See MIN_ORBIT in constants/wheel.ts. */}
      <HomeWheel />

      {role === 'DRIVER' && (
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
            /* The driver's actual job, and a stateful toggle rather than a
               destination -- which is why it is not a spoke. */
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
      )}

      <View style={{ height: 40 }} />
    </ScrollView>
  );
}

const makeStyles = (T: Theme) => StyleSheet.create({
  center: { flex: 1, backgroundColor: T.bg, justifyContent: 'center', alignItems: 'center' },
  container: { flex: 1, backgroundColor: T.bg, padding: 16 },
  errorText: { color: T.danger, fontSize: 16 },
  infoCard: { backgroundColor: T.surface, padding: 20, borderRadius: 16, marginBottom: 16 },
  childSwitcherRow: { marginHorizontal: -16, marginBottom: 16 },
  childChip: { backgroundColor: T.surface, borderRadius: 20, paddingHorizontal: 16, paddingVertical: 8, marginRight: 8, borderWidth: 1, borderColor: T.line },
  childChipActive: { backgroundColor: T.brand, borderColor: T.brand },
  childChipText: { color: T.text3, fontSize: 14, fontWeight: '600' },
  childChipTextActive: { color: T.surface },
  greeting: { color: T.text, fontSize: 24, fontWeight: 'bold' },
  subGreeting: { color: T.text3, fontSize: 16, marginTop: 4 },
  alertCard: { flexDirection: 'row', alignItems: 'center', backgroundColor: T.warn50, borderRadius: 12, padding: 12, marginBottom: 16, borderWidth: 1, borderColor: T.warn50, gap: 8 },
  alertDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: T.warn },
  alertText: { color: T.warnInk, fontSize: 13, flex: 1 },
  levelCard: { backgroundColor: T.surface, padding: 20, borderRadius: 16, marginBottom: 24 },
  levelTitle: { color: T.text, fontSize: 16, fontWeight: 'bold', marginBottom: 12 },
  progressBarBg: { height: 8, backgroundColor: T.line, borderRadius: 4, marginBottom: 8 },
  progressBarFill: { height: '100%', backgroundColor: T.brand, borderRadius: 4 },
  levelHint: { color: T.text3, fontSize: 12, textAlign: 'right' },
  section: { marginBottom: 24 },
  sectionTitle: { color: T.text, fontSize: 18, fontWeight: 'bold', marginBottom: 12 },
  infoText: { color: T.text3, fontSize: 14 },
  listItem: { backgroundColor: T.surface, padding: 16, borderRadius: 12, marginBottom: 8, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  itemTitle: { color: T.text, fontSize: 16, fontWeight: '600' },
  itemSubtitle: { color: T.text3, fontSize: 12, marginTop: 4 },
  itemReward: { color: T.successInk, fontWeight: 'bold' },
  statusBadge: { borderRadius: 20, paddingHorizontal: 10, paddingVertical: 4 },
  periodItem: { backgroundColor: T.surface, borderRadius: 12, padding: 12, marginBottom: 8, flexDirection: 'row', alignItems: 'center', gap: 12 },
  periodTimeBadge: { width: 40, height: 40, borderRadius: 10, justifyContent: 'center', alignItems: 'center' },
  periodTime: { fontSize: 13, fontWeight: '700' },
  periodInfo: { flex: 1 },
  periodSubject: { color: T.text, fontSize: 14, fontWeight: '600' },
  periodMeta: { color: T.text3, fontSize: 12, marginTop: 2 },
  periodStatus: { borderRadius: 8, paddingHorizontal: 8, paddingVertical: 4 },
  dateChip: { alignSelf: 'flex-start', backgroundColor: T.brand50, borderRadius: 20, paddingHorizontal: 10, paddingVertical: 4, marginTop: 10, borderWidth: 1, borderColor: T.brand100 },
  dateChipText: { color: T.brand, fontSize: 12, fontWeight: '500' },
  notificationHeaderRow: { flexDirection: 'row', justifyContent: 'flex-end', marginBottom: 8 },
  cardGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  tripButton: { backgroundColor: T.brand, borderRadius: 16, paddingVertical: 16, alignItems: 'center', marginBottom: 16 },
  tripButtonActive: { backgroundColor: T.danger },
  tripButtonText: { color: T.surface, fontSize: 16, fontWeight: '700' },
  bellButton: { padding: 8, position: 'relative' },
  badge: { position: 'absolute', top: 2, right: 2, backgroundColor: T.danger, borderRadius: 10, minWidth: 18, height: 18, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 4 },
  badgeText: { color: T.surface, fontSize: 10, fontWeight: '700' },
});
/**
 * Recognition rows. Kept separate from `styles` so the shared dashboard
 * styling stays readable -- these are only used by the parent's award list.
 */
