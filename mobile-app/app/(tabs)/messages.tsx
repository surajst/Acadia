import React, { useContext, useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, ScrollView, ActivityIndicator, TouchableOpacity, TextInput, Modal } from 'react-native';
import { SymbolView } from 'expo-symbols';
import { DataContext } from './_layout';
import {
  getConversations,
  startConversation,
  getConversationThread,
  sendConversationReply,
  getTeacherMessageRoster,
  getParentMessageTeachers,
} from '@/services/api';

export default function MessagesScreen() {
  const { role, data } = useContext(DataContext);
  const isTeacher = role === 'TEACHER';
  const studentId = data?.student?.id;

  const [conversations, setConversations] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [roster, setRoster] = useState<any[]>([]);
  const [pickerVisible, setPickerVisible] = useState(false);
  const [newBody, setNewBody] = useState('');
  const [selectedTarget, setSelectedTarget] = useState<any | null>(null);

  const [activeConversation, setActiveConversation] = useState<any | null>(null);
  const [thread, setThread] = useState<any[]>([]);
  const [replyBody, setReplyBody] = useState('');

  const loadConversations = useCallback(async () => {
    try {
      const list = await getConversations();
      setConversations(list);
    } catch (e) {
      console.error('Failed to fetch conversations:', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadConversations();
    (async () => {
      try {
        if (isTeacher) {
          setRoster(await getTeacherMessageRoster());
        } else if (studentId) {
          setRoster(await getParentMessageTeachers(studentId));
        }
      } catch (e) {
        console.error('Failed to fetch roster:', e);
      }
    })();
  }, [isTeacher, studentId, loadConversations]);

  const openThread = async (conversation: any) => {
    setActiveConversation(conversation);
    try {
      const messages = await getConversationThread(conversation.id);
      setThread(messages);
      loadConversations();
    } catch (e) {
      console.error('Failed to fetch thread:', e);
    }
  };

  const handleStartConversation = async () => {
    if (!selectedTarget || !newBody.trim()) return;
    const payload = isTeacher
      ? { studentId: selectedTarget.studentId, body: newBody.trim() }
      : { studentId, teacherId: selectedTarget.teacherId, body: newBody.trim() };
    try {
      const conv = await startConversation(payload);
      setNewBody('');
      setPickerVisible(false);
      setSelectedTarget(null);
      await loadConversations();
      openThread(conv);
    } catch (e) {
      console.error('Failed to start conversation:', e);
    }
  };

  const handleSendReply = async () => {
    if (!activeConversation || !replyBody.trim()) return;
    try {
      await sendConversationReply(activeConversation.id, replyBody.trim());
      setReplyBody('');
      openThread(activeConversation);
    } catch (e) {
      console.error('Failed to send reply:', e);
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
          <SymbolView name={{ ios: 'message', android: 'chat', web: 'chat' }} tintColor="#6366f1" size={26} />
        </View>
        <View style={{ flex: 1, marginLeft: 14 }}>
          <Text style={styles.headerTitle}>Messages</Text>
          <Text style={styles.headerSubtitle}>{isTeacher ? "Message a student's parent" : 'Message a teacher'}</Text>
        </View>
        <TouchableOpacity style={styles.newBtn} onPress={() => setPickerVisible(true)}>
          <SymbolView name={{ ios: 'square.and.pencil', android: 'edit', web: 'edit' }} tintColor="#fff" size={18} />
        </TouchableOpacity>
      </View>

      {activeConversation ? (
        <View style={{ flex: 1 }}>
          <View style={styles.threadHeader}>
            <TouchableOpacity onPress={() => setActiveConversation(null)}>
              <SymbolView name={{ ios: 'chevron.left', android: 'arrow_back', web: 'arrow_back' }} tintColor="#6366f1" size={20} />
            </TouchableOpacity>
            <Text style={styles.threadTitle}>
              {isTeacher ? activeConversation.studentName : activeConversation.teacherName}
            </Text>
          </View>
          <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent}>
            {thread.map((m) => (
              <View key={m.id} style={[styles.bubble, m.mine ? styles.bubbleMine : styles.bubbleTheirs]}>
                <Text style={styles.bubbleText}>{m.body}</Text>
              </View>
            ))}
          </ScrollView>
          <View style={styles.replyRow}>
            <TextInput
              style={styles.input}
              placeholder="Type a reply…"
              placeholderTextColor="#64748b"
              value={replyBody}
              onChangeText={setReplyBody}
            />
            <TouchableOpacity style={styles.sendBtn} onPress={handleSendReply}>
              <Text style={styles.sendBtnText}>Send</Text>
            </TouchableOpacity>
          </View>
        </View>
      ) : conversations.length === 0 ? (
        <View style={styles.center}>
          <SymbolView name={{ ios: 'message', android: 'chat', web: 'chat' }} tintColor="#334155" size={48} />
          <Text style={styles.emptyTitle}>No conversations yet</Text>
        </View>
      ) : (
        <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent}>
          {conversations.map((c) => (
            <TouchableOpacity key={c.id} style={styles.convCard} onPress={() => openThread(c)}>
              <View style={{ flex: 1 }}>
                <Text style={styles.convName}>{isTeacher ? c.studentName : c.teacherName}</Text>
                <Text style={styles.convPreview} numberOfLines={1}>{c.lastMessagePreview}</Text>
              </View>
              {c.unread && <View style={styles.unreadDot} />}
            </TouchableOpacity>
          ))}
        </ScrollView>
      )}

      <Modal visible={pickerVisible} transparent animationType="slide" onRequestClose={() => setPickerVisible(false)}>
        <View style={styles.modalOverlay}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>New Message</Text>
            <ScrollView style={{ maxHeight: 200 }}>
              {roster.map((r, idx) => (
                <TouchableOpacity
                  key={idx}
                  style={[styles.rosterRow, selectedTarget === r && styles.rosterRowActive]}
                  onPress={() => setSelectedTarget(r)}
                >
                  <Text style={styles.rosterRowText}>
                    {isTeacher ? `${r.studentName} (${r.className})` : `${r.teacherName} (${r.subjectName})`}
                  </Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
            <TextInput
              style={styles.input}
              placeholder="Type a message…"
              placeholderTextColor="#64748b"
              value={newBody}
              onChangeText={setNewBody}
            />
            <View style={styles.modalActions}>
              <TouchableOpacity style={styles.cancelBtn} onPress={() => setPickerVisible(false)}>
                <Text style={styles.cancelBtnText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.sendBtn} onPress={handleStartConversation}>
                <Text style={styles.sendBtnText}>Send</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
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
  newBtn: { width: 36, height: 36, borderRadius: 10, backgroundColor: '#6366f1', justifyContent: 'center', alignItems: 'center' },
  scroll: { flex: 1 },
  scrollContent: { padding: 16, gap: 8 },
  convCard: {
    flexDirection: 'row', alignItems: 'center', backgroundColor: '#1e293b',
    borderRadius: 12, padding: 14, borderWidth: 1, borderColor: '#334155',
  },
  convName: { fontSize: 14, fontWeight: '700', color: '#f1f5f9' },
  convPreview: { fontSize: 12, color: '#64748b', marginTop: 2 },
  unreadDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: '#6366f1' },
  emptyTitle: { fontSize: 16, fontWeight: '600', color: '#f1f5f9', marginTop: 16 },
  threadHeader: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: 16, borderBottomWidth: 1, borderBottomColor: '#334155' },
  threadTitle: { fontSize: 15, fontWeight: '700', color: '#f1f5f9' },
  bubble: { maxWidth: '80%', borderRadius: 14, padding: 10, marginBottom: 8 },
  bubbleMine: { backgroundColor: '#6366f140', alignSelf: 'flex-end' },
  bubbleTheirs: { backgroundColor: '#1e293b', alignSelf: 'flex-start' },
  bubbleText: { color: '#f1f5f9', fontSize: 13 },
  replyRow: { flexDirection: 'row', gap: 8, padding: 12, borderTopWidth: 1, borderTopColor: '#334155' },
  input: { flex: 1, backgroundColor: '#1e293b', borderRadius: 10, borderWidth: 1, borderColor: '#334155', paddingHorizontal: 12, color: '#f1f5f9', marginTop: 10 },
  sendBtn: { backgroundColor: '#6366f1', borderRadius: 10, paddingHorizontal: 16, justifyContent: 'center' },
  sendBtnText: { color: '#fff', fontWeight: '700', fontSize: 13 },
  modalOverlay: { flex: 1, backgroundColor: '#00000090', justifyContent: 'flex-end' },
  modalCard: { backgroundColor: '#1e293b', borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20 },
  modalTitle: { fontSize: 16, fontWeight: '700', color: '#f1f5f9', marginBottom: 12 },
  rosterRow: { padding: 10, borderRadius: 10, marginBottom: 4 },
  rosterRowActive: { backgroundColor: '#6366f122' },
  rosterRowText: { color: '#f1f5f9', fontSize: 13 },
  modalActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: 8, marginTop: 12 },
  cancelBtn: { paddingHorizontal: 16, justifyContent: 'center' },
  cancelBtnText: { color: '#94a3b8', fontWeight: '600' },
});
