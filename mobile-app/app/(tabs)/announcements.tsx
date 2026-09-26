import React, { useContext, useEffect, useMemo, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, ActivityIndicator, TouchableOpacity, Modal } from 'react-native';
import { SymbolView } from 'expo-symbols';
import { createAudioPlayer, type AudioPlayer } from 'expo-audio';
import {
  getParentAnnouncements,
  getSupportedLanguages,
  getAnnouncementLocalized,
  getAnnouncementSpeech,
  setPreferredLanguage,
} from '../../services/api';
import { useTheme, type Theme } from '../../context/ThemeContext';
import { DataContext } from './_layout';

export default function AnnouncementsScreen() {
  const T = useTheme();
  const styles = useMemo(() => makeStyles(T), [T]);
  const { data } = useContext(DataContext);
  const [announcements, setAnnouncements] = useState<any[]>([]);
  const [languages, setLanguages] = useState<any[]>([]);
  /**
   * The language notices are translated and read aloud in.
   *
   * <p>R3-P2-5. This was `useState('en')`, and the picker below has always saved
   * the choice to the parent's record -- so the screen wrote a preference and then
   * ignored it. A parent who picked Hindi came back to a corner chip reading
   * "English" and notices in English, which is why an unexplained language name
   * up there read as stray text left behind by something: it was never their
   * setting, just a hardcoded default.
   *
   * <p>Derived rather than copied into state in an effect, which is how the same
   * field went stale on the recognition screen: `data` arrives after the first
   * render, and a local copy taken once is a copy taken too early.
   */
  const [pickedLang, setPickedLang] = useState<string | null>(null);
  const selectedLang = pickedLang ?? data?.parent?.preferredLanguage ?? 'en';
  const [loading, setLoading] = useState(true);
  const [pickerVisible, setPickerVisible] = useState(false);
  const [localized, setLocalized] = useState<Record<string, { title: string; content: string }>>({});
  const [busyId, setBusyId] = useState<string | null>(null);
  const [player, setPlayer] = useState<AudioPlayer | null>(null);

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
      // expo-audio players hold a native resource until removed.
      player?.remove();
    };
  }, []);

  const handlePickLanguage = async (code: string) => {
    setPickerVisible(false);
    if (code === selectedLang) {
      return;
    }
    setPickedLang(code);
    // Translations already fetched were in the old language.
    setLocalized({});
    // Including English. This was guarded with `if (code !== 'en')`, so a parent
    // who tried Hindi and wanted to go back kept Hindi on their record for good
    // -- the one choice the picker offered that it refused to remember.
    try {
      await setPreferredLanguage(code);
    } catch (e) {
      console.log('Failed to save preferred language:', e);
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
      player?.remove();
      const nextPlayer = createAudioPlayer({ uri: `data:audio/mpeg;base64,${audioBase64}` });
      setPlayer(nextPlayer);
      nextPlayer.play();
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
        <ActivityIndicator size="large" color={T.brand} />
      </View>
    );
  }

  return (
    <View style={styles.root}>
      <View style={styles.headerBand}>
        <View style={styles.headerIconWrap}>
          <SymbolView name={{ ios: 'megaphone', android: 'campaign', web: 'campaign' }} tintColor={T.brand} size={26} />
        </View>
        <View style={{ flex: 1, marginLeft: 14 }}>
          <Text style={styles.headerTitle} numberOfLines={1}>Announcements</Text>
          <Text style={styles.headerSubtitle} numberOfLines={1}>From your school</Text>
        </View>
        {/* The language control. A globe and an accessible name were added for
            this once and QA still read it as stray text, which was fair: it said
            only "English", and in the default state nothing on the screen visibly
            depends on it -- Translate does not even appear until the language is
            something other than English. So it now names what it governs rather
            than only its value, and shows the parent's actual saved language. */}
        <TouchableOpacity
          style={styles.langBtn}
          onPress={() => setPickerVisible(true)}
          accessibilityRole="button"
          accessibilityLabel={
            `Notices are read in ${selectedLanguageName}. Change the language.`
          }
          hitSlop={10}
        >
          <SymbolView
            name={{ ios: 'globe', android: 'language', web: 'language' }}
            tintColor={T.brand}
            size={14}
          />
          <Text style={styles.langBtnText} numberOfLines={1}>Read in {selectedLanguageName}</Text>
        </TouchableOpacity>
      </View>

      {announcements.length === 0 ? (
        <View style={styles.center}>
          <SymbolView name={{ ios: 'megaphone', android: 'campaign', web: 'campaign' }} tintColor={T.text2} size={48} />
          <Text style={styles.emptyTitle}>No announcements yet</Text>
          <Text style={styles.emptySubtext}>Notices from your school will appear here.</Text>
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
                      {busy ? <ActivityIndicator size="small" color={T.brand} /> : <Text style={styles.actionBtnText}>Translate</Text>}
                    </TouchableOpacity>
                  )}
                  <TouchableOpacity style={styles.actionBtn} onPress={() => handlePlay(a.id)} disabled={busy}>
                    {busy ? <ActivityIndicator size="small" color={T.brand} /> : (
                      <>
                        <SymbolView name={{ ios: 'speaker.wave.2', android: 'volume_up', web: 'volume_up' }} tintColor={T.brand} size={16} />
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

const makeStyles = (T: Theme) => StyleSheet.create({
  root: { flex: 1, backgroundColor: T.bg },
  center: { flex: 1, backgroundColor: T.bg, justifyContent: 'center', alignItems: 'center' },
  headerBand: {
    flexDirection: 'row', alignItems: 'center',
    backgroundColor: T.surface, paddingHorizontal: 20, paddingVertical: 18,
    borderBottomWidth: 1, borderBottomColor: T.line,
  },
  headerIconWrap: { width: 48, height: 48, borderRadius: 14, backgroundColor: T.brand50, justifyContent: 'center', alignItems: 'center' },
  emptySubtext: { fontSize: 13, color: T.text3, marginTop: 6, textAlign: 'center' },
  headerTitle: { fontSize: 17, fontWeight: '700', color: T.text },
  headerSubtitle: { fontSize: 12, color: T.text3, marginTop: 2 },
  // A row now that it carries a globe, and at least 44dp tall so it is a
  // real target rather than a word you have to aim at.
  langBtn: {
    flexDirection: 'row', alignItems: 'center', gap: 6,
    backgroundColor: T.brand50, borderRadius: 10,
    paddingHorizontal: 12, paddingVertical: 8, minHeight: 44,
    borderWidth: 1, borderColor: T.brand100,
  },
  langBtnText: { color: T.brand, fontSize: 12, fontWeight: '600' },
  emptyTitle: { fontSize: 16, fontWeight: '600', color: T.text, marginTop: 16 },
  scroll: { flex: 1 },
  scrollContent: { padding: 16, gap: 12 },
  card: { backgroundColor: T.surface, borderRadius: 14, padding: 16, borderWidth: 1, borderColor: T.line, marginBottom: 12 },
  cardTitle: { color: T.text, fontSize: 15, fontWeight: '700', marginBottom: 6 },
  cardBody: { color: T.text3, fontSize: 13, lineHeight: 19 },
  cardActions: { flexDirection: 'row', gap: 8, marginTop: 12 },
  actionBtn: { flexDirection: 'row', alignItems: 'center', gap: 6, backgroundColor: T.brand50, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 8 },
  actionBtnText: { color: T.brand, fontSize: 12, fontWeight: '600' },
  modalOverlay: { flex: 1, backgroundColor: T.scrim, justifyContent: 'flex-end' },
  modalCard: { backgroundColor: T.surface, borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20 },
  modalTitle: { fontSize: 16, fontWeight: '700', color: T.text, marginBottom: 12 },
  langRow: { padding: 12, borderRadius: 10, marginBottom: 4 },
  langRowActive: { backgroundColor: T.brand50 },
  langRowText: { color: T.text, fontSize: 14 },
});
