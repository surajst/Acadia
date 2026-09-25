import { Stack } from 'expo-router';
import { View, ActivityIndicator, Text, TouchableOpacity } from 'react-native';
import { useCallback, useState, useEffect, createContext } from 'react';
import { getStudentDashboard, getParentDashboard, getApiHost, endedSession } from '../../services/api';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { useAuth } from '@/context/AuthContext';
import T from '../../constants/theme';

/**
 * The screen a cold deep link is opened *beneath*, so a link straight to
 * /fees still has the wheel under it and therefore a back arrow. Read by
 * expo-router at getRoutesCore.js:655.
 */
export const unstable_settings = { anchor: 'index' };

export const DataContext = createContext<any>({ role: null, data: {}, error: null, refreshData: async () => {}, selectedChildId: null, selectChild: (_id: string) => {} });

const ROLE_PARENT    = 'PARENT';
const ROLE_TEACHER   = 'TEACHER';
const ROLE_DRIVER    = 'DRIVER';
const ROLE_ADMIN     = 'ADMIN';
const ROLE_PRINCIPAL = 'PRINCIPAL';

// Admin/Principal accounts manage the school from the web dashboard — heavier
// data-entry tasks (staff, fees, bus routes, audit log) suit a bigger screen
// better than a phone. This screen redirects those roles there instead of
// falling through to a broken student-shaped dashboard fetch.
function WebOnlyRoleScreen({ role }: { role: string }) {
  const { logout } = useAuth();
  const host = getApiHost();
  return (
    <View style={{ flex: 1, backgroundColor: T.bg, justifyContent: 'center', alignItems: 'center', padding: 32 }}>
      <Text style={{ color: T.text, fontSize: 18, fontWeight: '700', textAlign: 'center', marginBottom: 12 }}>
        {role === ROLE_ADMIN ? 'Admin' : 'Principal'} accounts use the web dashboard
      </Text>
      <Text style={{ color: T.text3, fontSize: 14, textAlign: 'center', marginBottom: 24 }}>
        Open {host}/login in a browser on your computer or phone to manage your school.
      </Text>
      <TouchableOpacity
        onPress={() => logout()}
        style={{ backgroundColor: T.surface, borderWidth: 1, borderColor: T.line, borderRadius: 10, paddingHorizontal: 20, paddingVertical: 12 }}
      >
        <Text style={{ color: T.text3, fontWeight: '600' }}>Log Out</Text>
      </TouchableOpacity>
    </View>
  );
}

