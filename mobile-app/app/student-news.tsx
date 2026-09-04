import React, { useCallback, useEffect, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, RefreshControl } from 'react-native';
import { Stack } from 'expo-router';
import { getStudentAnnouncements } from '@/services/api';
import { ListSkeleton } from '@/components/ui/Skeleton';
import EmptyState from '@/components/ui/EmptyState';
import T from '@/constants/theme';

/**
 * School announcements, targeted the same way the parent feed is: this
 * student's own grade plus anything addressed to the whole school. Until now
 * announcements reached parents only, so a notice about tomorrow's sports day
 * never reached the people it was about.
 */

type Item = { id: string; title?: string; content?: string; createdAt?: string };

const when = (iso?: string) => {
  if (!iso) return '';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
};

export default function StudentNewsScreen() {
  const [rows, setRows] = useState<Item[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    setRows((await getStudentAnnouncements()) as Item[]);
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
        <Stack.Screen options={{ title: 'School News' }} />
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
      <Stack.Screen options={{ title: 'School News' }} />

      {rows.length === 0 ? (
        <EmptyState
          icon={{ ios: 'megaphone', android: 'campaign', web: 'campaign' }}
          title="Nothing announced yet"
          body="Notices your school sends to your class — or to the whole school — will appear here."
        />
      ) : (
        rows.map((a) => (
          <View key={a.id} style={s.card}>
            <Text style={s.title}>{a.title || 'Announcement'}</Text>
            {!!a.createdAt && <Text style={s.date}>{when(a.createdAt)}</Text>}
            {!!a.content && <Text style={s.body}>{a.content}</Text>}
          </View>
        ))
      )}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  page: { flex: 1, backgroundColor: T.bg },
  content: { padding: T.space.lg, paddingBottom: T.space.xxl, gap: T.space.md },
  card: { ...T.card, padding: T.space.lg },
  title: { ...T.type.heading, color: T.text },
  date: { ...T.type.caption, color: T.text3, marginTop: 2 },
  body: { ...T.type.body, color: T.text2, marginTop: T.space.sm },
});
