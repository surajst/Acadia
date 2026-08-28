import React, { useContext, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, RefreshControl } from 'react-native';
import { SymbolView } from 'expo-symbols';
import { DataContext } from './_layout';

/**
 * What this family owes the school, and what they have already paid.
 *
 * Reads the `fees` block the parent dashboard endpoint returns for the
 * currently selected child. The backend sums it (see StudentFeeSummaryService)
 * so this screen and the web portal cannot disagree about a number a parent
 * might bring to the office.
 *
 * `fees` is null when nothing has been billed yet -- that shows an explicit
 * "no fees raised" state rather than a card full of zeroes, because "nothing
 * billed" and "billed and settled" mean different things to a parent.
 */

const money = (n: any) => {
  const v = Number(n ?? 0);
  return '₹ ' + v.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
};

const day = (iso?: string | null) => {
  if (!iso) return '';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return String(iso);
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
};

export default function FeesScreen() {
  const { data, refreshData } = useContext(DataContext);
  const [refreshing, setRefreshing] = useState(false);

  const fees = data?.fees;
  const childName = data?.student?.firstName ?? 'Your child';

  const onRefresh = async () => {
    setRefreshing(true);
    try {
      await refreshData();
    } finally {
      setRefreshing(false);
    }
  };

  const refresh = <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />;

  if (!fees) {
    return (
      <ScrollView style={s.page} contentContainerStyle={s.emptyWrap} refreshControl={refresh}>
        <SymbolView name={{ ios: 'doc.text', android: 'receipt', web: 'receipt' }} tintColor="#94A3B8" size={44} />
        <Text style={s.emptyTitle}>No fees raised yet</Text>
        <Text style={s.emptyBody}>
          The school has not billed anything for {childName} so far. Anything raised will appear here.
        </Text>
      </ScrollView>
    );
  }

  const settled = Number(fees.totalDue ?? 0) <= 0;
  const overdue = Number(fees.overdueCount ?? 0) > 0;
  const tone = settled ? TONE.settled : overdue ? TONE.overdue : TONE.due;

  return (
    <ScrollView style={s.page} contentContainerStyle={s.content} refreshControl={refresh}>

      <View style={[s.hero, { backgroundColor: tone.bg, borderColor: tone.border }]}>
        <View style={s.heroTop}>
          <Text style={s.eyebrow}>SCHOOL FEES</Text>
          {overdue ? (
            <View style={s.overduePill}>
              <Text style={s.overduePillText}>
                {fees.overdueCount} {fees.overdueCount === 1 ? 'instalment overdue' : 'instalments overdue'}
              </Text>
            </View>
          ) : null}
        </View>

        {settled ? (
          <Text style={[s.heroAmount, { color: tone.ink, fontSize: 20 }]}>All paid — nothing due</Text>
        ) : (
          <>
            <Text style={[s.heroAmount, { color: tone.ink }]}>{money(fees.totalDue)}</Text>
            {fees.nextDueLabel ? (
              <Text style={s.heroNext}>
                Next: {fees.nextDueLabel}
                {fees.nextDueAmount != null ? ` · ${money(fees.nextDueAmount)}` : ''}
                {fees.nextDueDate ? ` · due ${day(fees.nextDueDate)}` : ''}
              </Text>
            ) : null}
          </>
        )}

        <Text style={s.heroProgress}>
          {fees.paidCount} of {fees.instalmentCount} instalments paid · {money(fees.totalPaid)} of {money(fees.totalBilled)}
        </Text>
      </View>

      {fees.dues?.length ? (
        <View style={s.card}>
          <Text style={s.cardTitle}>Still due</Text>
          {fees.dues.map((d: any, i: number) => (
            <View key={`due-${i}`} style={[s.row, i === fees.dues.length - 1 && s.rowLast]}>
              <View style={s.rowLeft}>
                <Text style={s.rowLabel}>{d.label || 'Instalment'}</Text>
                <Text style={[s.rowMeta, d.overdue && s.rowMetaOverdue]}>
                  {d.dueDate ? `${d.overdue ? 'overdue' : 'due'} ${day(d.dueDate)}` : ''}
                </Text>
              </View>
              <Text style={[s.rowAmount, d.overdue && { color: '#B91C1C' }]}>{money(d.amount)}</Text>
            </View>
          ))}
        </View>
      ) : null}

      <View style={s.card}>
        <Text style={s.cardTitle}>Payment history</Text>
        {fees.payments?.length ? (
          fees.payments.map((p: any, i: number) => (
            <View key={`pmt-${i}`} style={[s.row, i === fees.payments.length - 1 && s.rowLast]}>
              <View style={s.rowLeft}>
                <Text style={[s.rowLabel, p.reversal && { color: '#B91C1C' }]}>
                  {p.reversal ? 'Reversed' : p.receiptNumber != null ? `Receipt #${p.receiptNumber}` : 'Payment'}
                </Text>
                <Text style={s.rowMeta}>
                  {[p.label, day(p.paidOn), p.reversal ? null : p.mode].filter(Boolean).join(' · ')}
                </Text>
              </View>
              <Text style={[s.rowAmount, { color: p.reversal ? '#B91C1C' : '#047857' }]}>{money(p.amount)}</Text>
            </View>
          ))
        ) : (
          <Text style={s.emptyRow}>No payments received yet.</Text>
        )}
      </View>

      <Text style={s.footnote}>
        Showing what the school has recorded. If something looks wrong, speak to the school office.
      </Text>
    </ScrollView>
  );
}

