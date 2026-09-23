import { View, Text, StyleSheet, ScrollView, RefreshControl } from 'react-native';
import { useContext, useMemo, useState } from 'react';
import { DataContext } from './_layout';
import { useTheme, type Theme } from '../../context/ThemeContext';

interface ParentQuest {
  taskDescription: string;
  xpBounty: number;
  status: string;
}

interface ParentReward {
  rewardTitle: string;
  xpCost: number;
  status?: string;
}

/**
 * What an empty list looks like.
 *
 * <p>This screen used to render invented content in place of an empty state:
 * a pending "Clean your room" quest, an "Extra Screen Time" reward for
 * students and a redeemed "Ice Cream Trip" for parents. None of it had ever
 * been created, and because the student and parent branches carried different
 * fabrications, two families comparing notes would see two different fictions
 * presented as their own child's record.
 */
function EmptyCard({ title, subtitle }: { title: string; subtitle: string }) {
  const T = useTheme();
  const styles = useMemo(() => makeStyles(T), [T]);
  return (
    <View style={styles.emptyCard} accessibilityRole="text">
      <Text style={styles.emptyTitle}>{title}</Text>
      <Text style={styles.itemSubtitle}>{subtitle}</Text>
    </View>
  );
}

export default function QuestsScreen() {
  const T = useTheme();
  const styles = useMemo(() => makeStyles(T), [T]);
  const { role, data, refreshData } = useContext(DataContext);
  const [refreshing, setRefreshing] = useState(false);

  const onRefresh = async () => {
    setRefreshing(true);
    await refreshData();
    setRefreshing(false);
  };

  return (
    <ScrollView 
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={T.brand} />}
    >
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Parent Quests</Text>
        {!data.parentQuests || data.parentQuests.length === 0 ? (
          <EmptyCard
            title="No quests yet"
            subtitle="Quests your family sets at home will appear here."
          />
        ) : (
          data.parentQuests?.map((q: ParentQuest, i: number) => (
            <View key={i} style={styles.card}>
              <View style={styles.cardRow}>
                <Text style={styles.itemTitle}>{q.taskDescription}</Text>
                <Text style={styles.badgeText}>+{q.xpBounty} XP</Text>
              </View>
              <Text style={styles.itemSubtitle}>Status: {q.status}</Text>
            </View>
          ))
        )}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Rewards</Text>
        {role === 'STUDENT' ? (
          <>
            {(!data.availableParentRewards || data.availableParentRewards.length === 0) && (!data.pendingParentRewards || data.pendingParentRewards.length === 0) ? (
              <EmptyCard
                title="Nothing to claim yet"
                subtitle="Rewards your family offers will show up here."
              />
            ) : (
              <>
                {data.availableParentRewards?.map((r: ParentReward, i: number) => (
                  <View key={`avail-${i}`} style={styles.card}>
                    <View style={styles.cardRow}>
                      <Text style={styles.itemTitle}>{r.rewardTitle}</Text>
                      <Text style={styles.badgeTextCost}>-{r.xpCost} XP</Text>
                    </View>
                    <Text style={styles.itemSubtitle}>Available to claim!</Text>
                  </View>
                ))}
                {data.pendingParentRewards?.map((r: ParentReward, i: number) => (
                  <View key={`pend-${i}`} style={styles.card}>
                    <View style={styles.cardRow}>
                      <Text style={styles.itemTitle}>{r.rewardTitle}</Text>
                      <Text style={styles.badgeTextCost}>-{r.xpCost} XP</Text>
                    </View>
                    <Text style={styles.itemSubtitle}>Pending Parent Approval</Text>
                  </View>
                ))}
              </>
            )}
          </>
        ) : (
          <>
            {!data.parentRewards || data.parentRewards.length === 0 ? (
              <EmptyCard
                title="No rewards set up"
                subtitle="Rewards you offer your child will appear here."
              />
            ) : (
              data.parentRewards?.map((r: ParentReward, i: number) => (
                <View key={i} style={styles.card}>
                  <View style={styles.cardRow}>
                    <Text style={styles.itemTitle}>{r.rewardTitle}</Text>
                    <Text style={styles.badgeTextCost}>{r.xpCost} XP</Text>
                  </View>
                  <Text style={styles.itemSubtitle}>Status: {r.status}</Text>
                </View>
              ))
            )}
          </>
        )}
      </View>
      <View style={{ height: 40 }} />
    </ScrollView>
  );
}

const makeStyles = (T: Theme) => StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: T.bg,
    padding: 16,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    color: T.text,
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 12,
  },
  infoText: {
    color: T.text3,
    fontSize: 14,
  },
  card: {
    backgroundColor: T.surface,
    padding: 16,
    borderRadius: 12,
    marginBottom: 8,
  },
  // Sized to the empty-state standard in AGENTS.md: at most 100pt tall,
  // 16/24 padding, one line of message and one of subtext.
  emptyCard: {
    backgroundColor: T.surface,
    maxHeight: 100,
    paddingVertical: 16,
    paddingHorizontal: 24,
    borderRadius: 12,
    marginBottom: 8,
    justifyContent: 'center',
  },
  emptyTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: T.text,
    marginBottom: 2,
  },
  cardRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  itemTitle: {
    color: T.text,
    fontSize: 16,
    fontWeight: 'bold',
    flex: 1,
  },
  itemSubtitle: {
    color: T.text3,
    fontSize: 14,
  },
  badgeText: {
    color: T.successInk,
    fontWeight: 'bold',
    // Was successInk on successInk -- the same colour for text and background,
    // so the label could not be seen at all.
    backgroundColor: T.success50,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
    overflow: 'hidden',
  },
  badgeTextCost: {
    color: T.dangerInk,
    fontWeight: 'bold',
    backgroundColor: T.danger50,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
    overflow: 'hidden',
  },
});
