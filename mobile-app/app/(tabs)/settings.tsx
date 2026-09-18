import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView,
  ActivityIndicator, Alert, Modal,
} from 'react-native';
import { useContext, useEffect, useMemo, useState } from 'react';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useAuth } from '@/context/AuthContext';
import { DataContext } from './_layout';
import { getSupportedLanguages, setPreferredLanguage } from '../../services/api';
import { useThemePicker, type Theme } from '../../context/ThemeContext';

/**
 * Preferences and sign-out, split out of the profile screen when the wheel
 * gave the home screen two corners: identity top-left, settings top-right.
 *
 * <p>This lives inside the (tabs) group rather than at the root on purpose.
 * The language row reads `data.parent.preferredLanguage` from DataContext,
 * which is provided by (tabs)/_layout.tsx -- a root-level screen would receive
 * the context's default `{role: null, data: {}}` silently, with no error and no
 * language. app/recognition.tsx already carries the scar from that.
 */
export default function SettingsScreen() {
  const { role, data } = useContext(DataContext);
  const { logout } = useAuth();
  const { theme: T, paletteId, palettes, setPaletteId } = useThemePicker();
  const insets = useSafeAreaInsets();
  const styles = useMemo(() => makeStyles(T), [T]);

  const [themePickerOpen, setThemePickerOpen] = useState(false);
  const [languages, setLanguages] = useState<any[]>([]);
  const [languagePickerOpen, setLanguagePickerOpen] = useState(false);
  const [savingLanguage, setSavingLanguage] = useState(false);
  // The server's value, unless the user has picked one this session. Derived
  // during render rather than copied into state by an effect, so a reload can
  // never briefly show the previous language.
  const [pickedLanguage, setPickedLanguage] = useState<string | null>(null);
  const preferredLanguage = pickedLanguage ?? data?.parent?.preferredLanguage ?? 'en';

  useEffect(() => {
    getSupportedLanguages().then(setLanguages).catch(() => setLanguages([]));
  }, []);

  const handlePickLanguage = async (code: string) => {
    setLanguagePickerOpen(false);
    if (code === preferredLanguage) return;
    setSavingLanguage(true);
    try {
      await setPreferredLanguage(code);
      setPickedLanguage(code);
    } catch {
      Alert.alert('Language', 'Could not save your language preference.');
    } finally {
      setSavingLanguage(false);
    }
  };

  const handleLogout = async () => {
    await logout();
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={{ paddingBottom: insets.bottom + 40 }}>
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Theme</Text>
        <TouchableOpacity style={styles.card} onPress={() => setThemePickerOpen(true)}>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Header colour</Text>
            <View style={styles.themeRowValue}>
              <View style={[styles.themeSwatch, { backgroundColor: T.brand }]} />
              <Text style={styles.detailValue}>
                {palettes.find((p) => p.id === paletteId)?.label ?? 'Default'}
              </Text>
            </View>
          </View>
        </TouchableOpacity>
      </View>

      {role === 'PARENT' && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Language</Text>
          <TouchableOpacity style={styles.card} onPress={() => setLanguagePickerOpen(true)} disabled={savingLanguage}>
            <View style={styles.detailRow}>
              <Text style={styles.detailLabel}>Announcements &amp; messages in</Text>
              {savingLanguage ? (
                <ActivityIndicator size="small" color={T.brand} />
              ) : (
                <Text style={styles.detailValue}>
                  {languages.find((l) => l.code === preferredLanguage)?.name ?? 'English'}
                </Text>
              )}
            </View>
          </TouchableOpacity>
        </View>
      )}

      <TouchableOpacity style={styles.logoutButton} onPress={handleLogout} activeOpacity={0.7}>
        <Text style={styles.logoutText}>Log Out</Text>
      </TouchableOpacity>

      <Modal visible={languagePickerOpen} transparent animationType="slide" onRequestClose={() => setLanguagePickerOpen(false)}>
        <View style={styles.modalOverlay}>
          {/* paddingBottom from the real inset: these sheets sit on the bottom
              edge, so on a device with a navigation bar the last row was drawn
              underneath it and could not be tapped. */}
          <View style={[styles.modalCard, { paddingBottom: 20 + insets.bottom }]}>
            <Text style={styles.modalTitle}>Choose Language</Text>
            <ScrollView style={{ maxHeight: 300 }}>
              {languages.map((l) => (
                <TouchableOpacity
                  key={l.code}
                  style={[styles.langRow, preferredLanguage === l.code && styles.langRowActive]}
                  onPress={() => handlePickLanguage(l.code)}
                >
                  <Text style={styles.langRowText}>{l.name}</Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
          </View>
        </View>
      </Modal>

      <Modal visible={themePickerOpen} transparent animationType="slide" onRequestClose={() => setThemePickerOpen(false)}>
        <View style={styles.modalOverlay}>
          <View style={[styles.modalCard, { paddingBottom: 20 + insets.bottom }]}>
            <Text style={styles.modalTitle}>Choose a theme</Text>
            <ScrollView style={{ maxHeight: 360 }}>
              {palettes.map((p) => (
                <TouchableOpacity
                  key={p.id}
                  style={[styles.themePickerRow, paletteId === p.id && styles.langRowActive]}
                  onPress={() => { setPaletteId(p.id); setThemePickerOpen(false); }}
                >
                  <View style={[styles.themeSwatch, styles.themeSwatchLg, { backgroundColor: p.brand }]}>
                    <View style={[styles.themeSwatchHalf, { backgroundColor: p.brand700 }]} />
                  </View>
                  <Text style={styles.langRowText}>{p.label}</Text>
                  {paletteId === p.id && <Text style={{ color: T.brand, fontWeight: '700' }}>✓</Text>}
                </TouchableOpacity>
              ))}
            </ScrollView>
          </View>
        </View>
      </Modal>
    </ScrollView>
  );
}

const makeStyles = (T: Theme) => StyleSheet.create({
  container: { flex: 1, backgroundColor: T.bg, padding: 20 },
  section: { marginBottom: 24 },
  sectionTitle: {
    fontSize: 12, fontWeight: '700', color: T.text3,
    letterSpacing: 0.9, textTransform: 'uppercase', marginBottom: 8,
  },
  card: {
    backgroundColor: T.surface, borderRadius: T.r,
    borderWidth: 1, borderColor: T.line, paddingHorizontal: 16,
  },
  detailRow: {
    flexDirection: 'row', justifyContent: 'space-between',
    alignItems: 'center', paddingVertical: 14,
  },
  detailLabel: { color: T.text3, fontSize: 14, flex: 1 },
  detailValue: { color: T.text, fontSize: 14, fontWeight: '600' },
  themeRowValue: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  themeSwatch: { width: 16, height: 16, borderRadius: 5 },
  themeSwatchLg: { width: 28, height: 28, borderRadius: 8, overflow: 'hidden' },
  // A tiny diagonal split so the picker previews the actual two-stop gradient
  // rather than only its first colour.
  themeSwatchHalf: { position: 'absolute', right: 0, bottom: 0, width: 14, height: 14 },
  themePickerRow: {
    flexDirection: 'row', alignItems: 'center', gap: 12,
    padding: 12, borderRadius: 10, marginBottom: 4,
  },
  logoutButton: {
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.lineStrong,
    borderRadius: T.rSm, paddingVertical: 14, alignItems: 'center',
  },
  logoutText: { color: T.dangerInk, fontSize: 15, fontWeight: '700' },
  modalOverlay: { flex: 1, backgroundColor: T.scrim, justifyContent: 'flex-end' },
  modalCard: {
    backgroundColor: T.surface, borderTopLeftRadius: 20,
    borderTopRightRadius: 20, padding: 20,
  },
  modalTitle: { fontSize: 16, fontWeight: '700', color: T.text, marginBottom: 12 },
  langRow: { padding: 12, borderRadius: 10, marginBottom: 4 },
  langRowActive: { backgroundColor: T.brand50 },
  langRowText: { color: T.text, fontSize: 14, flex: 1 },
});
