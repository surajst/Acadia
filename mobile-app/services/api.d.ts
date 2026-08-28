import type { AxiosInstance, AxiosResponse } from 'axios';

/**
 * Types for services/api.js.
 *
 * This file had drifted badly from the module it describes: it declared
 * getClass6MathRoster, which was renamed to getClassRoster long ago, and was
 * missing roughly twenty functions that do exist. Every screen importing one of
 * those got "has no exported member", which is why `tsc` reported dozens of
 * errors across the app and nobody could tell a real type error from the noise.
 *
 * Keep it in step with api.js when adding an endpoint.
 */

export type UserRole = 'STUDENT' | 'PARENT' | 'TEACHER' | 'DRIVER' | 'ADMIN' | 'PRINCIPAL';

export interface LoginResponse {
  token: string;
  role: UserRole;
  firstName?: string;
  lastName?: string;
  email?: string;
  schoolName?: string;
  academicYearName?: string;
}

export interface CurriculumTopic {
  id?: string;
  topicName: string;
  subjectType: string;
  standard: number;
  topicOrder: number;
  xpReward: number;
}

export interface UserProfile {
  firstName: string;
  lastName: string;
  email: string;
  role: UserRole;
}

export interface RosterStudent {
  id: string;
  name: string;
  rollNumber: string;
  className?: string;
}

export interface AttendanceRecord {
  studentId: string;
  status: 'PRESENT' | 'ABSENT';
}

export interface SubjectPerformance {
  subjectCode: string;
  averagePercentage: number;
  trend: number[];
}

export interface Subject {
  id: string;
  code: string;
  displayName: string;
  colorHex?: string | null;
  active: boolean;
  sortOrder: number;
}

/** What a teacher may recognise a child for. */
export interface Badge {
  code: string;
  label: string;
  emoji: string;
  points: number;
  suggestion: string;
}

/** One thing a child was recognised for. */
export interface Award {
  id: string;
  studentId: string;
  badgeCode: string;
  label: string;
  emoji: string;
  points: number;
  reason: string;
  awardedByName: string | null;
  createdAt: string;
}

/** Someone other than a guardian who may collect a child. */
export interface PickupContact {
  id: string;
  name: string;
  relationship?: string | null;
  phone?: string | null;
}

/** A child, as a teacher sees them on a phone. */
export interface StudentProfile {
  studentId: string;
  firstName: string;
  lastName: string;
  rollNumber?: string | null;
  gradeName?: string | null;
  sectionName?: string | null;
  ageYears?: number | null;
  dateOfBirth?: string | null;
  medicalNotes?: string | null;
  emergencyContactName?: string | null;
  emergencyContactPhone?: string | null;
  pickupContacts: PickupContact[];
  primaryGuardian?: string | null;
  guardianPhone?: string | null;
  attendancePercentage: number;
  presentCount: number;
  absentCount: number;
  schoolXp: number;
  awards: Award[];
}

export type ApiObject = Record<string, any>;

export function getApiHost(): string;

export function login(email: string, password: string): Promise<AxiosResponse<LoginResponse>>;
export function logout(): Promise<void>;

export function getStudentDashboard(): Promise<ApiObject>;
export function getParentDashboard(studentId?: string): Promise<ApiObject>;
export function getUserProfile(): Promise<UserProfile>;

export function getCurriculumTopics(subjectEnum: string, standard?: number): Promise<CurriculumTopic[]>;
export function getParentAttendance(studentId?: string): Promise<ApiObject>;
export function getSubjectPerformance(studentId?: string): Promise<SubjectPerformance[]>;
export function getSubjects(): Promise<Subject[]>;
export function downloadReportCard(term: string, studentId?: string): Promise<string>;

export function getClassRoster(sectionId?: string): Promise<RosterStudent[]>;
export function submitClassAttendance(attendanceRecord: AttendanceRecord[]): Promise<ApiObject>;

export function getStudentProgress(): Promise<ApiObject>;
export function getStudentAttendance(): Promise<ApiObject>;
export function getStudentTasks(): Promise<any[]>;
export function getStudentSyllabus(): Promise<ApiObject>;

export function getNotifications(): Promise<any[]>;
export function getUnreadNotificationCount(): Promise<number>;
export function markNotificationRead(id: string): Promise<ApiObject>;
export function markAllNotificationsRead(): Promise<ApiObject>;

export function getTeacherClasses(): Promise<ApiObject[]>;
export function getAssessmentsForClass(classSectionId: string): Promise<ApiObject[]>;
export function createAssessment(payload: ApiObject): Promise<ApiObject>;
export function getAssessmentDetail(assessmentId: string): Promise<ApiObject>;
export function submitAssessmentScores(assessmentId: string, scores: ApiObject[]): Promise<ApiObject>;

export function getTimetableToday(): Promise<ApiObject[]>;
export function getTimetableWeek(): Promise<Record<string, ApiObject[]>>;

export function getConversations(): Promise<ApiObject[]>;
export function startConversation(payload: ApiObject): Promise<ApiObject>;
export function getConversationThread(conversationId: string): Promise<ApiObject[]>;
export function sendConversationReply(conversationId: string, body: string): Promise<ApiObject>;
export function getTeacherMessageRoster(): Promise<ApiObject[]>;
export function getParentMessageTeachers(studentId?: string): Promise<ApiObject[]>;

export function getMyBusRoute(): Promise<ApiObject>;
export function pingDriverLocation(latitude: number, longitude: number): Promise<ApiObject>;
export function getParentBusLocation(studentId?: string): Promise<ApiObject>;

export function getSupportedLanguages(): Promise<ApiObject[]>;
export function setPreferredLanguage(language: string): Promise<ApiObject>;
export function getParentAnnouncements(): Promise<ApiObject[]>;
export function getAnnouncementLocalized(id: string, lang: string): Promise<{ title: string; content: string }>;
export function getAnnouncementSpeech(id: string, lang: string): Promise<{ audioBase64: string; contentType: string }>;
export function getMessageLocalized(conversationId: string, messageId: string, lang: string): Promise<ApiObject>;
export function getMessageSpeech(conversationId: string, messageId: string, lang: string): Promise<{ audioBase64: string; contentType: string }>;
export function sendVoiceReply(conversationId: string, audioUri: string, lang: string): Promise<ApiObject>;

export function getStudentProfile(studentId: string): Promise<StudentProfile>;
export function getBadges(): Promise<Badge[]>;
export function awardBadge(studentId: string, badgeCode: string, reason?: string): Promise<Award>;

export function getPendingApprovals(): Promise<ApiObject[]>;
export function decideApproval(requestId: string, action: 'approve' | 'reject'): Promise<ApiObject>;
export function getPendingStaff(): Promise<ApiObject[]>;
export function decideStaff(userId: string, action: 'approve' | 'reject'): Promise<ApiObject>;

declare const api: AxiosInstance;

export default api;
