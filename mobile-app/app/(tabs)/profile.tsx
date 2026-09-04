import { View, Text, StyleSheet, TouchableOpacity, ScrollView, ActivityIndicator, TextInput, Alert, Modal } from 'react-native';
import { useContext, useEffect, useState } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { useAuth } from '@/context/AuthContext';
import { DataContext } from './_layout';
import { getUserProfile, getSupportedLanguages, setPreferredLanguage, type UserProfile, type UserRole } from '../../services/api';
import T from '../../constants/theme';

function isUserRole(value: string | null): value is UserRole {
  return value === 'STUDENT' || value === 'PARENT' || value === 'TEACHER';
}

export default function ProfileScreen() {
  const { role, data } = useContext(DataContext);
  const { logout, firstName: authFirstName, lastName: authLastName } = useAuth();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [editing, setEditing] = useState(false);
  const [editFirst, setEditFirst] = useState('');
  const [editLast, setEditLast] = useState('');
  const [saving, setSaving] = useState(false);
  const [languages, setLanguages] = useState<any[]>([]);
  // The server's value, unless the user has picked one this session. Derived
  // during render rather than copied into state by an effect: the copy only
  // caught up on the render *after* the dashboard refreshed, so a reload could
  // briefly show the previous language, and the two could disagree indefinitely
  // if the effect's dependencies ever stopped firing.
  const [pickedLanguage, setPickedLanguage] = useState<string | null>(null);
  const preferredLanguage = pickedLanguage ?? data?.parent?.preferredLanguage ?? 'en';
  const [languagePickerOpen, setLanguagePickerOpen] = useState(false);
  const [savingLanguage, setSavingLanguage] = useState(false);

  useEffect(() => {
    const loadProfile = async () => {
      try {
        const cachedFName = await AsyncStorage.getItem('userFirstName');
        const cachedLName = await AsyncStorage.getItem('userLastName');
        const cachedEmail = await AsyncStorage.getItem('userEmail');
        const cachedRole  = await AsyncStorage.getItem('userRole');

        if (cachedFName && isUserRole(cachedRole)) {
          setProfile({
            firstName: cachedFName,
            lastName: cachedLName ?? '',
            email: cachedEmail ?? '',
            role: cachedRole
          });
          setLoadingProfile(false);
        }

        const profileData = await getUserProfile();
        setProfile(profileData);

        await AsyncStorage.setItem('userFirstName', profileData.firstName);
        await AsyncStorage.setItem('userLastName', profileData.lastName);
        await AsyncStorage.setItem('userEmail', profileData.email);
      } catch (error) {
        console.log('Profile data link issue:', error);
      } finally {
        setLoadingProfile(false);
      }
    };
    loadProfile();
  }, []);

  useEffect(() => {
    if (role !== 'PARENT') return;
    getSupportedLanguages().then(setLanguages).catch(() => setLanguages([]));
  }, [role]);

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

  const handleLogout = async (): Promise<void> => {
    await logout();
  };

  const handleEditPress = () => {
    setEditFirst(firstName);
    setEditLast(lastName);
    setEditing(true);
  };

  const handleCancel = () => {
    setEditing(false);
  };

  const handleSave = async () => {
    if (!editFirst.trim()) {
      Alert.alert('Validation', 'First name cannot be empty.');
      return;
    }
    setSaving(true);
    try {
      // Save locally for now (no backend edit endpoint yet)
      await AsyncStorage.setItem('userFirstName', editFirst.trim());
      await AsyncStorage.setItem('userLastName', editLast.trim());
      setProfile(prev => prev ? { ...prev, firstName: editFirst.trim(), lastName: editLast.trim() } : prev);
      setEditing(false);
    } catch {
      Alert.alert('Error', 'Could not save changes.');
    } finally {
      setSaving(false);
    }
  };

  const userObj = role === 'PARENT' ? data.parent : data.student;
  const firstName = profile?.firstName || authFirstName || userObj?.firstName || 'Unknown';
  const lastName = profile?.lastName || authLastName || userObj?.lastName || 'User';

  const getAccountTypeLabel = () => {
    const activeRole = profile?.role || role;
    if (activeRole === 'TEACHER') return 'Educator Account';
    if (activeRole === 'PARENT') return 'Parent Account';
    return 'Student Account';
  };

  const isTeacher = (profile?.role || role) === 'TEACHER';

  if (loadingProfile) {
    return (
      <View style={[styles.container, { justifyContent: 'center' }]}>
        <ActivityIndicator size="large" color={T.brand} />
      </View>
    );
  }

  return (
    <ScrollView style={styles.container}>
      <View style={styles.header}>
        <View style={styles.avatar}>
          <Text style={styles.avatarText}>{firstName.charAt(0)}</Text>
        </View>
        <Text style={styles.name}>{firstName} {lastName}</Text>
        <Text style={styles.roleText}>{getAccountTypeLabel()}</Text>
      </View>

      <View style={styles.section}>
        <View style={styles.sectionHeaderRow}>
          <Text style={styles.sectionTitle}>Account Details</Text>
          {isTeacher && !editing && (
            <TouchableOpacity onPress={handleEditPress} style={styles.editBtn}>
              <Text style={styles.editBtnText}>Edit</Text>
            </TouchableOpacity>
          )}
        </View>

        <View style={styles.card}>
          {editing ? (
            <>
              <View style={styles.editRow}>
                <Text style={styles.detailLabel}>First Name</Text>
                <TextInput
                  style={styles.editInput}
                  value={editFirst}
                  onChangeText={setEditFirst}
                  placeholderTextColor={T.text2}
                  autoFocus
                />
              </View>
              <View style={styles.divider} />
              <View style={styles.editRow}>
                <Text style={styles.detailLabel}>Last Name</Text>
                <TextInput
                  style={styles.editInput}
                  value={editLast}
                  onChangeText={setEditLast}
                  placeholderTextColor={T.text2}
                />
              </View>
              {profile?.email && (
                <>
                  <View style={styles.divider} />
                  <View style={styles.detailRow}>
                    <Text style={styles.detailLabel}>Email</Text>
                    <Text style={styles.detailValue}>{profile.email}</Text>
                  </View>
                </>
              )}
              <View style={styles.divider} />
              <View style={styles.editActions}>
                <TouchableOpacity style={styles.cancelBtn} onPress={handleCancel}>
                  <Text style={styles.cancelBtnText}>Cancel</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.saveBtn} onPress={handleSave} disabled={saving}>
                  {saving ? <ActivityIndicator color={T.surface} size="small" /> : <Text style={styles.saveBtnText}>Save</Text>}
                </TouchableOpacity>
              </View>
            </>
          ) : (
            <>
              <View style={styles.detailRow}>
                <Text style={styles.detailLabel}>First Name</Text>
                <Text style={styles.detailValue}>{firstName}</Text>
              </View>
              <View style={styles.divider} />
              <View style={styles.detailRow}>
                <Text style={styles.detailLabel}>Last Name</Text>
                <Text style={styles.detailValue}>{lastName}</Text>
              </View>
              {(profile?.role === 'STUDENT' || (!profile && role === 'STUDENT')) && (
                <>
                  <View style={styles.divider} />
                  <View style={styles.detailRow}>
                    <Text style={styles.detailLabel}>Class & Section</Text>
                    <Text style={styles.detailValue}>{data.student?.gradeName || 'Unknown'} - {data.student?.sectionName || 'Section'}</Text>
                  </View>
                </>
              )}
              {profile?.email && (
                <>
                  <View style={styles.divider} />
                  <View style={styles.detailRow}>
                    <Text style={styles.detailLabel}>Email</Text>
                    <Text style={styles.detailValue}>{profile.email}</Text>
                  </View>
                </>
              )}
            </>
          )}
        </View>
      </View>

      {role === 'PARENT' && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Language</Text>
          <TouchableOpacity style={styles.card} onPress={() => setLanguagePickerOpen(true)} disabled={savingLanguage}>
            <View style={styles.detailRow}>
              <Text style={styles.detailLabel}>Announcements & messages in</Text>
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

      <View style={{ height: 40 }} />

      <Modal visible={languagePickerOpen} transparent animationType="slide" onRequestClose={() => setLanguagePickerOpen(false)}>
        <View style={styles.modalOverlay}>
          <View style={styles.modalCard}>
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
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: T.bg, padding: 16 },
  header: { alignItems: 'center', marginVertical: 32 },
  avatar: {
    width: 80, height: 80, borderRadius: 40,
    backgroundColor: T.brand,
    justifyContent: 'center', alignItems: 'center', marginBottom: 16,
  },
  avatarText: { color: T.text, fontSize: 32, fontWeight: 'bold' },
  name: { color: T.text, fontSize: 24, fontWeight: 'bold', marginBottom: 4 },
  roleText: { color: T.text3, fontSize: 16 },
  section: { marginBottom: 32 },
  sectionHeaderRow: {
    flexDirection: 'row', justifyContent: 'space-between',
    alignItems: 'center', marginBottom: 12, marginHorizontal: 4,
  },
  sectionTitle: {
    color: T.text3, fontSize: 14, fontWeight: 'bold',
    textTransform: 'uppercase',
  },
  editBtn: {
    backgroundColor: T.brand50, borderRadius: 8,
    paddingHorizontal: 12, paddingVertical: 4,
    borderWidth: 1, borderColor: T.brand100,
  },
  editBtnText: { color: T.brand, fontSize: 13, fontWeight: '600' },
  card: { backgroundColor: T.surface, borderRadius: 16, overflow: 'hidden' },
  detailRow: { flexDirection: 'row', justifyContent: 'space-between', padding: 16 },
  detailLabel: { color: T.text3, fontSize: 16 },
  detailValue: { color: T.text, fontSize: 16, fontWeight: '500' },
  editRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16 },
  editInput: {
    color: T.text, fontSize: 15, fontWeight: '500',
    borderBottomWidth: 1, borderBottomColor: T.brand,
    minWidth: 160, textAlign: 'right', paddingBottom: 2,
  },
  editActions: {
    flexDirection: 'row', gap: 10,
    padding: 16,
  },
  cancelBtn: {
    flex: 1, padding: 12, borderRadius: 10,
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.line,
    alignItems: 'center',
  },
  cancelBtnText: { color: T.text3, fontWeight: '600' },
  saveBtn: {
    flex: 1, padding: 12, borderRadius: 10,
    backgroundColor: T.brand, alignItems: 'center',
  },
  saveBtnText: { color: T.surface, fontWeight: '600' },
  divider: { height: 1, backgroundColor: T.line, marginLeft: 16 },
  logoutButton: {
    backgroundColor: T.danger50, borderWidth: 1, borderColor: T.danger200,
    padding: 16, borderRadius: 12,
    alignItems: 'center', marginTop: 16,
  },
  logoutText: { color: T.dangerInk, fontSize: 18, fontWeight: 'bold' },
  modalOverlay: { flex: 1, backgroundColor: T.scrim, justifyContent: 'flex-end' },
  modalCard: { backgroundColor: T.surface, borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20 },
  modalTitle: { fontSize: 16, fontWeight: '700', color: T.text, marginBottom: 12 },
  langRow: { padding: 12, borderRadius: 10, marginBottom: 4 },
  langRowActive: { backgroundColor: T.brand50 },
  langRowText: { color: T.text, fontSize: 14 },
});