const TONE = {
  settled: { bg: '#ECFDF5', border: '#A7F3D0', ink: '#047857' },
  overdue: { bg: '#FEF2F2', border: '#FECACA', ink: '#B91C1C' },
  due:     { bg: '#FFFBEB', border: '#FDE68A', ink: '#B45309' },
};

const s = StyleSheet.create({
  page: { flex: 1, backgroundColor: '#F4F6FB' },
  content: { padding: 16, paddingBottom: 32, gap: 14 },

  hero: { borderRadius: 16, borderWidth: 1, padding: 16 },
  heroTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 },
  eyebrow: { fontSize: 11, fontWeight: '700', letterSpacing: 0.8, color: '#64748B' },
  overduePill: { backgroundColor: '#DC2626', borderRadius: 999, paddingHorizontal: 8, paddingVertical: 3 },
  overduePillText: { color: '#fff', fontSize: 10, fontWeight: '700' },
  heroAmount: { fontSize: 30, fontWeight: '800', letterSpacing: -0.5 },
  heroNext: { fontSize: 12.5, color: '#475569', marginTop: 4 },
  heroProgress: { fontSize: 11.5, color: '#64748B', marginTop: 8 },

  card: { backgroundColor: '#fff', borderRadius: 16, borderWidth: 1, borderColor: '#E7EAF2', padding: 14 },
  cardTitle: { fontSize: 15, fontWeight: '700', color: '#0F172A', marginBottom: 8 },

  row: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingVertical: 9, borderBottomWidth: 1, borderBottomColor: '#EEF1F6', gap: 12,
  },
  rowLast: { borderBottomWidth: 0 },
  rowLeft: { flex: 1 },
  rowLabel: { fontSize: 13.5, fontWeight: '600', color: '#0F172A' },
  rowMeta: { fontSize: 11.5, color: '#64748B', marginTop: 2 },
  rowMetaOverdue: { color: '#B91C1C' },
  rowAmount: { fontSize: 14, fontWeight: '700', color: '#0F172A' },

  emptyRow: { fontSize: 13, color: '#64748B', paddingVertical: 6 },
  emptyWrap: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', padding: 32, gap: 10 },
  emptyTitle: { fontSize: 17, fontWeight: '700', color: '#0F172A' },
  emptyBody: { fontSize: 13.5, color: '#64748B', textAlign: 'center', lineHeight: 20 },

  footnote: { fontSize: 11, color: '#94A3B8', textAlign: 'center', marginTop: 2 },
});
