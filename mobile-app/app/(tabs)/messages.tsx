import React, { useContext, useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, ScrollView, ActivityIndicator, TouchableOpacity, TextInput, Modal, Platform, Alert } from 'react-native';
import { SymbolView } from 'expo-symbols';
import { Audio } from 'expo-av';
import { DataContext } from './_layout';
import {
  getConversations,
  startConversation,
  getConversationThread,
  sendConversationReply,
  getTeacherMessageRoster,
  getParentMessageTeachers,
  getSupportedLanguages,
  getMessageLocalized,
  getMessageSpeech,
  sendVoiceReply,
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

  const isParent = role === 'PARENT';
  const [languages, setLanguages] = useState<any[]>([]);
  const [lang, setLang] = useState('en');
  const [langPickerVisible, setLangPickerVisible] = useState(false);
  const [localized, setLocalized] = useState<Record<string, string>>({});
  const [busyMessageId, setBusyMessageId] = useState<string | null>(null);
  const [sound, setSound] = useState<Audio.Sound | null>(null);
  const [recording, setRecording] = useState<Audio.Recording | null>(null);
  const [recordingBusy, setRecordingBusy] = useState(false);

  useEffect(() => {
    if (!isParent) return;
    getSupportedLanguages().then(setLanguages).catch(() => setLanguages([]));
  }, [isParent]);

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

  const handleTranslateMessage = async (messageId: string) => {
    if (!activeConversation || lang === 'en' || localized[messageId]) return;
    setBusyMessageId(messageId);
    try {
      const result = await getMessageLocalized(activeConversation.id, messageId, lang);
      setLocalized((prev) => ({ ...prev, [messageId]: result.body }));
    } catch (e) {
      console.log('Failed to translate message:', e);
    } finally {
      setBusyMessageId(null);
    }
  };

  const handlePlayMessage = async (messageId: string) => {
    if (!activeConversation) return;
    setBusyMessageId(messageId);
    try {
      const { audioBase64 } = await getMessageSpeech(activeConversation.id, messageId, lang);
      await sound?.unloadAsync();
      const { sound: newSound } = await Audio.Sound.createAsync({ uri: `data:audio/mpeg;base64,${audioBase64}` });
      setSound(newSound);
      await newSound.playAsync();
    } catch (e) {
      console.log('Failed to play message audio:', e);
    } finally {
      setBusyMessageId(null);
    }
  };

  const handleToggleRecording = async () => {
    if (Platform.OS === 'web') {
      Alert.alert('Voice reply', 'Voice recording is only available in the native app, not the web version.');
      return;
    }
    if (!activeConversation) return;

    if (recording) {
      setRecordingBusy(true);
      try {
        await recording.stopAndUnloadAsync();
        const uri = recording.getURI();
        setRecording(null);
        if (uri) {
          await sendVoiceReply(activeConversation.id, uri, lang);
          openThread(activeConversation);
        }
      } catch (e) {
        console.log('Failed to send voice reply:', e);
        Alert.alert('Voice reply', 'Could not send your voice message.');
      } finally {
        setRecordingBusy(false);
      }
      return;
    }

    try {
      const permission = await Audio.requestPermissionsAsync();
      if (!permission.granted) {
        Alert.alert('Voice reply', 'Microphone permission was not granted.');
        return;
      }
      await Audio.setAudioModeAsync({ allowsRecordingIOS: true, playsInSilentModeIOS: true });
      const { recording: newRecording } = await Audio.Recording.createAsync(Audio.RecordingOptionsPresets.HIGH_QUALITY);
      setRecording(newRecording);
    } catch (e) {
      console.log('Failed to start recording:', e);
      Alert.alert('Voice reply', 'Could not start recording.');
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
            {isParent && (
              <TouchableOpacity style={styles.langBtn} onPress={() => setLangPickerVisible(true)}>
                <Text style={styles.langBtnText}>{languages.find((l) => l.code === lang)?.name ?? 'English'}</Text>
              </TouchableOpacity>
            )}
          </View>
          <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent}>
            {thread.map((m) => {
              const busy = busyMessageId === m.id;
              return (
                <View key={m.id} style={[styles.bubble, m.mine ? styles.bubbleMine : styles.bubbleTheirs]}>
                  <Text style={styles.bubbleText}>{localized[m.id] ?? m.body}</Text>
                  {isParent && !m.mine && (
                    <View style={styles.bubbleActions}>
                      {lang !== 'en' && !localized[m.id] && (
                        <TouchableOpacity onPress={() => handleTranslateMessage(m.id)} disabled={busy}>
                          <Text style={styles.bubbleActionText}>{busy ? '…' : 'Translate'}</Text>
                        </TouchableOpacity>
                      )}
                      <TouchableOpacity onPress={() => handlePlayMessage(m.id)} disabled={busy}>
                        <SymbolView name={{ ios: 'speaker.wave.2', android: 'volume_up', web: 'volume_up' }} tintColor="#6366f1" size={14} />
                      </TouchableOpacity>
                    </View>
                  )}
                </View>
              );
            })}
          </ScrollView>
          <View style={styles.replyRow}>
            <TextInput
              style={styles.input}
              placeholder="Type a reply…"
              placeholderTextColor="#64748b"
              value={replyBody}
              onChangeText={setReplyBody}
            />
            {isParent && (
              <TouchableOpacity
                style={[styles.micBtn, recording && styles.micBtnActive]}
                onPress={handleToggleRecording}
                disabled={recordingBusy}
              >
                {recordingBusy ? (
                  <ActivityIndicator size="small" color="#fff" />
                ) : (
                  <SymbolView name={{ ios: 'mic', android: 'mic', web: 'mic' }} tintColor="#fff" size={18} />
                )}
              </TouchableOpacity>
            )}
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

      <Modal visible={langPickerVisible} transparent animationType="slide" onRequestClose={() => setLangPickerVisible(false)}>
        <View style={styles.modalOverlay}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>Choose Language</Text>
            <ScrollView style={{ maxHeight: 300 }}>
              {languages.map((l) => (
                <TouchableOpacity
                  key={l.code}
                  style={[styles.rosterRow, lang === l.code && styles.rosterRowActive]}
                  onPress={() => { setLang(l.code); setLangPickerVisible(false); setLocalized({}); }}
                >
                  <Text style={styles.rosterRowText}>{l.name}</Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
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
  threadTitle: { fontSize: 15, fontWeight: '700', color: '#f1f5f9', flex: 1 },
  langBtn: { backgroundColor: '#6366f120', borderRadius: 10, paddingHorizontal: 10, paddingVertical: 6, borderWidth: 1, borderColor: '#6366f140' },
  langBtnText: { color: '#6366f1', fontSize: 11, fontWeight: '600' },
  bubble: { maxWidth: '80%', borderRadius: 14, padding: 10, marginBottom: 8 },
  bubbleMine: { backgroundColor: '#6366f140', alignSelf: 'flex-end' },
  bubbleTheirs: { backgroundColor: '#1e293b', alignSelf: 'flex-start' },
  bubbleText: { color: '#f1f5f9', fontSize: 13 },
  bubbleActions: { flexDirection: 'row', gap: 10, marginTop: 6, alignItems: 'center' },
  bubbleActionText: { color: '#6366f1', fontSize: 11, fontWeight: '600' },
  replyRow: { flexDirection: 'row', gap: 8, padding: 12, borderTopWidth: 1, borderTopColor: '#334155' },
  micBtn: { width: 40, height: 40, borderRadius: 10, backgroundColor: '#334155', justifyContent: 'center', alignItems: 'center' },
  micBtnActive: { backgroundColor: '#ef4444' },
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
