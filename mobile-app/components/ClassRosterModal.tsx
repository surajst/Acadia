import React, { useState, useEffect } from 'react';
import {
  StyleSheet,
  View,
  Text,
  Modal,
  FlatList,
  ActivityIndicator,
  TouchableOpacity,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { SymbolView } from 'expo-symbols';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useAuth } from '../context/AuthContext';
import {
  getClassRoster,
  submitClassAttendance,
  getApiHost,
} from '../services/api';

interface RosterStudent {
  id: string;
  name: string;
  className?: string;
}

interface ClassRosterModalProps {
  isVisible: boolean;
  onClose: () => void;
  sectionId?: string;
  className?: string;
}

type AttendanceStatus = 'PRESENT' | 'ABSENT';

export default function ClassRosterModal({ isVisible, onClose, sectionId, className }: ClassRosterModalProps) {
  const { schoolName } = useAuth();
  const [students, setStudents] = useState<RosterStudent[]>([]);
  const [attendanceMap, setAttendanceMap] = useState<Record<string, AttendanceStatus>>({});
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const BASE_HOST = getApiHost();

  useEffect(() => {
    if (isVisible) {
      fetchRoster();
    }
  }, [isVisible]);

  const fetchRoster = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const token = await AsyncStorage.getItem('userToken');

      const [rosterData, attendanceResp] = await Promise.all([
        getClassRoster(sectionId),
        fetch(`${BASE_HOST}/api/teacher/attendance/today/${sectionId}`, {
          headers: { Authorization: `Bearer ${token}` }
        })
      ]);

      setStudents(rosterData);

      const initialMap: Record<string, AttendanceStatus> = {};
      rosterData.forEach((s: RosterStudent) => {
        initialMap[s.id] = 'PRESENT';
      });

      if (attendanceResp.ok) {
        const attendanceData = await attendanceResp.json();
        const records = Array.isArray(attendanceData) ? attendanceData : (attendanceData.value ?? []);
        records.forEach((record: any) => {
          if (record.studentId && record.status) {
            initialMap[record.studentId] = record.status as AttendanceStatus;
          }
        });
      }

      setAttendanceMap(initialMap);
    } catch (err) {
      console.error('Failed to fetch roster:', err);
      setError('Unable to load roster. Please check your connection.');
    } finally {
      setIsLoading(false);
    }
  };

  const toggleAttendance = (studentId: string, status: AttendanceStatus) => {
    setAttendanceMap(prev => ({
      ...prev,
      [studentId]: status
    }));
  };

  const handleSubmit = async () => {
    setIsSubmitting(true);
    try {
      const payload = Object.entries(attendanceMap).map(([studentId, status]) => ({
        studentId,
        status,
        remarks: ''
      }));
      await submitClassAttendance(payload);
      setSubmitted(true);
      setTimeout(() => {
        setSubmitted(false);
        onClose();
      }, 2000);
    } catch (err) {
      console.error('Attendance submission failed:', err);
    } finally {
      setIsSubmitting(false);
    }
  };

  const renderStudentItem = ({ item }: { item: RosterStudent }) => {
    const status = attendanceMap[item.id] || 'PRESENT';
    return (
      <View style={styles.studentRow}>
        <View style={styles.avatar}>
          <Text style={styles.avatarText}>{item.name.charAt(0)}</Text>
        </View>
        <View style={styles.studentInfo}>
          <Text style={styles.studentName}>{item.name}</Text>
          {item.className && (
            <Text style={styles.rollNumber}>{item.className}</Text>
          )}
        </View>
        <View style={styles.toggleGroup}>
          <TouchableOpacity
            style={[styles.toggleBtn, status === 'PRESENT' && styles.presentActive]}
            onPress={() => toggleAttendance(item.id, 'PRESENT')}
          >
            <Text style={[styles.toggleText, status === 'PRESENT' && styles.textActive]}>P</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.toggleBtn, status === 'ABSENT' && styles.absentActive]}
            onPress={() => toggleAttendance(item.id, 'ABSENT')}
          >
            <Text style={[styles.toggleText, status === 'ABSENT' && styles.textActive]}>A</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  };

  return (
    <Modal
      animationType="slide"
      transparent={true}
      visible={isVisible}
      onRequestClose={onClose}
    >
      <View style={styles.modalOverlay}>
        <SafeAreaView style={[styles.modalContainer, { flex: 1 }]} edges={['bottom']}>
          <View style={styles.header}>
            <View>
              <Text style={styles.headerTitle}>{className || 'Class Roster'}</Text>
              <Text style={styles.headerSubtitle}>{schoolName || 'Your School'} · Daily Attendance</Text>
            </View>
            <TouchableOpacity onPress={onClose} style={styles.closeBtn} disabled={isSubmitting}>
              <SymbolView
                name={{ ios: 'xmark.circle.fill', android: 'cancel', web: 'cancel' }}
                tintColor="#94a3b8"
                size={24}
              />
            </TouchableOpacity>
          </View>

          <View style={styles.content}>
            {isLoading ? (
              <View style={styles.centerBox}>
                <ActivityIndicator size="large" color="#6366f1" />
                <Text style={styles.loadingText}>Loading Roster...</Text>
              </View>
            ) : error ? (
              <View style={styles.centerBox}>
                <Text style={styles.errorText}>{error}</Text>
                <TouchableOpacity style={styles.retryBtn} onPress={fetchRoster}>
                  <Text style={styles.retryText}>Retry</Text>
                </TouchableOpacity>
              </View>
            ) : (
              <FlatList
                data={students}
                keyExtractor={(item) => item.id}
                renderItem={renderStudentItem}
                contentContainerStyle={styles.listPadding}
                ItemSeparatorComponent={() => <View style={styles.separator} />}
                showsVerticalScrollIndicator={false}
                bounces={true}
              />
            )}
          </View>

          {!isLoading && !error && students.length > 0 && (
            <View style={styles.footer}>
              <TouchableOpacity
                style={[styles.submitBtn, isSubmitting && styles.submitDisabled]}
                onPress={handleSubmit}
                disabled={isSubmitting}
              >
                {isSubmitting ? (
                  <ActivityIndicator color="#fff" />
                ) : (
                  <Text style={styles.submitBtnText}>Submit Attendance Sheet</Text>
                )}
              </TouchableOpacity>
            </View>
          )}

          {submitted && (
            <View style={styles.successOverlay}>
              <View style={styles.successCard}>
                <View style={styles.successIcon}>
                  <Text style={styles.successCheckmark}>✓</Text>
                </View>
                <Text style={styles.successTitle}>Attendance Submitted</Text>
                <Text style={styles.successSubtitle}>Records saved successfully</Text>
              </View>
            </View>
          )}
        </SafeAreaView>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    justifyContent: 'flex-end',
  },
  modalContainer: {
    backgroundColor: '#0f172a',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    maxHeight: '90%',
    overflow: 'hidden',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 20,
    borderBottomWidth: 1,
    borderBottomColor: '#1e293b',
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#f1f5f9',
  },
  headerSubtitle: {
    fontSize: 12,
    color: '#64748b',
    marginTop: 2,
  },
  closeBtn: { padding: 4 },
  content: {
    flex: 1,
    minHeight: 0,
  },
  centerBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 40,
  },
  loadingText: { color: '#94a3b8', marginTop: 12, fontSize: 14 },
  errorText: { color: '#ef4444', textAlign: 'center', fontSize: 14 },
  retryBtn: {
    marginTop: 16,
    backgroundColor: '#1e293b',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#334155',
  },
  retryText: { color: '#6366f1', fontWeight: '600' },
  listPadding: {
    paddingHorizontal: 16,
    paddingBottom: 20,
    flexGrow: 1,
  },
  studentRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
  },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: '#6366f115',
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#6366f130',
  },
  avatarText: { color: '#818cf8', fontWeight: '700', fontSize: 18 },
  studentInfo: { flex: 1, marginLeft: 14 },
  studentName: { fontSize: 15, fontWeight: '600', color: '#f1f5f9' },
  rollNumber: { fontSize: 12, color: '#64748b', marginTop: 2 },
  toggleGroup: {
    flexDirection: 'row',
    backgroundColor: '#1e293b',
    borderRadius: 8,
    padding: 4,
    gap: 4,
  },
  toggleBtn: {
    width: 36,
    height: 32,
    borderRadius: 6,
    justifyContent: 'center',
    alignItems: 'center',
  },
  toggleText: { fontSize: 13, fontWeight: '700', color: '#475569' },
  presentActive: { backgroundColor: '#059669' },
  absentActive: { backgroundColor: '#dc2626' },
  textActive: { color: '#ffffff' },
  separator: { height: 1, backgroundColor: '#1e293b' },
  footer: {
    padding: 20,
    borderTopWidth: 1,
    borderTopColor: '#1e293b',
    backgroundColor: '#0f172a',
  },
  submitBtn: {
    backgroundColor: '#6366f1',
    height: 52,
    borderRadius: 14,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 4,
  },
  submitDisabled: { opacity: 0.6 },
  submitBtnText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  successOverlay: {
    position: 'absolute',
    top: 0, left: 0, right: 0, bottom: 0,
    backgroundColor: 'rgba(15, 23, 42, 0.92)',
    justifyContent: 'center',
    alignItems: 'center',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
  },
  successCard: {
    alignItems: 'center',
    padding: 32,
  },
  successIcon: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: '#059669',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 20,
    shadowColor: '#059669',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.5,
    shadowRadius: 20,
    elevation: 10,
  },
  successCheckmark: {
    color: '#fff',
    fontSize: 36,
    fontWeight: '700',
  },
  successTitle: {
    color: '#f1f5f9',
    fontSize: 20,
    fontWeight: '700',
    marginBottom: 8,
  },
  successSubtitle: {
    color: '#64748b',
    fontSize: 14,
  },
});