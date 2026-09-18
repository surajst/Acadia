import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  RefreshControl,
  ActivityIndicator,
  TouchableOpacity,
  TextInput,
  Modal,
  Alert,
} from 'react-native';
import { useContext, useState, useEffect, useMemo } from 'react';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { DataContext } from './_layout';
import { getStudentTasks, getTaskQuestions, submitTask } from '../../services/api';
import { useTheme, type Theme } from '../../context/ThemeContext';

type Challenge = {
  id: string;
  title: string;
  description: string;
  subjectType: string;
  taskType: 'HOMEWORK' | 'READING' | 'PROJECT';
  xpReward: number;
  dueDate: string;
  taskStatus: string;
};

export default function ChallengesScreen() {
  const T = useTheme();
  const styles = useMemo(() => makeStyles(T), [T]);
  const insets = useSafeAreaInsets();
  const { refreshData } = useContext(DataContext);
  const [refreshing, setRefreshing] = useState(false);
  const [challenges, setChallenges] = useState<Challenge[]>([]);
  const [loading, setLoading] = useState(true);

  // One status family per type, ink on its own tint. Built from the live theme
  // rather than frozen at module load, or the badges keep the default palette's
  // colours after the reader picks a different theme.
  const typeColor = useMemo<Record<Challenge['taskType'], string>>(() => ({
    HOMEWORK: T.infoInk, READING: T.brandInk, PROJECT: T.warnInk,
  }), [T]);
  const typeBg = useMemo<Record<Challenge['taskType'], string>>(() => ({
    HOMEWORK: T.info50, READING: T.brand50, PROJECT: T.warn50,
  }), [T]);

  const [openTask, setOpenTask] = useState<Challenge | null>(null);
  const [questions, setQuestions] = useState<string[]>([]);
  const [answers, setAnswers] = useState<string[]>([]);
  const [notes, setNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const fetchChallenges = async () => {
    setLoading(true);
    try {
      const data = await getStudentTasks();
      setChallenges(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Failed to fetch student tasks/challenges:', err);
      setChallenges([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchChallenges();
  }, []);

  const onRefresh = async () => {
    setRefreshing(true);
    await refreshData();
    await fetchChallenges();
    setRefreshing(false);
  };

  const open = async (task: Challenge) => {
    setOpenTask(task);
    setNotes('');
    setQuestions([]);
    setAnswers([]);
    try {
      const q = await getTaskQuestions(task.id);
      // The server returns {question1, question2, question3}, omitting any the
      // teacher left blank, so read them in order and drop the gaps.
      const list = ['question1', 'question2', 'question3']
        .map((k) => q?.[k])
        .filter((v): v is string => Boolean(v));
      setQuestions(list);
      setAnswers(list.map(() => ''));
    } catch (err) {
      console.error('Could not load task questions:', err);
    }
  };

  const hand_in = async () => {
    if (!openTask) return;
    setSubmitting(true);
    try {
      await submitTask({ taskId: openTask.id, notes, answers });
      setOpenTask(null);
      Alert.alert('Handed in', 'Your teacher will review it and award the XP.');
      await fetchChallenges();
    } catch (err: any) {
      const message = err?.response?.data?.error || 'Could not hand this in. Please try again.';
      Alert.alert('Not submitted', message);
    } finally {
      setSubmitting(false);
    }
  };

  const answered = questions.length === 0 || answers.every((a) => a.trim().length > 0);

  return (
    <>
      <ScrollView
        style={styles.container}
        contentContainerStyle={{ paddingBottom: insets.bottom + T.space.xl }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={T.brand} />}
      >
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Active Challenges</Text>
          {loading ? (
            <ActivityIndicator color={T.brand} style={{ marginTop: 20 }} />
          ) : challenges.length === 0 ? (
            <View style={styles.emptyCard}>
              <Text style={styles.emptyTitle}>No challenges assigned yet.</Text>
              <Text style={styles.emptySubtext}>Check back later!</Text>
            </View>
          ) : (
            challenges.map((item) => (
              <TouchableOpacity
                key={item.id}
                style={styles.card}
                onPress={() => open(item)}
                accessibilityRole="button"
                accessibilityLabel={`${item.title}, ${item.xpReward} XP. Open to hand in.`}
              >
                <View style={styles.cardHeader}>
                  <Text style={styles.cardTitle}>{item.title}</Text>
                  <View style={[styles.badge, { backgroundColor: typeBg[item.taskType] || T.line }]}>
                    <Text style={[styles.badgeText, { color: typeColor[item.taskType] || T.text3 }]}>
                      {item.taskType}
                    </Text>
                  </View>
                </View>
                <Text style={styles.cardDescription}>{item.description}</Text>
                <View style={styles.cardFooter}>
                  <Text style={styles.xpText}>+{item.xpReward} XP</Text>
                  {item.dueDate && <Text style={styles.dateText}>Due: {item.dueDate}</Text>}
                </View>
                <Text style={styles.openHint}>Tap to open and hand in →</Text>
              </TouchableOpacity>
            ))
          )}
        </View>
      </ScrollView>

      <Modal
        visible={openTask !== null}
        transparent
        animationType="slide"
        onRequestClose={() => setOpenTask(null)}
      >
        <View style={styles.modalOverlay}>
          <View style={[styles.modalCard, { paddingBottom: T.space.lg + insets.bottom }]}>
            <Text style={styles.modalTitle}>{openTask?.title}</Text>
            <Text style={styles.modalMeta}>
              {openTask?.taskType} · +{openTask?.xpReward} XP
              {openTask?.dueDate ? ` · due ${openTask.dueDate}` : ''}
            </Text>

            <ScrollView style={{ maxHeight: 380 }} keyboardShouldPersistTaps="handled">
              {!!openTask?.description && (
                <Text style={styles.modalBody}>{openTask.description}</Text>
              )}

              {questions.map((q, i) => (
                <View key={i} style={styles.questionBlock}>
                  <Text style={styles.questionText}>{i + 1}. {q}</Text>
                  <TextInput
                    style={styles.input}
                    value={answers[i]}
                    onChangeText={(v) => setAnswers((prev) => prev.map((a, j) => (j === i ? v : a)))}
                    placeholder="Your answer"
                    placeholderTextColor={T.text4}
                    multiline
                  />
                </View>
              ))}

              <View style={styles.questionBlock}>
                <Text style={styles.questionText}>
                  {questions.length > 0 ? 'Anything else for your teacher?' : 'What did you do?'}
                </Text>
                <TextInput
                  style={styles.input}
                  value={notes}
                  onChangeText={setNotes}
                  placeholder="Optional note"
                  placeholderTextColor={T.text4}
                  multiline
                />
              </View>
            </ScrollView>

            <View style={styles.modalActions}>
              <TouchableOpacity style={styles.secondaryBtn} onPress={() => setOpenTask(null)}>
                <Text style={styles.secondaryBtnText}>Close</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.primaryBtn, (!answered || submitting) && styles.primaryBtnDisabled]}
                onPress={hand_in}
                disabled={!answered || submitting}
              >
                <Text style={styles.primaryBtnText}>{submitting ? 'Sending…' : 'Hand in'}</Text>
              </TouchableOpacity>
            </View>
            {!answered && (
              <Text style={styles.hintText}>Answer every question to hand this in.</Text>
            )}
          </View>
        </View>
      </Modal>
    </>
  );
}

const makeStyles = (T: Theme) => StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: T.bg,
    padding: 16,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    color: T.text,
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 12,
  },
  card: {
    backgroundColor: T.surface,
    borderRadius: 14,
    padding: 16,
    marginBottom: 12,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: 8,
  },
  cardTitle: {
    color: T.text,
    fontSize: 16,
    fontWeight: '700',
    flex: 1,
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 8,
  },
  badgeText: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  cardDescription: {
    color: T.text3,
    fontSize: 14,
    marginTop: 8,
    lineHeight: 20,
  },
  cardFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: T.line,
  },
  xpText: {
    color: T.successInk,
    fontSize: 15,
    fontWeight: '700',
  },
  dateText: {
    color: T.text3,
    fontSize: 12,
  },
  openHint: {
    color: T.brandInk,
    fontSize: 12,
    fontWeight: '600',
    marginTop: 10,
  },
  emptyCard: {
    height: 100,
    paddingVertical: 16,
    paddingHorizontal: 24,
    backgroundColor: T.surface,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyTitle: {
    color: T.text,
    fontSize: 15,
    fontWeight: '600',
    textAlign: 'center',
  },
  emptySubtext: {
    color: T.text3,
    fontSize: 13,
    marginTop: 4,
    textAlign: 'center',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: T.scrim,
    justifyContent: 'flex-end',
  },
  modalCard: {
    backgroundColor: T.surface,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 20,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: T.text,
  },
  modalMeta: {
    fontSize: 12,
    color: T.text3,
    marginTop: 4,
    marginBottom: 12,
  },
  modalBody: {
    fontSize: 14,
    color: T.text2,
    lineHeight: 20,
    marginBottom: 12,
  },
  questionBlock: {
    marginBottom: 14,
  },
  questionText: {
    fontSize: 14,
    fontWeight: '600',
    color: T.text,
    marginBottom: 6,
  },
  input: {
    borderWidth: 1,
    borderColor: T.lineStrong,
    borderRadius: T.rXs,
    padding: 10,
    fontSize: 14,
    color: T.text,
    backgroundColor: T.surface2,
    minHeight: 44,
    textAlignVertical: 'top',
  },
  modalActions: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 8,
  },
  secondaryBtn: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: T.rSm,
    borderWidth: 1,
    borderColor: T.lineStrong,
    alignItems: 'center',
  },
  secondaryBtnText: {
    color: T.text2,
    fontWeight: '600',
    fontSize: 14,
  },
  primaryBtn: {
    flex: 2,
    paddingVertical: 12,
    borderRadius: T.rSm,
    backgroundColor: T.brand,
    alignItems: 'center',
  },
  primaryBtnDisabled: {
    opacity: 0.5,
  },
  primaryBtnText: {
    color: T.onBrand,
    fontWeight: '700',
    fontSize: 14,
  },
  hintText: {
    color: T.text3,
    fontSize: 12,
    textAlign: 'center',
    marginTop: 8,
  },
});
