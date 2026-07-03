import { SymbolView } from 'expo-symbols';
import { Tabs } from 'expo-router';
import { View, ActivityIndicator, Platform } from 'react-native';
import { useCallback, useState, useEffect, createContext } from 'react';
import { getStudentDashboard, getParentDashboard } from '../../services/api';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { useClientOnlyValue } from '@/components/useClientOnlyValue';
import { useAuth } from '@/context/AuthContext';

export const DataContext = createContext<any>({ role: null, data: {}, refreshData: async () => {} });

const ROLE_STUDENT = 'STUDENT';
const ROLE_PARENT  = 'PARENT';
const ROLE_TEACHER = 'TEACHER';

export default function TabLayout() {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const { userRole: role } = useAuth();
  const headerShown = useClientOnlyValue(false, true);

  const fetchDashboardData = useCallback(async () => {
    setLoading(true);
    try {
      if (role === ROLE_PARENT) {
        const parentData = await getParentDashboard();
        setData(parentData);
      } else if (role === ROLE_TEACHER) {
        const token = await AsyncStorage.getItem('userToken');
        const BASE_HOST = (typeof window !== 'undefined') ? 'http://localhost:8080' : (Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080');
        const [classesResp, tasksResp, attendanceSummaryResp, timetableResp] = await Promise.all([
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
          })
        ]);
        const classes = await classesResp.json();
        const tasksRaw = await tasksResp.json();
        const tasks = Array.isArray(tasksRaw) ? tasksRaw : (tasksRaw.value ?? []);
        const attendanceSummary = await attendanceSummaryResp.json();
        const timetableRaw = await timetableResp.json();
        const timetable = Array.isArray(timetableRaw) ? timetableRaw : (timetableRaw.value ?? []);
        setData({ teacher: true, classes, tasks, attendanceSummary, timetable });
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
  }, [role]);

  useEffect(() => {
    void fetchDashboardData();
  }, [fetchDashboardData]);

  const isStudent = role === ROLE_STUDENT || (!role && true);
  const isParent  = role === ROLE_PARENT;
  const isTeacher = role === ROLE_TEACHER;

  if (loading) {
    return (
      <DataContext.Provider value={{ role, data: {}, refreshData: fetchDashboardData }}>
        <View style={{ flex: 1, backgroundColor: '#0f172a', justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color="#6366f1" />
        </View>
      </DataContext.Provider>
    );
  }

  if (!data) {
    return (
      <DataContext.Provider value={{ role, data: {}, refreshData: fetchDashboardData }}>
        <View style={{ flex: 1, backgroundColor: '#0f172a' }} />
      </DataContext.Provider>
    );
  }

  return (
    <DataContext.Provider value={{ role, data, refreshData: fetchDashboardData }}>
      <Tabs
        screenOptions={{
          tabBarActiveTintColor:   '#6366f1',
          tabBarInactiveTintColor: '#94a3b8',
          tabBarStyle: { backgroundColor: '#0f172a', borderTopColor: '#1e293b' },
          headerStyle: { backgroundColor: '#0f172a' },
          headerTintColor: '#fff',
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

        <Tabs.Screen
          name="teacher"
          options={{
            title: 'My Classes',
            href: isTeacher ? undefined : null,
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'person.badge.clock', android: 'school', web: 'school' }} tintColor={color} size={28} />
            ),
          }}
        />

        <Tabs.Screen
          name="tasks"
          options={{
            title: 'Tasks',
            href: isTeacher ? undefined : null,
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'checklist', android: 'task_alt', web: 'task_alt' }} tintColor={color} size={28} />
            ),
          }}
        />

        <Tabs.Screen
          name="gradebook"
          options={{
            title: 'Gradebook',
            href: isTeacher ? undefined : null,
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'chart.bar.doc.horizontal', android: 'grading', web: 'grading' }} tintColor={color} size={28} />
            ),
          }}
        />

        <Tabs.Screen
          name="timetable"
          options={{
            title: 'Timetable',
            href: isTeacher ? undefined : null,
            tabBarIcon: ({ color }) => (
              <SymbolView name={{ ios: 'calendar', android: 'event', web: 'event' }} tintColor={color} size={28} />
            ),
          }}
        />

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