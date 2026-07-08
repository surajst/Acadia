import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, ActivityIndicator, TouchableOpacity, Modal } from 'react-native';
import { SymbolView } from 'expo-symbols';
import { Audio } from 'expo-av';
import {
  getParentAnnouncements,
  getSupportedLanguages,
  getAnnouncementLocalized,
  getAnnouncementSpeech,
  setPreferredLanguage,
} from '../../services/api';

export default function AnnouncementsScreen() {
  const [announcements, setAnnouncements] = useState<any[]>([]);
  const [languages, setLanguages] = useState<any[]>([]);
  const [selectedLang, setSelectedLang] = useState('en');
  const [loading, setLoading] = useState(true);
  const [pickerVisible, setPickerVisible] = useState(false);
  const [localized, setLocalized] = useState<Record<string, { title: string; content: string }>>({});
  const [busyId, setBusyId] = useState<string | null>(null);
  const [sound, setSound] = useState<Audio.Sound | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const [list, langs] = await Promise.all([getParentAnnouncements(), getSupportedLanguages()]);
        setAnnouncements(list);
        setLanguages(langs);
      } catch (e) {
        console.log('Failed to load announcements:', e);
      } finally {
        setLoading(false);
      }
    })();
    return () => {
      sound?.unloadAsync();
    };
  }, []);

  const handlePickLanguage = async (code: string) => {
    setSelectedLang(code);
    setPickerVisible(false);
    setLocalized({});
    if (code !== 'en') {
      try {
        await setPreferredLanguage(code);
      } catch (e) {
        console.log('Failed to save preferred language:', e);
      }
    }
  };

  const handleTranslate = async (id: string) => {
    if (selectedLang === 'en' || localized[id]) return;
    setBusyId(id);
    try {
      const result = await getAnnouncementLocalized(id, selectedLang);
      setLocalized((prev) => ({ ...prev, [id]: result }));
    } catch (e) {
      console.log('Failed to translate announcement:', e);
    } finally {
      setBusyId(null);
    }
  };

  const handlePlay = async (id: string) => {
    setBusyId(id);
    try {
      const { audioBase64 } = await getAnnouncementSpeech(id, selectedLang);
      await sound?.unloadAsync();
      const { sound: newSound } = await Audio.Sound.createAsync({
        uri: `data:audio/mpeg;base64,${audioBase64}`,
      });
      setSound(newSound);
      await newSound.playAsync();
    } catch (e) {
      console.log('Failed to play announcement audio:', e);
    } finally {
      setBusyId(null);
    }
  };

  const selectedLanguageName = languages.find((l) => l.code === selectedLang)?.name || 'English';

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
          <SymbolView name={{ ios: 'megaphone', android: 'campaign', web: 'campaign' }} tintColor="#6366f1" size={26} />
        </View>
        <View style={{ flex: 1, marginLeft: 14 }}>
          <Text style={styles.headerTitle}>Announcements</Text>
          <Text style={styles.headerSubtitle}>From your school</Text>
        </View>
        <TouchableOpacity style={styles.langBtn} onPress={() => setPickerVisible(true)}>
          <Text style={styles.langBtnText}>{selectedLanguageName}</Text>
        </TouchableOpacity>
      </View>

      {announcements.length === 0 ? (
        <View style={styles.center}>
          <SymbolView name={{ ios: 'megaphone', android: 'campaign', web: 'campaign' }} tintColor="#334155" size={48} />
          <Text style={styles.emptyTitle}>No announcements yet</Text>
        </View>
      ) : (
        <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent}>
          {announcements.map((a) => {
            const shown = localized[a.id];
            const busy = busyId === a.id;
            return (
              <View key={a.id} style={styles.card}>
                <Text style={styles.cardTitle}>{shown?.title ?? a.title}</Text>
                <Text style={styles.cardBody}>{shown?.content ?? a.content}</Text>
                <View style={styles.cardActions}>
                  {selectedLang !== 'en' && !shown && (
                    <TouchableOpacity style={styles.actionBtn} onPress={() => handleTranslate(a.id)} disabled={busy}>
                      {busy ? <ActivityIndicator size="small" color="#6366f1" /> : <Text style={styles.actionBtnText}>Translate</Text>}
                    </TouchableOpacity>
                  )}
                  <TouchableOpacity style={styles.actionBtn} onPress={() => handlePlay(a.id)} disabled={busy}>
                    {busy ? <ActivityIndicator size="small" color="#6366f1" /> : (
                      <>
                        <SymbolView name={{ ios: 'speaker.wave.2', android: 'volume_up', web: 'volume_up' }} tintColor="#6366f1" size={16} />
                        <Text style={styles.actionBtnText}>Play</Text>
                      </>
                    )}
                  </TouchableOpacity>
                </View>
              </View>
            );
          })}
        </ScrollView>
      )}

      <Modal visible={pickerVisible} transparent animationType="slide" onRequestClose={() => setPickerVisible(false)}>
        <View style={styles.modalOverlay}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>Choose Language</Text>
            <ScrollView style={{ maxHeight: 300 }}>
              {languages.map((l) => (
                <TouchableOpacity
                  key={l.code}
                  style={[styles.langRow, selectedLang === l.code && styles.langRowActive]}
                  onPress={() => handlePickLanguage(l.code)}
                >
                  <Text style={styles.langRowText}>{l.name}</Text>
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
  langBtn: { backgroundColor: '#6366f120', borderRadius: 10, paddingHorizontal: 12, paddingVertical: 8, borderWidth: 1, borderColor: '#6366f140' },
  langBtnText: { color: '#6366f1', fontSize: 12, fontWeight: '600' },
  emptyTitle: { fontSize: 16, fontWeight: '600', color: '#f1f5f9', marginTop: 16 },
  scroll: { flex: 1 },
  scrollContent: { padding: 16, gap: 12 },
  card: { backgroundColor: '#1e293b', borderRadius: 14, padding: 16, borderWidth: 1, borderColor: '#334155', marginBottom: 12 },
  cardTitle: { color: '#f1f5f9', fontSize: 15, fontWeight: '700', marginBottom: 6 },
  cardBody: { color: '#94a3b8', fontSize: 13, lineHeight: 19 },
  cardActions: { flexDirection: 'row', gap: 8, marginTop: 12 },
  actionBtn: { flexDirection: 'row', alignItems: 'center', gap: 6, backgroundColor: '#6366f118', borderRadius: 10, paddingHorizontal: 12, paddingVertical: 8 },
  actionBtnText: { color: '#6366f1', fontSize: 12, fontWeight: '600' },
  modalOverlay: { flex: 1, backgroundColor: '#00000090', justifyContent: 'flex-end' },
  modalCard: { backgroundColor: '#1e293b', borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20 },
  modalTitle: { fontSize: 16, fontWeight: '700', color: '#f1f5f9', marginBottom: 12 },
  langRow: { padding: 12, borderRadius: 10, marginBottom: 4 },
  langRowActive: { backgroundColor: '#6366f122' },
  langRowText: { color: '#f1f5f9', fontSize: 14 },
});
