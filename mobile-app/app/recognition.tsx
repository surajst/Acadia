import React, { useContext, useEffect, useMemo, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, RefreshControl } from 'react-native';
import { Stack } from 'expo-router';
import { DataContext } from './(tabs)/_layout';
import { getParentDashboard } from '../services/api';
import T from '../constants/theme';

/**
 * Everything a child has been recognised for.
 *
 * The home card shows the three most recent, which answers "anything new
 * today". This is the other question a parent asks, usually at a parents'
 * evening or after a hard week: what has my child actually been noticed for.
 *
 * Grouped by day rather than listed flat. "Tuesday: kind hands, great
 * listening" is a story about a day; the same two lines with full timestamps
 * are a database export.
 */

type Award = {
  id: string; label: string; emoji: string; points: number;
  reason: string; awardedByName: string | null; createdAt: string;
};

const dayKey = (iso: string) => {
  const d = new Date(iso);
  return isNaN(d.getTime()) ? '' : d.toDateString();
};

const dayLabel = (iso: string) => {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  const today = new Date();
  const yesterday = new Date(); yesterday.setDate(today.getDate() - 1);
  if (d.toDateString() === today.toDateString()) return 'Today';
  if (d.toDateString() === yesterday.toDateString()) return 'Yesterday';
  return d.toLocaleDateString('en-IN', { weekday: 'long', day: '2-digit', month: 'short' });
};

export default function RecognitionScreen() {
  const ctx = useContext(DataContext);
  const [refreshing, setRefreshing] = useState(false);
  // Own copy, for when this screen is reached directly rather than from the
  // home card: it lives outside the tab group, so on a deep link or a page
  // refresh the tab layout has not mounted and its context is empty. Reading
  // only the context meant a bookmarked link showed a child with no history.
  const [own, setOwn] = useState<any>(null);

  const data = (ctx?.data && Object.keys(ctx.data).length ? ctx.data : own) ?? {};

  const load = async () => {
    const fresh = await getParentDashboard();
    setOwn(fresh);
  };

  useEffect(() => {
    if (!ctx?.data || !Object.keys(ctx.data).length) {
      load().catch(() => setOwn({}));
    }
  }, [ctx?.data]);

  const refreshData = ctx?.refreshData ?? load;

  const awards: Award[] = Array.isArray(data.awards) ? data.awards : [];
  // Two forms because the name appears both at the start of a sentence and
  // inside one, and "recognises Your child" reads as a mistake.
  const childName = data.student?.firstName ?? 'Your child';
  const childRef = data.student?.firstName ?? 'your child';
  const totalXp = data.metrics?.schoolXp ?? 0;

  const groups = useMemo(() => {
    const byDay = new Map<string, Award[]>();
    for (const a of awards) {
      const k = dayKey(a.createdAt);
      if (!byDay.has(k)) byDay.set(k, []);
      byDay.get(k)!.push(a);
    }
    return Array.from(byDay.entries());
  }, [awards]);

  const onRefresh = async () => {
    setRefreshing(true);
    try { await refreshData(); await load(); } catch { /* keep what is on screen */ }
    finally { setRefreshing(false); }
  };

  return (
    <ScrollView
      style={s.page}
      contentContainerStyle={s.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={T.brand} />}
    >
      <Stack.Screen options={{ title: 'Recognition' }} />

      <View style={s.hero}>
        <Text style={s.heroLabel}>SCHOOL XP</Text>
        <Text style={s.heroValue}>{totalXp}</Text>
        <Text style={s.heroNote}>
          {awards.length === 0
            ? `${childName} has not been recognised yet`
            : `From ${awards.length} ${awards.length === 1 ? 'moment' : 'moments'} their teachers noticed`}
        </Text>
      </View>

      {groups.length === 0 ? (
        <View style={s.card}>
          <Text style={s.emptyTitle}>Nothing yet</Text>
          <Text style={s.emptyBody}>
            When a teacher recognises {childRef} for something — kindness, effort, a good question —
            it will appear here with what they did and who noticed.
          </Text>
        </View>
      ) : (
        groups.map(([key, items]) => (
          <View key={key} style={s.card}>
            <Text style={s.dayLabel}>{dayLabel(items[0].createdAt)}</Text>
            {items.map((a, i) => (
              <View key={a.id ?? i} style={[s.row, i === items.length - 1 && s.rowLast]}>
                <Text style={s.emoji}>{a.emoji || '🏅'}</Text>
                <View style={{ flex: 1 }}>
                  <Text style={s.label}>
                    {a.label} <Text style={s.points}>+{a.points}</Text>
                  </Text>
                  {!!a.reason && <Text style={s.reason}>{a.reason}</Text>}
                  {!!a.awardedByName && <Text style={s.meta}>{a.awardedByName}</Text>}
                </View>
              </View>
            ))}
          </View>
        ))
      )}

      <Text style={s.footnote}>
        Recognition is recorded by the school. If something looks wrong, mention it to your
        child&apos;s teacher.
      </Text>
    </ScrollView>
  );
}

const s = StyleSheet.create({
  page: { flex: 1, backgroundColor: T.bg },
  content: { padding: 16, paddingBottom: 32, gap: 14 },

  hero: { ...T.card, backgroundColor: T.brand, borderColor: T.brand, padding: 18 },
  heroLabel: { fontSize: 11, fontWeight: '700', letterSpacing: 0.8, color: T.onBrandFaint },
  heroValue: { fontSize: 38, fontWeight: '800', color: T.onBrand, letterSpacing: -1, marginTop: 6, lineHeight: 40 },
  heroNote: { fontSize: 12.5, color: T.onBrandMuted, marginTop: 6 },

  card: { ...T.card, padding: 16 },
  dayLabel: { fontSize: 11.5, fontWeight: '700', letterSpacing: 0.6, textTransform: 'uppercase', color: T.text3, marginBottom: 8 },

  row: { flexDirection: 'row', gap: 10, paddingVertical: 9, borderBottomWidth: 1, borderBottomColor: T.line },
  rowLast: { borderBottomWidth: 0, paddingBottom: 0 },
  emoji: { fontSize: 20, lineHeight: 24 },
  label: { fontSize: 14, fontWeight: '600', color: T.text },
  points: { color: T.brand, fontWeight: '700' },
  reason: { fontSize: 13, color: T.text2, marginTop: 2, lineHeight: 18 },
  meta: { fontSize: 11.5, color: T.text3, marginTop: 3 },

  emptyTitle: { fontSize: 15, fontWeight: '700', color: T.text, marginBottom: 4 },
  emptyBody: { fontSize: 13.5, color: T.text3, lineHeight: 20 },

  footnote: { fontSize: 11, color: T.text3, textAlign: 'center' },
});