export default function TabLayout() {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [selectedChildId, setSelectedChildId] = useState<string | null>(null);
  // Why a request failed, or null. Before this, every failure was flattened into
  // an empty data object and each screen rendered its "nothing here" message --
  // which is how a parent whose token had expired overnight was shown "No fees
  // raised yet. The school has not billed anything for Your child" over 450
  // rupees outstanding. An empty state is a claim about the school's data, and
  // it must only ever be made when the server actually said so.
  const [error, setError] = useState<string | null>(null);
  const { userRole: role } = useAuth();

  const fetchDashboardData = useCallback(async (childId?: string | null) => {
    // Admins still manage the school from a bigger screen. Principals do not:
    // their work in the app is deciding what is waiting, and the approvals
    // screen fetches that itself.
    if (role === ROLE_ADMIN || role === ROLE_PRINCIPAL) {
      setData({});
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      if (role === ROLE_PARENT) {
        const parentData = await getParentDashboard(childId ?? selectedChildId ?? undefined);
        setData(parentData);
        // Keep the selection in sync with whichever child the server resolved
        // (e.g. on first load, before the parent has picked one explicitly).
        if (parentData?.student?.id) {
          setSelectedChildId(parentData.student.id);
        }
      } else if (role === ROLE_TEACHER) {
        const token = await AsyncStorage.getItem('userToken');
        const BASE_HOST = getApiHost();
        const [classesResp, tasksResp, attendanceSummaryResp, timetableResp, queueResp] = await Promise.all([
          fetch(`${BASE_HOST}/api/teacher/classes`, {
            headers: { Authorization: `Bearer ${token}` }
          }),
          fetch(`${BASE_HOST}/api/teacher/tasks/my-tasks`, {
            headers: { Authorization: `Bearer ${token}` }
          }),
          fetch(`${BASE_HOST}/api/teacher/attendance/summary`, {
            headers: { Authorization: `Bearer ${token}` }
          }),
          fetch(`${BASE_HOST}/api/teacher/timetable/today`, {
            headers: { Authorization: `Bearer ${token}` }
          }),
          fetch(`${BASE_HOST}/api/teacher/queue`, {
            headers: { Authorization: `Bearer ${token}` }
          })
        ]);
        // These bypass the axios interceptor, so they have to check for
        // themselves. A 401 clears the session and the route gate takes over;
        // there is nothing useful to render in the meantime.
        const responses = [classesResp, tasksResp, attendanceSummaryResp, timetableResp, queueResp];
        for (const resp of responses) {
          if (await endedSession(resp)) return;
        }
        // And a failure that is not a 401 must not be parsed as data: an error
        // body is JSON too, and .value ?? [] turns it into an empty list.
        const failed = responses.find((r) => !r.ok);
        if (failed) {
          throw new Error(`Request failed with ${failed.status}`);
        }

        const classes = await classesResp.json();
        const tasksRaw = await tasksResp.json();
        const tasks = Array.isArray(tasksRaw) ? tasksRaw : (tasksRaw.value ?? []);
        const attendanceSummary = await attendanceSummaryResp.json();
        const timetableRaw = await timetableResp.json();
        const timetable = Array.isArray(timetableRaw) ? timetableRaw : (timetableRaw.value ?? []);
        const queue = await queueResp.json();
        setData({ teacher: true, classes, tasks, attendanceSummary, timetable, queue });
      } else if (role === ROLE_DRIVER) {
        const token = await AsyncStorage.getItem('userToken');
        const BASE_HOST = getApiHost();
        const routeResp = await fetch(`${BASE_HOST}/api/mobile/driver/route/my-route`, {
          headers: { Authorization: `Bearer ${token}` }
        });
        if (await endedSession(routeResp)) return;
        if (!routeResp.ok) {
          throw new Error(`Request failed with ${routeResp.status}`);
        }
        const route = await routeResp.json();
        setData({ driver: true, route });
      } else {
        const studentData = await getStudentDashboard();
        setData(studentData);
      }
    } catch (err: any) {
      // Deliberately NOT setData({}). Clearing the data is what turned a failed
      // request into a confident "there is nothing here" on every screen. What
      // was loaded before stays; the error says the figures may be stale.
      console.log('Dashboard data sync error:', err);
      setError(
        err?.message === 'Network Error' || err?.code === 'ERR_NETWORK'
          ? 'Could not reach the school. Check your connection and try again.'
          : 'Could not load the latest information. Please try again.',
      );
    } finally {
      setLoading(false);
    }
  }, [role, selectedChildId]);

  const selectChild = useCallback((childId: string) => {
    setSelectedChildId(childId);
    void fetchDashboardData(childId);
  }, [fetchDashboardData]);

  useEffect(() => {
    void fetchDashboardData();
  }, [fetchDashboardData]);

  // The isStudent/isParent/isTeacher/isPrincipal booleans that used to live
  // here existed only to feed `href:` on each Tabs.Screen. Role now decides
  // which spokes the wheel shows, in constants/wheel.ts -- one place, instead
  // of thirteen scattered conditionals.

  // Both oversight roles work from the web dashboard. Principals briefly had
  // the approvals queue here, but their job is review and data entry on a
  // bigger screen; the queue still exists at /approvals and the route is a
  // one-line revert away if that turns out to be wrong.
  if (role === ROLE_ADMIN || role === ROLE_PRINCIPAL) {
    return <WebOnlyRoleScreen role={role} />;
  }

  // Only the very first load takes the whole screen. Later refetches -- a pull
  // to refresh, a parent switching between children -- used to tear the entire
  // tab navigator down and rebuild it, so the tab bar vanished and the screen
  // flashed for a request that usually returns in under a second.
  if (loading && !data) {
    return (
      <DataContext.Provider value={{ role, data: {}, error, refreshData: fetchDashboardData, selectedChildId, selectChild }}>
        <View style={{ flex: 1, backgroundColor: T.bg, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color={T.brand} />
        </View>
      </DataContext.Provider>
    );
  }

  if (!data) {
    return (
      <DataContext.Provider value={{ role, data: {}, error, refreshData: fetchDashboardData, selectedChildId, selectChild }}>
        <View style={{ flex: 1, backgroundColor: T.bg }} />
      </DataContext.Provider>
    );
  }

  return (
    <DataContext.Provider value={{ role, data, error, refreshData: fetchDashboardData, selectedChildId, selectChild }}>
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: T.bg },
          // Was #fff, which put white text on a white header bar -- the screen
          // title was invisible on every stack screen that showed one.
          headerTintColor: T.text,
          contentStyle: { backgroundColor: T.bg },
        }}>

        {/* The wheel. Its own gradient header carries the greeting and the
            Profile/Settings corners, so the navigator draws none -- that
            double-header over the gradient is what headerShown:true used to
            produce here. Every other screen keeps its header, because that
            header is what provides the back arrow now the tab bar is gone. */}
        <Stack.Screen name="index" options={{ headerShown: false }} />

        <Stack.Screen name="syllabus" options={{ title: 'Syllabus' }} />
        <Stack.Screen name="student-attendance" options={{ title: 'Attendance' }} />
        <Stack.Screen name="challenges" options={{ title: 'Challenges' }} />
        <Stack.Screen name="quests" options={{ title: 'Quests' }} />
        <Stack.Screen name="attendance" options={{ title: 'Attendance' }} />
        <Stack.Screen name="performance" options={{ title: 'Performance' }} />
        <Stack.Screen name="approvals" options={{ title: 'Approvals' }} />
        <Stack.Screen name="fees" options={{ title: 'Fees' }} />
        <Stack.Screen name="bus" options={{ title: 'Bus' }} />
        <Stack.Screen name="announcements" options={{ title: 'News' }} />
        <Stack.Screen name="messages" options={{ title: 'Messages' }} />
        <Stack.Screen name="profile" options={{ title: 'Profile' }} />
        <Stack.Screen name="settings" options={{ title: 'Settings' }} />
      </Stack>
    </DataContext.Provider>
  );
}