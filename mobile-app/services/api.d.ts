import type { AxiosInstance, AxiosResponse } from 'axios';

export type UserRole = 'STUDENT' | 'PARENT' | 'TEACHER';

export interface LoginResponse {
  token: string;
  role: UserRole;
  firstName?: string;
  lastName?: string;
  email?: string;
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

export type ApiObject = Record<string, unknown>;

export function login(
  email: string,
  password: string,
): Promise<AxiosResponse<LoginResponse>>;

export function getStudentDashboard(): Promise<ApiObject>;
export function getParentDashboard(): Promise<ApiObject>;
export function logout(): Promise<void>;
export function getCurriculumTopics(
  subjectEnum: string,
  standard?: number,
): Promise<CurriculumTopic[]>;
export function getParentAttendance(studentId: string): Promise<ApiObject>;
export function getSubjectPerformance(studentId: string): Promise<SubjectPerformance[]>;
export function getSubjects(): Promise<Subject[]>;
export function downloadReportCard(term: string, studentId?: string): Promise<string>;
export function getClass6MathRoster(): Promise<RosterStudent[]>;
export function submitClassAttendance(
  attendanceRecord: AttendanceRecord[],
): Promise<ApiObject>;
export function getUserProfile(): Promise<UserProfile>;

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
export function getParentMessageTeachers(studentId: string): Promise<ApiObject[]>;

declare const api: AxiosInstance;

export default api;
