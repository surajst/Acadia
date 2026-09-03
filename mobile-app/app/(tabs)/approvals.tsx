import React, { useCallback, useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TouchableOpacity,
  ActivityIndicator, RefreshControl, Alert,
} from 'react-native';
import { Stack } from 'expo-router';
import {
  getPendingApprovals, decideApproval, getPendingStaff, decideStaff,
  getPendingWaivers, decideWaiver,
} from '../../services/api';
import T from '../../constants/theme';

/**
 * What is waiting on the principal.
 *
 * Principals were sent to the web dashboard for this. But the decisions here
 * are small and frequent -- approve a new teacher, sign off a fee change --
 * and the person making them spends their day walking around a school, not
 * sitting at a desk. Being unable to clear a queue from a corridor is why
 * queues sit for days.
 *
 * Two lists, one shape: something happened, someone asked, decide. The same
 * queue-row pattern the web console uses, so a principal who has seen one has
 * seen both.
 */

type Row = { id: string; title: string; sub: string; meta: string; kind: 'approval' | 'staff' | 'waiver' };

const when = (iso?: string) => {
  if (!iso) return '';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
};

export default function ApprovalsScreen() {
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    // Settled independently: a failure fetching staff should not blank the
    // approvals list a principal is halfway through clearing.
    const [approvals, staff, waivers] = await Promise.allSettled([
      getPendingApprovals(), getPendingStaff(), getPendingWaivers(),
    ]);

    const out: Row[] = [];
    if (approvals.status === 'fulfilled' && Array.isArray(approvals.value)) {
      for (const a of approvals.value) {
        out.push({
          id: a.requestId,
          title: a.summary || a.action || 'Approval request',
          sub: a.action ? String(a.action).replace(/_/g, ' ').toLowerCase() : '',
          meta: [a.requestedBy, when(a.requestedAt)].filter(Boolean).join(' · '),
          kind: 'approval',
        });
      }
    }
    if (staff.status === 'fulfilled' && Array.isArray(staff.value)) {
      for (const s of staff.value) {
        out.push({
          id: s.id,
          title: s.fullName || 'New staff member',
          sub: `${s.role || 'Staff'} — awaiting approval`,
          meta: s.email || '',
          kind: 'staff',
        });
      }
    }
    // Fee waivers are their own queue. A parent asking for help with a term
    // is exactly the kind of thing that should not wait for someone to open a
    // laptop, so it belongs on this screen alongside everything else.
    if (waivers.status === 'fulfilled' && Array.isArray(waivers.value)) {
      for (const w of waivers.value) {
        out.push({
          id: w.invoiceId,
          title: `Fee help for ${w.studentName ?? 'a child'}`,
          sub: w.waiverReason || 'No reason given',
          meta: w.waiverAmount != null ? `₹ ${Number(w.waiverAmount).toLocaleString('en-IN')} requested` : '',
          kind: 'waiver',
        });
      }
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

  const decide = async (row: Row, action: 'approve' | 'reject') => {
    setBusyId(row.id);
    try {
      if (row.kind === 'approval') await decideApproval(row.id, action);
      else if (row.kind === 'waiver') await decideWaiver(row.id, action);
      else await decideStaff(row.id, action);
      // Refetch rather than removing locally: approving a fee plan can change
      // what else is waiting, and a list that disagrees with the server is
      // worse than one that takes a moment.
      await load();
    } catch (e: any) {
      Alert.alert('Could not save', e?.response?.data?.error ?? 'Please try again.');
    } finally {
      setBusyId(null);
    }
  };

  if (loading) {
    return (
      <View style={s.centre}>
        <Stack.Screen options={{ title: 'Approvals' }} />
        <ActivityIndicator color={T.brand} />
      </View>
    );
  }

  return (
    <ScrollView
      style={s.page}
      contentContainerStyle={s.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={T.brand} />}
    >
      <Stack.Screen options={{ title: 'Approvals' }} />

      <View style={s.hero}>
        <Text style={s.heroLabel}>WAITING ON YOU</Text>
        <Text style={s.heroValue}>{rows.length}</Text>
        <Text style={s.heroNote}>
          {rows.length === 0 ? 'Nothing needs a decision' : 'Nothing happens until you decide'}
        </Text>
      </View>

      {rows.length === 0 ? (
        <View style={s.card}>
          <Text style={s.emptyTitle}>All clear</Text>
          <Text style={s.emptyBody}>
            New staff, fee plan changes, payment reversals and requests for fee help will appear here for your decision.
          </Text>
        </View>
      ) : (
        rows.map((r) => (
          <View key={`${r.kind}-${r.id}`} style={s.card}>
            <View style={s.rowTop}>
              <View style={[s.kindDot, r.kind === 'staff' ? s.kindStaff : r.kind === 'waiver' ? s.kindWaiver : s.kindApproval]} />
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text style={s.title}>{r.title}</Text>
                {!!r.sub && <Text style={s.sub}>{r.sub}</Text>}
                {!!r.meta && <Text style={s.meta}>{r.meta}</Text>}
              </View>
            </View>

            <View style={s.actions}>
              <TouchableOpacity
                style={[s.btn, s.btnReject, busyId === r.id && s.busy]}
                onPress={() => decide(r, 'reject')}
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
                  {busyId === r.id ? 'Saving…' : 'Approve'}
                </Text>
              </TouchableOpacity>
            </View>
          </View>
        ))
      )}

      <Text style={s.footnote}>
        Every decision is recorded against your name in the audit log.
      </Text>
    </ScrollView>
  );
}

const s = StyleSheet.create({
  page: { flex: 1, backgroundColor: T.bg },
  content: { padding: 16, paddingBottom: 32, gap: 14 },
  centre: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: T.bg },

  hero: { ...T.card, backgroundColor: T.brand, borderColor: T.brand, padding: 18 },
  heroLabel: { fontSize: 11, fontWeight: '700', letterSpacing: 0.8, color: T.onBrandFaint },
  heroValue: { fontSize: 38, fontWeight: '800', color: T.onBrand, letterSpacing: -1, marginTop: 6, lineHeight: 40 },
  heroNote: { fontSize: 12.5, color: T.onBrandMuted, marginTop: 6 },

  card: { ...T.card, padding: 16 },
  rowTop: { flexDirection: 'row', gap: 10 },
  kindDot: { width: 8, height: 8, borderRadius: 4, marginTop: 6 },
  kindApproval: { backgroundColor: T.warn },
  kindStaff: { backgroundColor: T.info },
  kindWaiver: { backgroundColor: T.danger },
  title: { fontSize: 14.5, fontWeight: '600', color: T.text, lineHeight: 20 },
  sub: { fontSize: 12.5, color: T.text2, marginTop: 2 },
  meta: { fontSize: 11.5, color: T.text4, marginTop: 3 },

  actions: { flexDirection: 'row', gap: 8, marginTop: 12 },
  btn: { flex: 1, minHeight: 44, borderRadius: T.rXs, alignItems: 'center', justifyContent: 'center', borderWidth: 1 },
  btnReject: { backgroundColor: T.surface, borderColor: T.lineStrong },
  btnRejectText: { fontSize: 13.5, fontWeight: '600', color: T.text2 },
  btnApprove: { backgroundColor: T.success50, borderColor: T.success200 },
  btnApproveText: { fontSize: 13.5, fontWeight: '700', color: T.success },
  busy: { opacity: 0.6 },

  emptyTitle: { fontSize: 15, fontWeight: '700', color: T.text, marginBottom: 4 },
  emptyBody: { fontSize: 13.5, color: T.text3, lineHeight: 20 },
  footnote: { fontSize: 11, color: T.text4, textAlign: 'center' },
});
