import { View, Text, StyleSheet, ScrollView, RefreshControl, TouchableOpacity, ActivityIndicator } from 'react-native';
import { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'expo-router';
import { getNotifications, markNotificationRead, markAllNotificationsRead } from '../services/api';

interface NotificationItem {
  id: string;
  title: string;
  body: string;
  type: string;
  read: boolean;
  createdAt: string;
}

const TYPE_COLOR: Record<string, string> = {
  ATTENDANCE: '#f59e0b',
  TASK: '#3b82f6',
  ANNOUNCEMENT: '#a855f7',
  SYSTEM: '#64748b',
};

function timeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

export default function NotificationsScreen() {
  const router = useRouter();
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const fetchNotifications = useCallback(async () => {
    try {
      const data = await getNotifications();
      setNotifications(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Failed to fetch notifications:', err);
      setNotifications([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchNotifications(); }, [fetchNotifications]);

  const onRefresh = async () => {
    setRefreshing(true);
    await fetchNotifications();
    setRefreshing(false);
  };

  const handleTap = async (item: NotificationItem) => {
    if (!item.read) {
      await markNotificationRead(item.id);
      setNotifications(prev => prev.map(n => n.id === item.id ? { ...n, read: true } : n));
    }
  };

  const handleMarkAllRead = async () => {
    await markAllNotificationsRead();
    setNotifications(prev => prev.map(n => ({ ...n, read: true })));
  };

  const unreadCount = notifications.filter(n => !n.read).length;

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={styles.backButton}>‹ Back</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Notifications</Text>
        {unreadCount > 0 ? (
          <TouchableOpacity onPress={handleMarkAllRead}>
            <Text style={styles.markAllText}>Mark all read</Text>
          </TouchableOpacity>
        ) : <View style={{ width: 80 }} />}
      </View>

      {loading ? (
        <View style={styles.center}><ActivityIndicator color="#6366f1" size="large" /></View>
      ) : (
        <ScrollView
          style={styles.list}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#fff" />}
        >
          {notifications.length === 0 ? (
            <View style={styles.emptyCard}>
              <Text style={styles.emptyText}>No notifications yet.</Text>
            </View>
          ) : (
            notifications.map(item => (
              <TouchableOpacity key={item.id} style={styles.card} onPress={() => handleTap(item)}>
                <View style={styles.cardHeader}>
                  <View style={styles.titleRow}>
                    {!item.read && <View style={styles.unreadDot} />}
                    <Text style={styles.cardTitle}>{item.title}</Text>
                  </View>
                  <View style={[styles.typeBadge, { backgroundColor: (TYPE_COLOR[item.type] || '#64748b') + '22' }]}>
                    <Text style={[styles.typeBadgeText, { color: TYPE_COLOR[item.type] || '#64748b' }]}>
                      {item.type}
                    </Text>
                  </View>
                </View>
                <Text style={styles.cardBody}>{item.body}</Text>
                <Text style={styles.cardTime}>{timeAgo(item.createdAt)}</Text>
              </TouchableOpacity>
            ))
          )}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f172a' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, paddingTop: 50, borderBottomWidth: 1, borderBottomColor: '#1e293b' },
  backButton: { color: '#6366f1', fontSize: 16, fontWeight: '600' },
  headerTitle: { color: '#fff', fontSize: 18, fontWeight: 'bold' },
  markAllText: { color: '#6366f1', fontSize: 13, fontWeight: '600' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  list: { flex: 1, padding: 16 },
  card: { backgroundColor: '#1e293b', borderRadius: 12, padding: 14, marginBottom: 10 },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 6 },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: 6, flex: 1 },
  unreadDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: '#ef4444' },
  cardTitle: { color: '#fff', fontSize: 15, fontWeight: '700', flex: 1 },
  typeBadge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 8 },
  typeBadgeText: { fontSize: 10, fontWeight: '700' },
  cardBody: { color: '#94a3b8', fontSize: 13, lineHeight: 18 },
  cardTime: { color: '#64748b', fontSize: 11, marginTop: 6 },
  emptyCard: { backgroundColor: '#1e293b', borderRadius: 12, padding: 20, alignItems: 'center', height: 100, justifyContent: 'center' },
  emptyText: { color: '#94a3b8', fontSize: 14 },
});