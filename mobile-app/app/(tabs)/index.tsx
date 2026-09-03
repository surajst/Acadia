import { View, Text, StyleSheet, ScrollView, RefreshControl, TouchableOpacity, Platform, Alert } from 'react-native';
import { useAuth } from '@/context/AuthContext';
import { useContext, useState, useEffect } from 'react';
import { DataContext } from './_layout';
import { useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { getUnreadNotificationCount } from '../../services/api';
import { startTrip, stopTrip, isTripActive } from '../../services/driverLocationTask';
import { Stat, StatRow } from '../../components/ui/Stat';
import NavCard from '../../components/ui/NavCard';
import T from '../../constants/theme';

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
  const pendingReviews = (data.queue?.pendingSubmissions?.length ?? 0) + (data.queue?.pendingProgress?.length ?? 0);
  const parentQuests = Array.isArray(data.parentQuests) ? data.parentQuests : [];
  const children = Array.isArray(data.children) ? data.children : [];

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={T.brand} />}
    >
      {role === 'TEACHER' && (
        <View style={styles.notificationHeaderRow}>
          <TouchableOpacity onPress={() => router.push('/notifications')} style={styles.bellButton}>
            <SymbolView name={{ ios: 'bell', android: 'notifications', web: 'notifications' }} tintColor={T.text2} size={24} />
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
          <StatRow>
            <Stat label="Classes" value={classes.length} />
            <Stat
              label="Attendance"
              value={`${attendanceMarked}/${attendanceTotal}`}
              tone={attendancePending > 0 ? 'attention' : 'good'}
            />
            <Stat
              label="Tasks"
              value={tasks.length}
              tone={tasks.length > 0 ? 'attention' : 'neutral'}
            />
          </StatRow>

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
              {/* Only the card with work waiting carries colour. When every
                  tile has its own tinted icon circle, nothing stands out and
                  the grid reads as decoration rather than a queue. */}
              <NavCard
                to="/verification"
                icon={{ ios: 'checkmark.seal', android: 'verified', web: 'verified' }}
                title="Verification"
                subtitle={pendingReviews > 0 ? `${pendingReviews} waiting` : 'Queue cleared'}
                urgent={pendingReviews > 0}
              />
              <NavCard
                to="/teacher"
                icon={{ ios: 'person.badge.clock', android: 'school', web: 'school' }}
                title="My Classes"
                subtitle={`${classes.length} ${classes.length === 1 ? 'class' : 'classes'}`}
              />
              <NavCard
                to="/tasks"
                icon={{ ios: 'checklist', android: 'task_alt', web: 'task_alt' }}
                title="Tasks"
                subtitle={tasks.length > 0 ? `${tasks.length} pending` : 'None assigned'}
                urgent={tasks.length > 0}
              />
              <NavCard
                to="/gradebook"
                icon={{ ios: 'chart.bar.doc.horizontal', android: 'grading', web: 'grading' }}
                title="Gradebook"
                subtitle="Enter scores"
              />
              <NavCard
                to="/timetable"
                icon={{ ios: 'calendar', android: 'event', web: 'event' }}
                title="Timetable"
                subtitle="Today's schedule"
              />
            </View>
          </View>

          {Array.isArray(data.timetable) && data.timetable.length > 0 && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Today's Schedule</Text>
              {data.timetable.map((period: any) => (
                <View key={period.id} style={styles.periodItem}>
                  <View style={[styles.periodTimeBadge, { backgroundColor: period.attendanceMarked ? T.success50 : T.warn50 }]}>
                    <Text style={[styles.periodTime, { color: period.attendanceMarked ? T.success : T.warn }]}>
                      P{period.periodNumber}
                    </Text>
                  </View>
                  <View style={styles.periodInfo}>
                    <Text style={styles.periodSubject}>{period.subjectName}</Text>
                    <Text style={styles.periodMeta}>{period.startTime} – {period.endTime} · {period.roomNumber}</Text>
                  </View>
                  <View style={[styles.periodStatus, { backgroundColor: period.attendanceMarked ? T.success50 : T.warn50 }]}>
                    <Text style={{ fontSize: 11, fontWeight: '600', color: period.attendanceMarked ? T.success : T.warn }}>
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
                  <View style={[styles.statusBadge, { backgroundColor: T.warn50 }]}>
                    <Text style={{ color: T.warn, fontSize: 12, fontWeight: '600' }}>Pending</Text>
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
        <StatRow>
          <Stat label="School XP" value={data.metrics?.schoolXp ?? 0} />
          <Stat label="Parent XP" value={data.metrics?.parentXp ?? 0} />
          <Stat label="Streak" value={data.metrics?.activeStreak ?? 0} />
        </StatRow>
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

      {/* The screens that came out of the tab bar. Same treatment the teacher
          role has used since its own bar was cut from seven items to three. */}
      {role === 'PARENT' && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Quick Actions</Text>
          <View style={styles.cardGrid}>
            <NavCard
              to="/performance"
              icon={{ ios: 'chart.bar', android: 'bar_chart', web: 'bar_chart' }}
              title="Performance"
              subtitle="Marks and report cards"
            />
            <NavCard
              to="/bus"
              icon={{ ios: 'bus', android: 'directions_bus', web: 'directions_bus' }}
              title="Bus"
              subtitle="Live pickup status"
            />
            <NavCard
              to="/announcements"
              icon={{ ios: 'megaphone', android: 'campaign', web: 'campaign' }}
              title="News"
              subtitle="From your school"
            />
          </View>
        </View>
      )}

      {role === 'STUDENT' && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Quick Actions</Text>
          <View style={styles.cardGrid}>
            <NavCard
              to="/quests"
              icon={{ ios: 'star', android: 'star', web: 'star' }}
              title="Quests"
              subtitle={parentQuests.length > 0 ? `${parentQuests.length} to claim` : 'Set by your family'}
              urgent={parentQuests.length > 0}
            />
            <NavCard
              to="/student-attendance"
              icon={{ ios: 'calendar', android: 'event', web: 'event' }}
              title="Attendance"
              subtitle="Your record"
            />
          </View>
        </View>
      )}

      {/* What their teacher recognised them for. Directly under the XP tiles
          on purpose: the number above is meaningless on its own, and this is
          the part a parent actually reads. Hidden entirely when there is
          nothing yet -- an empty "recognition" heading reads as a school that
          has not noticed their child. */}
      {role === 'PARENT' && Array.isArray(data.awards) && data.awards.length > 0 && (
        <View style={styles.section}>
          {/* The card shows the newest few; the full history answers the other
              question a parent asks -- what has my child been noticed for. */}
          <TouchableOpacity
            style={awardStyles.headerRow}
            onPress={() => router.push('/recognition')}
            accessibilityRole="button"
          >
            <Text style={styles.sectionTitle}>Recognised by their teacher</Text>
            <Text style={awardStyles.seeAll}>See all ›</Text>
          </TouchableOpacity>
          {data.awards.slice(0, 5).map((a: any, i: number) => (
            <View
              key={a.id ?? `award-${i}`}
              style={[awardStyles.row, i === Math.min(data.awards.length, 5) - 1 && awardStyles.rowLast]}
            >
              <Text style={awardStyles.emoji}>{a.emoji || '🏅'}</Text>
              <View style={awardStyles.body}>
                <Text style={awardStyles.label}>
                  {a.label} <Text style={awardStyles.points}>+{a.points}</Text>
                </Text>
                {a.reason ? <Text style={awardStyles.reason}>{a.reason}</Text> : null}
                <Text style={awardStyles.meta}>
                  {[a.awardedByName, a.createdAt ? new Date(a.createdAt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' }) : null]
                    .filter(Boolean)
                    .join(' · ')}
                </Text>
              </View>
            </View>
          ))}
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
  alertText: { color: T.warn, fontSize: 13, flex: 1 },
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
const awardStyles = StyleSheet.create({
  headerRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', minHeight: 44 },
  seeAll: { fontSize: 12.5, fontWeight: '600', color: T.brand },
  row: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: T.track,
  },
  rowLast: { borderBottomWidth: 0 },
  emoji: { fontSize: 18, lineHeight: 22 },
  body: { flex: 1 },
  label: { fontSize: 13.5, fontWeight: '600', color: T.text },
  points: { color: T.brand, fontWeight: '700' },
  reason: { fontSize: 12.5, color: T.text2, marginTop: 2 },
  meta: { fontSize: 11, color: T.text4, marginTop: 2 },
});
