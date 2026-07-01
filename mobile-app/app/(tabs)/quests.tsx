import { View, Text, StyleSheet, ScrollView, RefreshControl } from 'react-native';
import { useContext, useState } from 'react';
import { DataContext } from './_layout';

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

export default function QuestsScreen() {
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
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#fff" />}
    >
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Parent Quests</Text>
        {!data.parentQuests || data.parentQuests.length === 0 ? (
          <View style={styles.card}>
            <View style={styles.cardRow}>
              <Text style={styles.itemTitle}>Clean your room</Text>
              <Text style={styles.badgeText}>+50 XP</Text>
            </View>
            <Text style={styles.itemSubtitle}>Status: PENDING</Text>
          </View>
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
              <View style={styles.card}>
                <View style={styles.cardRow}>
                  <Text style={styles.itemTitle}>Extra Screen Time (1hr)</Text>
                  <Text style={styles.badgeTextCost}>-100 XP</Text>
                </View>
                <Text style={styles.itemSubtitle}>Available to claim!</Text>
              </View>
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
              <View style={styles.card}>
                <View style={styles.cardRow}>
                  <Text style={styles.itemTitle}>Ice Cream Trip</Text>
                  <Text style={styles.badgeTextCost}>200 XP</Text>
                </View>
                <Text style={styles.itemSubtitle}>Status: REDEEMED</Text>
              </View>
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

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0f172a',
    padding: 16,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    color: '#fff',
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 12,
  },
  infoText: {
    color: '#94a3b8',
    fontSize: 14,
  },
  card: {
    backgroundColor: '#1e293b',
    padding: 16,
    borderRadius: 12,
    marginBottom: 8,
  },
  cardRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  itemTitle: {
    color: '#fff',
    fontSize: 16,
    fontWeight: 'bold',
    flex: 1,
  },
  itemSubtitle: {
    color: '#94a3b8',
    fontSize: 14,
  },
  badgeText: {
    color: '#4ade80',
    fontWeight: 'bold',
    backgroundColor: '#14532d',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
    overflow: 'hidden',
  },
  badgeTextCost: {
    color: '#f87171',
    fontWeight: 'bold',
    backgroundColor: '#7f1d1d',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
    overflow: 'hidden',
  },
});
