import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, ScrollView, ActivityIndicator, TouchableOpacity, TextInput, Alert } from 'react-native';
import { SymbolView } from 'expo-symbols';
import { useAuth } from '@/context/AuthContext';
import {
  getTeacherClasses,
  getAssessmentsForClass,
  createAssessment,
  getAssessmentDetail,
  submitAssessmentScores,
} from '@/services/api';

export default function GradebookScreen() {
  const { userToken } = useAuth();
  const [classes, setClasses] = useState<any[]>([]);
  const [selectedClass, setSelectedClass] = useState<any | null>(null);
  const [assessments, setAssessments] = useState<any[]>([]);
  const [selectedAssessment, setSelectedAssessment] = useState<any | null>(null);
  const [detail, setDetail] = useState<any | null>(null);
  const [scores, setScores] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [newTitle, setNewTitle] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const list = await getTeacherClasses();
        setClasses(list);
        if (list.length > 0) setSelectedClass(list[0]);
      } catch (e) {
        console.error('Failed to fetch classes:', e);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const loadAssessments = useCallback(async (classSectionId: string) => {
    try {
      const list = await getAssessmentsForClass(classSectionId);
      setAssessments(list);
      setSelectedAssessment(null);
      setDetail(null);
      if (list.length > 0) openAssessment((list[0] as any).id);
    } catch (e) {
      console.error('Failed to fetch assessments:', e);
    }
  }, []);

  useEffect(() => {
    if (selectedClass) loadAssessments(selectedClass.id);
  }, [selectedClass, loadAssessments]);

  const openAssessment = async (assessmentId: string) => {
    setSelectedAssessment(assessmentId);
    try {
      const d: any = await getAssessmentDetail(assessmentId);
      setDetail(d);
      const prefill: Record<string, string> = {};
      (d.roster ?? []).forEach((r: any) => { prefill[r.studentId] = r.score != null ? String(r.score) : ''; });
      setScores(prefill);
    } catch (e) {
      console.error('Failed to fetch assessment detail:', e);
    }
  };

  const handleCreateAssessment = async () => {
    if (!selectedClass || !newTitle.trim()) return;
    try {
      const created: any = await createAssessment({
        title: newTitle.trim(),
        subjectCode: selectedClass.subject,
        classSectionId: selectedClass.id,
        term: 'TERM1',
        maxScore: 100,
        assessmentDate: new Date().toISOString().split('T')[0],
      });
      setNewTitle('');
      await loadAssessments(selectedClass.id);
      openAssessment(created.id);
    } catch (e) {
      Alert.alert('Error', 'Could not create assessment.');
    }
  };

  const handleSaveScores = async () => {
    if (!selectedAssessment) return;
    setSaving(true);
    try {
      const payload = Object.entries(scores)
        .filter(([, v]) => v !== '')
        .map(([studentId, v]) => ({ studentId, score: parseInt(v, 10) }));
      await submitAssessmentScores(selectedAssessment, payload);
      Alert.alert('Saved', 'Scores saved successfully.');
    } catch (e) {
      Alert.alert('Error', 'Could not save scores.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#6366f1" />
      </View>
    );
  }

  return (
    <View style={styles.root}>
      <View style={styles.headerBand}>
        <View style={styles.headerIconWrap}>
          <SymbolView name={{ ios: 'chart.bar.doc.horizontal', android: 'grading', web: 'grading' }} tintColor="#6366f1" size={26} />
        </View>
        <View style={{ flex: 1, marginLeft: 14 }}>
          <Text style={styles.headerTitle}>Gradebook</Text>
          <Text style={styles.headerSubtitle}>Create assessments and enter scores</Text>
        </View>
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.classChips} contentContainerStyle={{ paddingHorizontal: 16, gap: 8 }}>
        {classes.map((cls) => (
          <TouchableOpacity
            key={cls.id}
            style={[styles.chip, selectedClass?.id === cls.id && styles.chipActive]}
            onPress={() => setSelectedClass(cls)}
          >
            <Text style={[styles.chipText, selectedClass?.id === cls.id && styles.chipTextActive]}>{cls.className}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      <View style={styles.newAssessmentRow}>
        <TextInput
          style={styles.input}
          placeholder="New assessment title"
          placeholderTextColor="#64748b"
          value={newTitle}
          onChangeText={setNewTitle}
        />
        <TouchableOpacity style={styles.createBtn} onPress={handleCreateAssessment}>
          <Text style={styles.createBtnText}>Create</Text>
        </TouchableOpacity>
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.assessmentChips} contentContainerStyle={{ paddingHorizontal: 16, gap: 8 }}>
        {assessments.map((a) => (
          <TouchableOpacity
            key={a.id}
            style={[styles.chip, selectedAssessment === a.id && styles.chipActive]}
            onPress={() => openAssessment(a.id)}
          >
            <Text style={[styles.chipText, selectedAssessment === a.id && styles.chipTextActive]}>{a.title}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      {detail && (
        <>
          <View style={styles.sectionLabelRow}>
            <Text style={styles.sectionLabel}>{detail.title.toUpperCase()} — MAX {detail.maxScore}</Text>
          </View>
          <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent}>
            {(detail.roster ?? []).map((r: any) => (
              <View key={r.studentId} style={styles.rosterRow}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.rosterName}>{r.studentName}</Text>
                  <Text style={styles.rosterRoll}>{r.rollNumber}</Text>
                </View>
                <TextInput
                  style={styles.scoreInput}
                  keyboardType="numeric"
                  value={scores[r.studentId] ?? ''}
                  onChangeText={(v) => setScores((prev) => ({ ...prev, [r.studentId]: v }))}
                />
              </View>
            ))}
          </ScrollView>
          <TouchableOpacity style={styles.saveBtn} onPress={handleSaveScores} disabled={saving}>
            <Text style={styles.saveBtnText}>{saving ? 'Saving...' : 'Save All Scores'}</Text>
          </TouchableOpacity>
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0f172a' },
  center: { flex: 1, backgroundColor: '#0f172a', justifyContent: 'center', alignItems: 'center' },
  headerBand: {
    flexDirection: 'row', alignItems: 'center',
    backgroundColor: '#1e293b', paddingHorizontal: 20, paddingVertical: 18,
    borderBottomWidth: 1, borderBottomColor: '#334155',
  },
  headerIconWrap: { width: 48, height: 48, borderRadius: 14, backgroundColor: '#6366f120', justifyContent: 'center', alignItems: 'center' },
  headerTitle: { fontSize: 17, fontWeight: '700', color: '#f1f5f9' },
  headerSubtitle: { fontSize: 12, color: '#64748b', marginTop: 2 },
  classChips: { marginTop: 14, maxHeight: 44 },
  assessmentChips: { marginTop: 10, maxHeight: 44 },
  chip: { backgroundColor: '#1e293b', borderRadius: 20, paddingHorizontal: 14, paddingVertical: 8, borderWidth: 1, borderColor: '#334155' },
  chipActive: { backgroundColor: '#6366f122', borderColor: '#6366f1' },
  chipText: { fontSize: 13, color: '#94a3b8', fontWeight: '600' },
  chipTextActive: { color: '#6366f1' },
  newAssessmentRow: { flexDirection: 'row', gap: 8, paddingHorizontal: 16, marginTop: 14 },
  input: { flex: 1, backgroundColor: '#1e293b', borderRadius: 10, borderWidth: 1, borderColor: '#334155', paddingHorizontal: 12, color: '#f1f5f9' },
  createBtn: { backgroundColor: '#6366f1', borderRadius: 10, paddingHorizontal: 16, justifyContent: 'center' },
  createBtnText: { color: '#fff', fontWeight: '700', fontSize: 13 },
  sectionLabelRow: { paddingHorizontal: 20, paddingTop: 18, paddingBottom: 8 },
  sectionLabel: { fontSize: 11, fontWeight: '700', color: '#64748b', letterSpacing: 1 },
  scroll: { flex: 1 },
  scrollContent: { paddingHorizontal: 16, paddingBottom: 16, gap: 8 },
  rosterRow: {
    flexDirection: 'row', alignItems: 'center', backgroundColor: '#1e293b',
    borderRadius: 12, padding: 12, borderWidth: 1, borderColor: '#334155',
  },
  rosterName: { fontSize: 14, fontWeight: '600', color: '#f1f5f9' },
  rosterRoll: { fontSize: 11, color: '#64748b', marginTop: 2 },
  scoreInput: {
    width: 60, backgroundColor: '#0f172a', borderRadius: 8, borderWidth: 1, borderColor: '#334155',
    color: '#f1f5f9', textAlign: 'center', paddingVertical: 6,
  },
  saveBtn: { backgroundColor: '#6366f1', margin: 16, borderRadius: 12, paddingVertical: 14, alignItems: 'center' },
  saveBtnText: { color: '#fff', fontWeight: '700', fontSize: 14 },
});
