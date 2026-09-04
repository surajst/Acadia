import React, { useCallback, useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TouchableOpacity, TextInput,
  ActivityIndicator, RefreshControl, Alert, Linking,
} from 'react-native';
import { useLocalSearchParams, useRouter, Stack } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { getStudentProfile, getBadges, awardBadge } from '../../services/api';
import T from '../../constants/theme';

/**
 * One child, on a teacher's phone.
 *
 * This screen exists for two moments that happen away from a desk. Someone is
 * at the gate to collect a child and the teacher needs to know whether they are
 * allowed to. And a child does something worth telling their parent about,
 * which is worth recording while it is still true rather than at the end of the
 * day when it has blurred into everything else.
 *
 * Care and safety therefore sits above everything, before attendance or XP.
 */

type Award = {
  id: string; label: string; emoji: string; points: number;
  reason: string; awardedByName: string; createdAt: string;
};
type Badge = { code: string; label: string; emoji: string; points: number; suggestion: string };

const day = (iso?: string | null) => {
  if (!iso) return '';
  const d = new Date(iso);
  return isNaN(d.getTime()) ? '' : d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
};

export default function StudentProfileScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();

  const [profile, setProfile] = useState<any>(null);
  const [badges, setBadges] = useState<Badge[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [picked, setPicked] = useState<Badge | null>(null);
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    const [p, b] = await Promise.all([
      getStudentProfile(id),
      badges.length ? Promise.resolve(badges) : getBadges(),
    ]);
    setProfile(p);
    setBadges(b);
  }, [id, badges.length]);

  useEffect(() => {
    (async () => {
      try { await load(); }
      catch (e: any) { Alert.alert('Could not load', e?.response?.data?.error ?? 'Please try again.'); }
      finally { setLoading(false); }
    })();
  }, [id]);

  const onRefresh = async () => {
    setRefreshing(true);
    try { await load(); } catch { /* keep what is on screen */ }
    setRefreshing(false);
  };

  const submitAward = async () => {
    if (!picked || saving) return;
    setSaving(true);
    try {
      await awardBadge(id!, picked.code, reason.trim() || picked.suggestion);
      setPicked(null);
      setReason('');
      await load();
    } catch (e: any) {
      Alert.alert('Could not award', e?.response?.data?.error ?? 'Please try again.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <View style={s.centre}>
        <Stack.Screen options={{ title: 'Child' }} />
        <ActivityIndicator color={T.brand} />
      </View>
    );
  }

  if (!profile) {
    return (
      <View style={s.centre}>
        <Stack.Screen options={{ title: 'Child' }} />
        <Text style={s.muted}>This child could not be found.</Text>
        <TouchableOpacity onPress={() => router.back()} style={[s.btn, s.btnSecondary, { marginTop: 12 }]}>
          <Text style={s.btnSecondaryText}>Go back</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const name = `${profile.firstName ?? ''} ${profile.lastName ?? ''}`.trim();
  const pickups = profile.pickupContacts ?? [];
  const awards: Award[] = profile.awards ?? [];

  return (
    <ScrollView
      style={s.page}
      contentContainerStyle={s.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={T.brand} />}
    >
      <Stack.Screen options={{ title: name || 'Child' }} />

      {/* Identity */}
      <View style={s.card}>
        <View style={s.identityRow}>
          <View style={s.avatar}>
            <Text style={s.avatarText}>{(profile.firstName ?? '?').slice(0, 1)}</Text>
          </View>
          <View style={{ flex: 1, minWidth: 0 }}>
            <Text style={s.name}>{name}</Text>
            <Text style={s.sub}>
              {[profile.gradeName, profile.sectionName].filter(Boolean).join(' · ')}
              {profile.rollNumber ? `  ·  ${profile.rollNumber}` : ''}
            </Text>
          </View>
          {profile.ageYears != null && (
            <View style={s.badgeNeutral}><Text style={s.badgeNeutralText}>{profile.ageYears} yrs</Text></View>
          )}
        </View>
      </View>

      {/* Care & safety. First, because this is what the screen is for. */}
      <View style={s.card}>
        <Text style={s.cardTitle}>Care &amp; safety</Text>

        <Text style={s.label}>Allergies &amp; medical notes</Text>
        {profile.medicalNotes ? (
          <View style={s.alert}>
            <Text style={s.alertText}>{profile.medicalNotes}</Text>
          </View>
        ) : (
          <Text style={s.muted}>Nothing recorded — which is not the same as none.</Text>
        )}

        <Text style={[s.label, { marginTop: 14 }]}>Emergency contact</Text>
        {profile.emergencyContactName ? (
          <TouchableOpacity
            style={s.contactRow}
            onPress={() => profile.emergencyContactPhone &&
              Linking.openURL(`tel:${String(profile.emergencyContactPhone).replace(/\s/g, '')}`)}
          >
            <View style={{ flex: 1 }}>
              <Text style={s.contactName}>{profile.emergencyContactName}</Text>
              {!!profile.emergencyContactPhone && (
                <Text style={s.contactPhone}>{profile.emergencyContactPhone}</Text>
              )}
            </View>
            {!!profile.emergencyContactPhone && (
              <SymbolView name={{ ios: 'phone.fill', android: 'call', web: 'call' }} tintColor={T.success} size={20} />
            )}
          </TouchableOpacity>
        ) : (
          <Text style={s.muted}>No emergency contact on file.</Text>
        )}

        <Text style={[s.label, { marginTop: 14 }]}>
          Authorised to collect ({pickups.length})
        </Text>
        {pickups.length ? (
          pickups.map((p: any, i: number) => (
            <View key={p.id ?? i} style={s.pickupRow}>
              <SymbolView name={{ ios: 'checkmark.seal.fill', android: 'verified', web: 'verified' }}
                          tintColor={T.success} size={18} />
              <View style={{ flex: 1 }}>
                <Text style={s.contactName}>{p.name}</Text>
                <Text style={s.contactPhone}>
                  {[p.relationship, p.phone].filter(Boolean).join(' · ')}
                </Text>
              </View>
            </View>
          ))
        ) : (
          <Text style={s.muted}>Nobody besides the guardians on file.</Text>
        )}
      </View>

      {/* Recognition */}
      <View style={s.card}>
        <View style={s.rowBetween}>
          <Text style={s.cardTitle}>Recognition</Text>
          <View style={s.badgeBrand}><Text style={s.badgeBrandText}>{profile.schoolXp ?? 0} XP</Text></View>
        </View>

        <Text style={[s.label, { marginTop: 4 }]}>Award something</Text>
        <View style={s.badgeWrap}>
          {badges.map((b) => {
            const on = picked?.code === b.code;
            return (
              <TouchableOpacity
                key={b.code}
                onPress={() => setPicked(on ? null : b)}
                style={[s.badgeChip, on && s.badgeChipOn]}
              >
                <Text style={[s.badgeChipText, on && s.badgeChipTextOn]}>
                  {b.emoji} {b.label} +{b.points}
                </Text>
              </TouchableOpacity>
            );
          })}
        </View>

        {/* The note only appears once a badge is chosen: asking what happened
            before knowing what is being recognised is the wrong order. */}
        {picked && (
          <View style={{ marginTop: 10 }}>
            <TextInput
              style={s.input}
              placeholder={picked.suggestion}
              placeholderTextColor={T.text3}
              value={reason}
              onChangeText={setReason}
              maxLength={500}
              multiline
            />
            <TouchableOpacity
              style={[s.btn, s.btnPrimary, saving && { opacity: 0.6 }]}
              onPress={submitAward}
              disabled={saving}
            >
              <Text style={s.btnPrimaryText}>
                {saving ? 'Saving…' : `Award ${picked.label}`}
              </Text>
            </TouchableOpacity>
          </View>
        )}

        {awards.length ? (
          <View style={{ marginTop: 14 }}>
            <Text style={s.label}>Recently recognised</Text>
            {awards.slice(0, 6).map((a, i) => (
              <View key={a.id ?? i} style={s.awardRow}>
                <Text style={s.awardEmoji}>{a.emoji}</Text>
                <View style={{ flex: 1 }}>
                  <Text style={s.awardLabel}>
                    {a.label} <Text style={s.awardPoints}>+{a.points}</Text>
                  </Text>
                  {!!a.reason && <Text style={s.awardReason}>{a.reason}</Text>}
                  <Text style={s.awardMeta}>
                    {[a.awardedByName, day(a.createdAt)].filter(Boolean).join(' · ')}
                  </Text>
                </View>
              </View>
            ))}
          </View>
        ) : (
          <Text style={[s.muted, { marginTop: 12 }]}>Nothing recognised yet — the first one means the most.</Text>
        )}
      </View>

      {/* Attendance, last: useful context, not the reason you opened this. */}
      <View style={s.card}>
        <Text style={s.cardTitle}>Attendance</Text>
        <View style={s.statRow}>
          <View style={s.stat}>
            <Text style={s.statLabel}>PRESENT</Text>
            <Text style={[s.statValue, { color: T.successInk }]}>{profile.presentCount ?? 0}</Text>
          </View>
          <View style={s.stat}>
            <Text style={s.statLabel}>ABSENT</Text>
            <Text style={[s.statValue, { color: T.danger }]}>{profile.absentCount ?? 0}</Text>
          </View>
          <View style={s.stat}>
            <Text style={s.statLabel}>RATE</Text>
            <Text style={s.statValue}>{profile.attendancePercentage ?? 0}%</Text>
          </View>
        </View>
      </View>
    </ScrollView>
  );
}

const s = StyleSheet.create({
  page: { flex: 1, backgroundColor: T.bg },
  content: { padding: 16, paddingBottom: 32, gap: 14 },
  centre: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: T.bg, padding: 24 },

  card: { ...T.card, padding: 16 },
  cardTitle: { fontSize: 15, fontWeight: '700', color: T.text, marginBottom: 10 },
  label: { fontSize: 11.5, fontWeight: '600', letterSpacing: 0.6, textTransform: 'uppercase', color: T.text3, marginBottom: 6 },
  muted: { fontSize: 13, color: T.text3 },
  rowBetween: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },

  identityRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  avatar: { width: 44, height: 44, borderRadius: 12, backgroundColor: T.brand50, alignItems: 'center', justifyContent: 'center' },
  avatarText: { fontSize: 18, fontWeight: '700', color: T.brand },
  name: { fontSize: 17, fontWeight: '700', color: T.text },
  sub: { fontSize: 12.5, color: T.text3, marginTop: 2 },

  badgeNeutral: { backgroundColor: T.surface2, borderWidth: 1, borderColor: T.lineStrong, borderRadius: T.pill, paddingHorizontal: 9, paddingVertical: 3 },
  badgeNeutralText: { fontSize: 10.5, fontWeight: '700', color: T.text2 },
  badgeBrand: { backgroundColor: T.brand50, borderWidth: 1, borderColor: T.brand100, borderRadius: T.pill, paddingHorizontal: 9, paddingVertical: 3 },
  badgeBrandText: { fontSize: 10.5, fontWeight: '700', color: T.brand },

  alert: { backgroundColor: T.danger50, borderWidth: 1, borderColor: T.danger200, borderRadius: T.rXs, padding: 10 },
  alertText: { fontSize: 13.5, color: '#991B1B', fontWeight: '600' },

  contactRow: { flexDirection: 'row', alignItems: 'center', gap: 10, backgroundColor: T.surface2, borderWidth: 1, borderColor: T.line, borderRadius: T.rXs, padding: 10, minHeight: 44 },
  contactName: { fontSize: 13.5, fontWeight: '600', color: T.text },
  contactPhone: { fontSize: 11.5, color: T.text3, marginTop: 1 },
  pickupRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: T.line },

  badgeWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  badgeChip: { backgroundColor: T.surface2, borderWidth: 1, borderColor: T.lineStrong, borderRadius: T.pill, paddingHorizontal: 11, paddingVertical: 8 },
  badgeChipOn: { backgroundColor: T.brand, borderColor: T.brand },
  badgeChipText: { fontSize: 12.5, fontWeight: '600', color: T.text2 },
  badgeChipTextOn: { color: T.onBrand },

  input: { backgroundColor: T.surface2, borderWidth: 1, borderColor: T.lineStrong, borderRadius: T.rXs, padding: 11, fontSize: 14.5, color: T.text, minHeight: 44 },

  btn: { borderRadius: T.rXs, alignItems: 'center', justifyContent: 'center', minHeight: 46, marginTop: 10 },
  btnPrimary: { backgroundColor: T.brand },
  btnPrimaryText: { color: T.onBrand, fontWeight: '700', fontSize: 14 },
  btnSecondary: { backgroundColor: T.surface2, borderWidth: 1, borderColor: T.lineStrong, paddingHorizontal: 18 },
  btnSecondaryText: { color: T.text, fontWeight: '600', fontSize: 14 },

  awardRow: { flexDirection: 'row', gap: 10, paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: T.line },
  awardEmoji: { fontSize: 18 },
  awardLabel: { fontSize: 13.5, fontWeight: '600', color: T.text },
  awardPoints: { color: T.brand, fontWeight: '700' },
  awardReason: { fontSize: 12.5, color: T.text2, marginTop: 2 },
  awardMeta: { fontSize: 11, color: T.text3, marginTop: 2 },

  statRow: { flexDirection: 'row', gap: 10 },
  stat: { flex: 1, backgroundColor: T.surface2, borderWidth: 1, borderColor: T.line, borderRadius: T.rXs, padding: 11 },
  statLabel: { fontSize: 9.5, fontWeight: '700', letterSpacing: 0.8, color: T.text3 },
  statValue: { fontSize: 20, fontWeight: '800', color: T.text, marginTop: 3 },
});
