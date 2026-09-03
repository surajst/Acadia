import React, { useCallback, useEffect, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, RefreshControl, Alert } from 'react-native';
import { Stack } from 'expo-router';
import {
  getStudentDashboard,
  redeemSchoolReward,
  redeemParentReward,
} from '@/services/api';
import { ListSkeleton } from '@/components/ui/Skeleton';
import EmptyState from '@/components/ui/EmptyState';
import { Stat, StatRow } from '@/components/ui/Stat';
import T from '@/constants/theme';

/**
 * Spending XP. The catalogue and the redeem logic both already existed -- but
 * redeeming was a Thymeleaf form post answering with a redirect, so the phone
 * could show a child their XP total and give them no way to spend it.
 *
 * The two ledgers are shown apart because they are earned apart, but they are
 * not spent apart: a school reward costs school XP, while a parent reward is
 * charged against parent XP first and then school XP. This screen mirrors that
 * rather than inventing its own rule -- gating a parent reward on parent XP
 * alone disabled rewards the child could actually afford.
 */

type Item = { id: string; title?: string; description?: string; xpCost?: number; displayEmoji?: string; inventoryCount?: number };
type ParentItem = { id: string; rewardTitle?: string; xpCost?: number };

export default function MarketplaceScreen() {
  const [schoolXp, setSchoolXp] = useState(0);
  const [parentXp, setParentXp] = useState(0);
  const [items, setItems] = useState<Item[]>([]);
  const [parentItems, setParentItems] = useState<ParentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    const d: any = await getStudentDashboard();
    setSchoolXp(d?.metrics?.schoolXp ?? 0);
    setParentXp(d?.metrics?.parentXp ?? 0);
    setItems(Array.isArray(d?.rewardInventoryList) ? d.rewardInventoryList : []);
    setParentItems(Array.isArray(d?.availableParentRewards) ? d.availableParentRewards : []);
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

  const spend = async (id: string, kind: 'school' | 'parent', title: string, cost: number) => {
    setBusyId(id);
    try {
      if (kind === 'school') await redeemSchoolReward(id);
      else await redeemParentReward(id);
      // Refetch: the balance and what is still affordable both move.
      await load();
      Alert.alert('Redeemed', `${title} is yours. ${cost} XP spent.`);
    } catch (e: any) {
      Alert.alert('Could not redeem', e?.response?.data?.error ?? 'Please try again.');
    } finally {
      setBusyId(null);
    }
  };

  if (loading) {
    return (
      <View style={[s.page, s.content]}>
        <Stack.Screen options={{ title: 'Rewards' }} />
        <ListSkeleton rows={4} />
      </View>
    );
  }

  const nothingToSpendOn = items.length === 0 && parentItems.length === 0;

  return (
    <ScrollView
      style={s.page}
      contentContainerStyle={s.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={T.brand} />}
    >
      <Stack.Screen options={{ title: 'Rewards' }} />

      <StatRow>
        <Stat label="School XP" value={schoolXp} />
        <Stat label="Parent XP" value={parentXp} />
      </StatRow>

      {nothingToSpendOn ? (
        <EmptyState
          icon={{ ios: 'gift', android: 'redeem', web: 'redeem' }}
          title="Nothing to spend on yet"
          body="When your school adds rewards — or a parent sets one aside for you — they show up here with what they cost."
        />
      ) : (
        <>
          {items.length > 0 && (
            <View style={s.group}>
              <Text style={s.groupTitle}>From your school</Text>
              {items.map((it) => {
                const cost = it.xpCost ?? 0;
                const afford = schoolXp >= cost;
                const outOfStock = (it.inventoryCount ?? 0) <= 0;
                return (
                  <Row
                    key={it.id}
                    emoji={it.displayEmoji}
                    title={it.title || 'Reward'}
                    subtitle={outOfStock ? 'Out of stock' : it.description}
                    cost={cost}
                    disabled={!afford || outOfStock || busyId === it.id}
                    busy={busyId === it.id}
                    hint={!afford && !outOfStock ? `${cost - schoolXp} XP to go` : undefined}
                    onPress={() => spend(it.id, 'school', it.title || 'Reward', cost)}
                  />
                );
              })}
            </View>
          )}

          {parentItems.length > 0 && (
            <View style={s.group}>
              <Text style={s.groupTitle}>Set aside by your family</Text>
              {parentItems.map((it) => {
                const cost = it.xpCost ?? 0;
                // The server checks school + parent and spends parent XP
                // first, so gating on parent XP alone disabled rewards the
                // child could actually afford.
                const afford = schoolXp + parentXp >= cost;
                return (
                  <Row
                    key={it.id}
                    emoji="🎁"
                    title={it.rewardTitle || 'Reward'}
                    cost={cost}
                    disabled={!afford || busyId === it.id}
                    busy={busyId === it.id}
                    hint={!afford ? `${cost - (schoolXp + parentXp)} XP to go` : undefined}
                    onPress={() => spend(it.id, 'parent', it.rewardTitle || 'Reward', cost)}
                  />
                );
              })}
            </View>
          )}
        </>
      )}
    </ScrollView>
  );
}

function Row({ emoji, title, subtitle, cost, disabled, busy, hint, onPress }: {
  emoji?: string; title: string; subtitle?: string; cost: number;
  disabled?: boolean; busy?: boolean; hint?: string; onPress: () => void;
}) {
  return (
    <View style={s.card}>
      <View style={s.rowTop}>
        {!!emoji && <Text style={s.emoji}>{emoji}</Text>}
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={s.title}>{title}</Text>
          {!!subtitle && <Text style={s.sub}>{subtitle}</Text>}
        </View>
        <Text style={s.cost}>{cost} XP</Text>
      </View>

      <TouchableOpacity
        style={[s.btn, disabled && s.btnOff]}
        onPress={onPress}
        disabled={disabled}
        accessibilityRole="button"
        accessibilityLabel={`Redeem ${title} for ${cost} XP`}
      >
        <Text style={[s.btnText, disabled && s.btnTextOff]}>
          {busy ? 'Redeeming…' : hint ?? 'Redeem'}
        </Text>
      </TouchableOpacity>
    </View>
  );
}

const s = StyleSheet.create({
  page: { flex: 1, backgroundColor: T.bg },
  content: { padding: T.space.lg, paddingBottom: T.space.xxl, gap: T.space.md },

  group: { gap: T.space.sm },
  groupTitle: { ...T.type.overline, color: T.text3, textTransform: 'uppercase', marginBottom: T.space.xs },

  card: { ...T.card, padding: T.space.lg },
  rowTop: { flexDirection: 'row', alignItems: 'center', gap: T.space.md },
  emoji: { fontSize: 26 },
  title: { ...T.type.heading, color: T.text },
  sub: { ...T.type.caption, color: T.text3, marginTop: 1 },
  cost: { ...T.type.label, color: T.brand, fontVariant: ['tabular-nums'] },

  btn: {
    minHeight: 44, borderRadius: T.rXs, alignItems: 'center', justifyContent: 'center',
    backgroundColor: T.brand, marginTop: T.space.md,
  },
  btnOff: { backgroundColor: T.track },
  btnText: { ...T.type.label, color: T.onBrand },
  btnTextOff: { color: T.text3 },
});
