import axios from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';
import * as FileSystem from 'expo-file-system';

const BASE_HOST = (typeof window !== 'undefined') ? 'http://localhost:8080' : (Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080');
const API_URL = `${BASE_HOST}/api/mobile`;

const api = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(
  async (config) => {
    const token = await AsyncStorage.getItem('userToken');
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export const login = async (email, password) => {
  const response = await api.post('/auth/login', { email, password });
  return response;
};

export const getStudentDashboard = async () => {
  const response = await api.get('/student/dashboard');
  return response.data;
};

export const getParentDashboard = async () => {
  const response = await api.get('/parent/dashboard');
  return response.data;
};

export const logout = async () => {
  await AsyncStorage.removeItem('userToken');
  await AsyncStorage.removeItem('userRole');
};

export const getCurriculumTopics = async (subjectEnum, standard = 6) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/curriculum`, {
    params: { syllabus: 'CBSE', standard, subject: subjectEnum },
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const getParentAttendance = async (studentId) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/mobile/parent/attendance`, {
    params: { studentId },
    headers: { Authorization: `Bearer ${token}` },
  });
  return Array.isArray(response.data) ? response.data : (response.data.value ?? []);
};

export const getSubjectPerformance = async (studentId) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/mobile/parent/subject-performance`, {
    params: { studentId },
    headers: { Authorization: `Bearer ${token}` },
  });
  return Array.isArray(response.data) ? response.data : (response.data.value ?? []);
};

export const getSubjects = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/subjects`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return Array.isArray(response.data) ? response.data : (response.data.value ?? []);
};

export const downloadReportCard = async (term, studentId) => {
  const token = await AsyncStorage.getItem('userToken');
  const params = new URLSearchParams({ term });
  if (studentId) params.set('studentId', studentId);
  const url = `${BASE_HOST}/api/mobile/parent/report-card?${params.toString()}`;
  const fileUri = `${FileSystem.documentDirectory}report_card_${term}.pdf`;
  const result = await FileSystem.downloadAsync(url, fileUri, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (result.status !== 200) {
    throw new Error(`Report card download failed with status ${result.status}`);
  }
  return result.uri;
};

export const getClassRoster = async (sectionId) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/teacher/my-students`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const submitClassAttendance = async (attendanceRecord) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.post(
    `${BASE_HOST}/api/teacher/attendance/submit`,
    { attendance: attendanceRecord },
    { headers: { Authorization: `Bearer ${token}` } }
  );
  return response.data;
};

export const getUserProfile = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/mobile/user/profile`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const getStudentProgress = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/student/progress`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const getStudentAttendance = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/mobile/student/attendance`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const getStudentTasks = async () => {
  const response = await api.get('/student/tasks');
  return response.data;
};

export const getStudentSyllabus = async () => {
  const response = await api.get('/student/syllabus');
  return response.data;
};

export const getNotifications = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/notifications`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const getUnreadNotificationCount = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/notifications/unread-count`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data.count;
};

export const markNotificationRead = async (id) => {
  const token = await AsyncStorage.getItem('userToken');
  await axios.post(`${BASE_HOST}/api/notifications/${id}/read`, {}, {
    headers: { Authorization: `Bearer ${token}` },
  });
};

export const markAllNotificationsRead = async () => {
  const token = await AsyncStorage.getItem('userToken');
  await axios.post(`${BASE_HOST}/api/notifications/read-all`, {}, {
    headers: { Authorization: `Bearer ${token}` },
  });
};

// ─── Gradebook (teacher) ────────────────────────────────────────────────────

export const getTeacherClasses = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/teacher/classes`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return Array.isArray(response.data) ? response.data : (response.data.value ?? []);
};

export const getAssessmentsForClass = async (classSectionId) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/teacher/assessments/class/${classSectionId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return Array.isArray(response.data) ? response.data : [];
};

export const createAssessment = async (payload) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.post(`${BASE_HOST}/api/teacher/assessments/create`, payload, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const getAssessmentDetail = async (assessmentId) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/teacher/assessments/${assessmentId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const submitAssessmentScores = async (assessmentId, scores) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.post(`${BASE_HOST}/api/teacher/assessments/${assessmentId}/scores`, { scores }, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

// ─── Timetable (teacher) ────────────────────────────────────────────────────

export const getTimetableToday = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/teacher/timetable/today`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return Array.isArray(response.data) ? response.data : [];
};

export const getTimetableWeek = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/teacher/timetable/week`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data ?? {};
};

// ─── Messaging (teacher + parent) ───────────────────────────────────────────

export const getConversations = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/messages/conversations`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return Array.isArray(response.data) ? response.data : [];
};

export const startConversation = async (payload) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.post(`${BASE_HOST}/api/messages/conversations/start`, payload, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const getConversationThread = async (conversationId) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/messages/conversations/${conversationId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return Array.isArray(response.data) ? response.data : [];
};

export const sendConversationReply = async (conversationId, body) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.post(`${BASE_HOST}/api/messages/conversations/${conversationId}/messages`, { body }, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const getTeacherMessageRoster = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/teacher/messages/roster`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return Array.isArray(response.data) ? response.data : [];
};

export const getParentMessageTeachers = async (studentId) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/parent/messages/teachers`, {
    params: { studentId },
    headers: { Authorization: `Bearer ${token}` },
  });
  return Array.isArray(response.data) ? response.data : [];
};

export default api;