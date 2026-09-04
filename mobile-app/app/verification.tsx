import React, { useCallback, useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TouchableOpacity,
  RefreshControl, Alert,
} from 'react-native';
import { Stack } from 'expo-router';
import { getTeacherQueue, decideMilestone, decideProgress } from '@/services/api';
import { ListSkeleton } from '@/components/ui/Skeleton';
import EmptyState from '@/components/ui/EmptyState';
import T from '@/constants/theme';

/**
 * What is waiting on the teacher: student milestone submissions asking for an
 * XP award, and syllabus topics marked complete waiting for sign-off. This is
 * the first thing a teacher sees on the web dashboard -- on the phone it did
 * not exist at all, so a teacher had to find a laptop to clear it.
 */

type Row = {
  id: string;
  title: string;
  sub: string;
  meta: string;
  kind: 'milestone' | 'progress';
};

const when = (iso?: string) => {
  if (!iso) return '';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
};

export default function VerificationScreen() {
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    const queue = await getTeacherQueue();

    const out: Row[] = [];
    for (const s of queue.pendingSubmissions ?? []) {
      out.push({
        id: s.id,
        title: `${s.studentName} · ${s.skillName}`,
        sub: `${s.xpBounty ?? 0} XP requested`,
        meta: when(s.submittedAt),
        kind: 'milestone',
      });
    }
    for (const p of queue.pendingProgress ?? []) {
      out.push({
        id: p.id,
        title: `${p.studentName} · ${p.topicName}`,
        sub: p.subjectName ?? '',
        meta: when(p.submittedAt),
        kind: 'progress',
      });
    }

    setRows(out);
  }, []);

  useEffect(() => {
    (async () => {
      try { await load(); }
      catch (e: any) { Alert.alert('Could not load', e?.response?.data?.error ?? 'Please try again.'); }
      finally { setLoading(false); }
    })();
  }, [load]);

  const onRefresh = async () => {
    setRefreshing(true);
    try { await load(); } catch { /* keep what is on screen */ }
    setRefreshing(false);
  };

  const confirmDecline = (row: Row) => {
    // Declining sends the student a "needs review" notice, and there is no
    // undo -- so it asks first, where awarding XP does not.
    Alert.alert(
      'Send this back?',
      `${row.title} will be returned to the student as needing more work.`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Send back', style: 'destructive', onPress: () => decide(row, 'reject') },
      ],
    );
  };

  const decide = async (row: Row, action: 'approve' | 'reject') => {
    setBusyId(row.id);
    try {
      if (row.kind === 'milestone') await decideMilestone(row.id, action);
      else await decideProgress(row.id, action);
      // Refetch rather than removing locally -- approving one submission can
      // change what else is pending for the same student.
      await load();
    } catch (e: any) {
      Alert.alert('Could not save', e?.response?.data?.error ?? 'Please try again.');
    } finally {
      setBusyId(null);
    }
  };

  if (loading) {
    return (
      <View style={[s.page, s.content]}>
        <Stack.Screen options={{ title: 'Verification Queue' }} />
        <View style={[s.hero, s.heroSkeleton]} />
        <ListSkeleton rows={3} />
      </View>
    );
  }

  return (
    <ScrollView
      style={s.page}
      contentContainerStyle={s.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={T.brand} />}
    >
      <Stack.Screen options={{ title: 'Verification Queue' }} />

      <View style={s.hero}>
        <Text style={s.heroLabel}>WAITING ON YOU</Text>
        <Text style={s.heroValue}>{rows.length}</Text>
        <Text style={s.heroNote}>
          {rows.length === 0 ? 'Nothing needs a decision' : 'Award or decline before it sits any longer'}
        </Text>
      </View>

      {rows.length === 0 ? (
        <EmptyState
          icon={{ ios: 'checkmark.seal', android: 'verified', web: 'verified' }}
          title="Queue cleared"
          body="Milestone submissions and syllabus completions waiting on your review appear here."
        />
      ) : (
        rows.map((r) => (
          <View key={`${r.kind}-${r.id}`} style={s.card}>
            <View style={s.rowTop}>
              <View style={[s.kindDot, r.kind === 'milestone' ? s.kindMilestone : s.kindProgress]} />
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text style={s.title}>{r.title}</Text>
                {!!r.sub && <Text style={s.sub}>{r.sub}</Text>}
                {!!r.meta && <Text style={s.meta}>{r.meta}</Text>}
              </View>
            </View>

            <View style={s.actions}>
              <TouchableOpacity
                style={[s.btn, s.btnReject, busyId === r.id && s.busy]}
                onPress={() => confirmDecline(r)}
                disabled={busyId === r.id}
              >
                <Text style={s.btnRejectText}>Decline</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[s.btn, s.btnApprove, busyId === r.id && s.busy]}
                onPress={() => decide(r, 'approve')}
                disabled={busyId === r.id}
              >
                <Text style={s.btnApproveText}>
                  {busyId === r.id ? 'Saving…' : r.kind === 'milestone' ? 'Award XP' : 'Approve'}
                </Text>
              </TouchableOpacity>
            </View>
          </View>
        ))
      )}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  page: { flex: 1, backgroundColor: T.bg },
  content: { padding: 16, paddingBottom: 32, gap: 14 },

  hero: { ...T.card, backgroundColor: T.brand, borderColor: T.brand, padding: 18 },
  heroSkeleton: { backgroundColor: T.track, borderColor: T.line, height: 104 },
  heroLabel: { fontSize: 11, fontWeight: '700', letterSpacing: 0.8, color: T.onBrandFaint },
  heroValue: { fontSize: 38, fontWeight: '800', color: T.onBrand, letterSpacing: -1, marginTop: 6, lineHeight: 40 },
  heroNote: { fontSize: 12.5, color: T.onBrandMuted, marginTop: 6 },

  card: { ...T.card, padding: 16 },
  rowTop: { flexDirection: 'row', gap: 10 },
  kindDot: { width: 8, height: 8, borderRadius: 4, marginTop: 6 },
  kindMilestone: { backgroundColor: T.warn },
  kindProgress: { backgroundColor: T.info },
  title: { fontSize: 14.5, fontWeight: '600', color: T.text, lineHeight: 20 },
  sub: { fontSize: 12.5, color: T.text2, marginTop: 2 },
  meta: { fontSize: 11.5, color: T.text3, marginTop: 3 },

  actions: { flexDirection: 'row', gap: 8, marginTop: 12 },
  btn: { flex: 1, minHeight: 44, borderRadius: T.rXs, alignItems: 'center', justifyContent: 'center', borderWidth: 1 },
  btnReject: { backgroundColor: T.surface, borderColor: T.lineStrong },
  btnRejectText: { fontSize: 13.5, fontWeight: '600', color: T.text2 },
  btnApprove: { backgroundColor: T.success50, borderColor: T.success200 },
  btnApproveText: { fontSize: 13.5, fontWeight: '700', color: T.successInk },
  busy: { opacity: 0.6 },

});
