import axios from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';
import * as FileSystem from 'expo-file-system';

export function getApiHost() {
  const envHost = process.env.EXPO_PUBLIC_API_HOST;
  if (envHost) return envHost;
  if (typeof window !== 'undefined') return 'http://localhost:8080';
  return Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080';
}

const BASE_HOST = getApiHost();
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

export const getParentDashboard = async (studentId) => {
  const response = await api.get('/parent/dashboard', { params: studentId ? { studentId } : {} });
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
  const response = await axios.get(`${BASE_HOST}/api/teacher/attendance/roster/${sectionId}`, {
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

// Claiming a parent-set quest. The endpoint existed; nothing in the app called
// it, so a child could see "available to claim" and had no way to claim it.
export const claimQuest = async (questId) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.post(`${BASE_HOST}/api/student/claim-quest/${questId}`, null, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const getStudentTimetable = async () => {
  const response = await api.get('/student/timetable');
  return Array.isArray(response.data) ? response.data : [];
};

export const getStudentAnnouncements = async () => {
  const response = await api.get('/student/announcements');
  return Array.isArray(response.data) ? response.data : [];
};

export const getStudentPerformance = async () => {
  const response = await api.get('/student/performance');
  return Array.isArray(response.data) ? response.data : [];
};

// Spending XP. Two kinds: the school's reward catalogue, and rewards a parent
// set aside for this child specifically.
export const redeemSchoolReward = async (rewardId) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.post(`${BASE_HOST}/api/student/rewards/${rewardId}/redeem`, null, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const redeemParentReward = async (rewardId) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.post(`${BASE_HOST}/api/student/parent-rewards/${rewardId}/redeem`, null, {
    headers: { Authorization: `Bearer ${token}` },
  });
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

// ─── Verification queue (teacher) ──────────────────────────────────────────

export const getTeacherQueue = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/teacher/queue`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const decideMilestone = async (submissionId, action, reason) => {
  const token = await AsyncStorage.getItem('userToken');
  const params = action === 'approve' ? { submissionId } : { submissionId, reason };
  const response = await axios.post(`${BASE_HOST}/api/teacher/milestone/${action}`, null, {
    headers: { Authorization: `Bearer ${token}` },
    params,
  });
  return response.data;
};

export const decideProgress = async (studentProgressId, action, reason) => {
  const token = await AsyncStorage.getItem('userToken');
  const params = action === 'approve' ? { studentProgressId } : { studentProgressId, reason };
  const response = await axios.post(`${BASE_HOST}/api/teacher/progress/${action}`, null, {
    headers: { Authorization: `Bearer ${token}` },
    params,
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

// ─── Bus tracking ───────────────────────────────────────────────────────────

export const getMyBusRoute = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/mobile/driver/route/my-route`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const pingDriverLocation = async (latitude, longitude) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.post(`${BASE_HOST}/api/mobile/driver/location/ping`, { latitude, longitude }, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const getParentBusLocation = async (studentId) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/mobile/parent/bus-location`, {
    params: studentId ? { studentId } : {},
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

// ─── Language: translate + text-to-speech / speech-to-text ─────────────────

export const getSupportedLanguages = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/mobile/languages`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return Array.isArray(response.data) ? response.data : [];
};

export const setPreferredLanguage = async (language) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.put(`${BASE_HOST}/api/mobile/parent/language`, { language }, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const getParentAnnouncements = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/mobile/parent/announcements`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return Array.isArray(response.data) ? response.data : [];
};

export const getAnnouncementLocalized = async (id, lang) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/mobile/parent/announcements/${id}/localized`, {
    params: { lang },
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const getAnnouncementSpeech = async (id, lang) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/mobile/parent/announcements/${id}/speech`, {
    params: { lang },
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data; // { audioBase64, contentType }
};

export const getMessageLocalized = async (conversationId, messageId, lang) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(
    `${BASE_HOST}/api/messages/conversations/${conversationId}/messages/${messageId}/localized`,
    { params: { lang }, headers: { Authorization: `Bearer ${token}` } }
  );
  return response.data;
};

export const getMessageSpeech = async (conversationId, messageId, lang) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(
    `${BASE_HOST}/api/messages/conversations/${conversationId}/messages/${messageId}/speech`,
    { params: { lang }, headers: { Authorization: `Bearer ${token}` } }
  );
  return response.data; // { audioBase64, contentType }
};

export const sendVoiceReply = async (conversationId, audioUri, lang) => {
  const token = await AsyncStorage.getItem('userToken');
  const formData = new FormData();
  formData.append('audio', {
    uri: audioUri,
    name: 'voice.wav',
    type: 'audio/wav',
  });
  const response = await axios.post(
    `${BASE_HOST}/api/messages/conversations/${conversationId}/voice-reply?lang=${lang}`,
    formData,
    { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'multipart/form-data' } }
  );
  return response.data;
};

/*
 * A child's profile for a teacher on a phone: age, allergies, emergency
 * contact and who is allowed to collect them. The information exists on the
 * web console, but the moment it is wanted is at the gate.
 */
export const getStudentProfile = async (studentId) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/mobile/teacher/student/${studentId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

/** What a teacher may recognise a child for. */
export const getBadges = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/mobile/teacher/badges`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

/** Recognises a child. Same service and audit trail as the web. */
export const awardBadge = async (studentId, badgeCode, reason) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.post(
    `${BASE_HOST}/api/mobile/teacher/student/${studentId}/award`,
    { badgeCode, reason },
    { headers: { Authorization: `Bearer ${token}` } }
  );
  return response.data;
};

/* ── Principal approvals ──────────────────────────────────────────────────
 * These are the same endpoints the web console uses; they already accept the
 * app's bearer token, so the app needed a screen rather than an API.
 */
export const getPendingApprovals = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/principal/approvals/pending`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const decideApproval = async (requestId, action) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.post(
    `${BASE_HOST}/api/principal/approvals/${requestId}/${action}`,
    {},
    { headers: { Authorization: `Bearer ${token}` } }
  );
  return response.data;
};

export const getPendingStaff = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/principal/staff/pending`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const decideStaff = async (userId, action) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.post(
    `${BASE_HOST}/api/principal/staff/${userId}/${action}`,
    {},
    { headers: { Authorization: `Bearer ${token}` } }
  );
  return response.data;
};

/**
 * Asks the school to reduce one instalment. This is a request: a principal
 * decides, and nothing owed changes until they do.
 */
export const requestFeeWaiver = async (invoiceId, amount, reason) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.post(
    `${BASE_HOST}/api/mobile/parent/fees/${invoiceId}/waiver-request`,
    { amount, reason },
    { headers: { Authorization: `Bearer ${token}` } }
  );
  return response.data;
};

/* Waivers live in their own queue, separate from approval requests. A
 * principal's screen has to read both or a parent's request goes unseen. */
export const getPendingWaivers = async () => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.get(`${BASE_HOST}/api/principal/fees/waivers/pending`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data;
};

export const decideWaiver = async (invoiceId, action) => {
  const token = await AsyncStorage.getItem('userToken');
  const response = await axios.post(
    `${BASE_HOST}/api/principal/fees/${invoiceId}/waiver/${action}`,
    {},
    { headers: { Authorization: `Bearer ${token}` } }
  );
  return response.data;
};

export default api;