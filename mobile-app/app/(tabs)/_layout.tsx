import { SymbolView } from 'expo-symbols';
import { Tabs } from 'expo-router';
import { View, ActivityIndicator, Platform, Text, TouchableOpacity } from 'react-native';
import { useCallback, useState, useEffect, createContext } from 'react';
import { getStudentDashboard, getParentDashboard, getApiHost } from '../../services/api';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { useClientOnlyValue } from '@/components/useClientOnlyValue';
import { useAuth } from '@/context/AuthContext';
import T from '../../constants/theme';

export const DataContext = createContext<any>({ role: null, data: {}, refreshData: async () => {}, selectedChildId: null, selectChild: (_id: string) => {} });

const ROLE_STUDENT   = 'STUDENT';
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
    <View style={{ flex: 1, backgroundColor: '#F7F9FC', justifyContent: 'center', alignItems: 'center', padding: 32 }}>
      <Text style={{ color: '#0F172A', fontSize: 18, fontWeight: '700', textAlign: 'center', marginBottom: 12 }}>
        {role === ROLE_ADMIN ? 'Admin' : 'Principal'} accounts use the web dashboard
      </Text>
      <Text style={{ color: '#64748B', fontSize: 14, textAlign: 'center', marginBottom: 24 }}>
        Open {host}/login in a browser on your computer or phone to manage your school.
      </Text>
      <TouchableOpacity
        onPress={() => logout()}
        style={{ backgroundColor: '#FFFFFF', borderWidth: 1, borderColor: '#E7EAF2', borderRadius: 10, paddingHorizontal: 20, paddingVertical: 12 }}
      >
        <Text style={{ color: '#64748B', fontWeight: '600' }}>Log Out</Text>
      </TouchableOpacity>
    </View>
  );
}

export default function TabLayout() {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [selectedChildId, setSelectedChildId] = useState<string | null>(null);
  const { userRole: role } = useAuth();
  const headerShown = useClientOnlyValue(false, true);

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
        const route = await routeResp.json();
        setData({ driver: true, route });
      } else {
        const studentData = await getStudentDashboard();
        setData(studentData);
      }
    } catch (error) {
      console.log('Dashboard data sync error:', error);
      setData({});
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

  const isStudent = role === ROLE_STUDENT || (!role && true);
  const isParent  = role === ROLE_PARENT;
  const isTeacher = role === ROLE_TEACHER;
  const isDriver  = role === ROLE_DRIVER;
  const isPrincipal = role === ROLE_PRINCIPAL;

  // Admins only. Principals now have the approvals queue, which is the work
  // they actually do from a phone.
  if (role === ROLE_ADMIN) {
    return <WebOnlyRoleScreen role={role} />;
  }

  if (loading) {
    return (
      <DataContext.Provider value={{ role, data: {}, refreshData: fetchDashboardData, selectedChildId, selectChild }}>
        <View style={{ flex: 1, backgroundColor: T.bg, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color={T.brand} />
        </View>
      </DataContext.Provider>
    );
  }

  if (!data) {
    return (
      <DataContext.Provider value={{ role, data: {}, refreshData: fetchDashboardData, selectedChildId, selectChild }}>
        <View style={{ flex: 1, backgroundColor: T.bg }} />
      </DataContext.Provider>
    );
  }

  return (
    <DataContext.Provider value={{ role, data, refreshData: fetchDashboardData, selectedChildId, selectChild }}>
      <Tabs
        screenOptions={{
          tabBarActiveTintColor:   T.brand,
          tabBarInactiveTintColor: T.text3,
          tabBarStyle: { backgroundColor: T.surface, borderTopColor: T.line },
          headerStyle: { backgroundColor: T.bg },
          // Was #fff, which put white text on a white header bar -- the screen
          // title was invisible on every stack screen that showed one.
          headerTintColor: T.text,
          headerShown: headerShown,
        }}>

        <Tabs.Screen
          name="index"
          options={{
            title: 'Dashboard',
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'house', android: 'house', web: 'house' }} tintColor={color} size={28} />
            ),
          }}
        />

        <Tabs.Screen
          name="syllabus"
          options={{
            title: 'Syllabus',
            href: isStudent ? undefined : null,
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'book', android: 'book', web: 'book' }} tintColor={color} size={28} />
            ),
          }}
        />

        <Tabs.Screen
          name="student-attendance"
          options={{
            title: 'Attendance',
            href: isStudent ? undefined : null,
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'calendar', android: 'event', web: 'event' }} tintColor={color} size={28} />
            ),
          }}
        />

        <Tabs.Screen
          name="challenges"
          options={{
            title: 'Challenges',
            href: isStudent ? undefined : null,
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'star.circle', android: 'military_tech', web: 'military_tech' }} tintColor={color} size={28} />
            ),
          }}
        />

        <Tabs.Screen
          name="quests"
          options={{
            title: 'Quests',
            href: isStudent ? undefined : null,
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'star', android: 'star', web: 'star' }} tintColor={color} size={28} />
            ),
          }}
        />

        <Tabs.Screen
          name="attendance"
          options={{
            title: 'Attendance',
            href: isParent ? undefined : null,
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'calendar', android: 'event', web: 'event' }} tintColor={color} size={28} />
            ),
          }}
        />

        <Tabs.Screen
          name="performance"
          options={{
            title: 'Performance',
            href: isParent ? undefined : null,
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'chart.bar', android: 'bar_chart', web: 'bar_chart' }} tintColor={color} size={28} />
            ),
          }}
        />

        {/* Parents only. Fee collection is the school's job and lives on the
            web dashboard; this tab is the family's own read-only view of what
            they owe and what they have paid. */}
        {/* Principals decide things while walking around a school, not at a
            desk -- which is why this queue used to sit for days. */}
        <Tabs.Screen
          name="approvals"
          options={{
            title: 'Approvals',
            href: isPrincipal ? undefined : null,
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'checkmark.seal', android: 'verified', web: 'verified' }} tintColor={color} size={28} />
            ),
          }}
        />

        <Tabs.Screen
          name="fees"
          options={{
            title: 'Fees',
            href: isParent ? undefined : null,
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'creditcard', android: 'payments', web: 'payments' }} tintColor={color} size={28} />
            ),
          }}
        />

        <Tabs.Screen
          name="bus"
          options={{
            title: 'Bus',
            href: isParent ? undefined : null,
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'bus', android: 'directions_bus', web: 'directions_bus' }} tintColor={color} size={28} />
            ),
          }}
        />

        <Tabs.Screen
          name="announcements"
          options={{
            title: 'News',
            href: isParent ? undefined : null,
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'megaphone', android: 'campaign', web: 'campaign' }} tintColor={color} size={28} />
            ),
          }}
        />

        {/* My Classes, Tasks, Gradebook, and Timetable are no longer tabs —
            they live at the root stack (app/teacher.tsx, tasks.tsx,
            gradebook.tsx, timetable.tsx) and are reachable via the
            Dashboard's Quick Actions card grid (see index.tsx). That keeps
            this tab bar at 3 items instead of squeezing in 7, and gives
            those screens a proper back button via the root Stack navigator. */}

        <Tabs.Screen
          name="messages"
          options={{
            title: 'Messages',
            href: (isTeacher || isParent) ? undefined : null,
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'message', android: 'chat', web: 'chat' }} tintColor={color} size={28} />
            ),
          }}
        />

        <Tabs.Screen
          name="profile"
          options={{
            title: 'Profile',
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'person', android: 'person', web: 'person' }} tintColor={color} size={28} />
            ),
          }}
        />

      </Tabs>
    </DataContext.Provider>
  );
}