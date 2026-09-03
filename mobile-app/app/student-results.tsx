import React, { useCallback, useEffect, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, RefreshControl } from 'react-native';
import { Stack } from 'expo-router';
import { getStudentPerformance } from '@/services/api';
import { ListSkeleton } from '@/components/ui/Skeleton';
import EmptyState from '@/components/ui/EmptyState';
import T from '@/constants/theme';

/**
 * Marks per subject -- the same figures a parent already sees, for the child
 * they are about. A student could take the tests and never be shown how they
 * did unless a parent relayed it.
 *
 * The bar is there because a bare "72.4" answers less than it looks like it
 * does; against a full-width track it reads at a glance.
 */

type Row = { subjectCode?: string; averagePercentage?: number; trend?: number[] };

/** Bands, not a gradient: the colour says where this sits, or says nothing. */
function band(pct: number) {
  if (pct >= 75) return { fill: T.success, ink: T.successInk, label: 'Strong' };
  if (pct >= 50) return { fill: T.info, ink: T.infoInk, label: 'Steady' };
  return { fill: T.warn, ink: T.warnInk, label: 'Needs work' };
}

export default function StudentResultsScreen() {
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    setRows((await getStudentPerformance()) as Row[]);
  }, []);

  useEffect(() => {
    (async () => {
      try { await load(); } catch { /* empty state covers it */ }
      finally { setLoading(false); }
    })();
  }, [load]);

  const onRefresh = async () => {
    setRefreshing(true);
    try { await load(); } catch { /* keep what is on screen */ }
    setRefreshing(false);
  };

  if (loading) {
    return (
      <View style={[s.page, s.content]}>
        <Stack.Screen options={{ title: 'My Results' }} />
        <ListSkeleton rows={4} />
      </View>
    );
  }

  return (
    <ScrollView
      style={s.page}
      contentContainerStyle={s.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={T.brand} />}
    >
      <Stack.Screen options={{ title: 'My Results' }} />

      {rows.length === 0 ? (
        <EmptyState
          icon={{ ios: 'chart.bar', android: 'bar_chart', web: 'bar_chart' }}
          title="No marks yet"
          body="When your teachers enter scores for a test, each subject shows up here with your average."
        />
      ) : (
        rows.map((r, i) => {
          const pct = Math.max(0, Math.min(100, Math.round(r.averagePercentage ?? 0)));
          const b = band(pct);
          return (
            <View key={r.subjectCode ?? i} style={s.card}>
              <View style={s.head}>
                <Text style={s.subject}>{r.subjectCode || 'Subject'}</Text>
                <Text style={[s.pct, { color: b.ink }]}>{pct}%</Text>
              </View>
              <View style={s.track}>
                <View style={[s.fill, { width: `${pct}%`, backgroundColor: b.fill }]} />
              </View>
              {/* The band is written out as well as coloured -- colour alone is
                  not a signal every reader receives. */}
              <Text style={[s.band, { color: b.ink }]}>{b.label}</Text>
            </View>
          );
        })
      )}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  page: { flex: 1, backgroundColor: T.bg },
  content: { padding: T.space.lg, paddingBottom: T.space.xxl, gap: T.space.md },
  card: { ...T.card, padding: T.space.lg },
  head: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  subject: { ...T.type.heading, color: T.text, flex: 1, minWidth: 0 },
  pct: { ...T.type.metric, fontSize: 20, lineHeight: 24 },
  track: {
    height: 8, borderRadius: T.pill, backgroundColor: T.track,
    marginTop: T.space.md, overflow: 'hidden',
  },
  fill: { height: 8, borderRadius: T.pill },
  band: { ...T.type.caption, fontWeight: '600', marginTop: T.space.sm },
});
