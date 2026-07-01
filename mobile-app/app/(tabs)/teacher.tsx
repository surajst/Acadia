import React, { useState, useEffect } from 'react';
import { StyleSheet, View, Text, ScrollView, TouchableOpacity, ActivityIndicator, Platform } from 'react-native';
import { SymbolView } from 'expo-symbols';
import ClassRosterModal from '@/components/ClassRosterModal';
import { useAuth } from '@/context/AuthContext';

interface RosterCardProps {
  className: string;
  subject: string;
  studentCount: number;
  status: 'pending' | 'active' | 'completed';
  onViewRoster: () => void;
}

function RosterCard({ className, subject, studentCount, status, onViewRoster }: RosterCardProps) {
  const statusColors: Record<string, string> = {
    pending: '#f59e0b',
    active:  '#22c55e',
    completed: '#6366f1',
  };
  const statusLabels: Record<string, string> = {
    pending:   'Pending Review',
    active:    'Active',
    completed: 'Completed',
  };
  const statusBg: Record<string, string> = {
    pending:   '#f59e0b22',
    active:    '#22c55e22',
    completed: '#6366f122',
  };

  return (
    <TouchableOpacity style={styles.card} activeOpacity={0.85} onPress={onViewRoster}>
      <View style={styles.cardHeader}>
        <View style={styles.cardIconWrap}>
          <SymbolView
            name={{ ios: 'person.3', android: 'group', web: 'group' }}
            tintColor="#6366f1"
            size={22}
          />
        </View>
        <View style={{ flex: 1, marginLeft: 12 }}>
          <Text style={styles.cardTitle}>{className}</Text>
          <Text style={styles.cardSubject}>{subject}</Text>
        </View>
        <View style={[styles.statusBadge, { backgroundColor: statusBg[status] }]}>
          <View style={[styles.statusDot, { backgroundColor: statusColors[status] }]} />
          <Text style={[styles.statusText, { color: statusColors[status] }]}>
            {statusLabels[status]}
          </Text>
        </View>
      </View>

      <View style={styles.cardDivider} />

      <View style={styles.cardFooter}>
        <SymbolView
          name={{ ios: 'person', android: 'person', web: 'person' }}
          tintColor="#94a3b8"
          size={14}
        />
        <Text style={styles.footerText}> {studentCount} Students</Text>
        <TouchableOpacity style={styles.viewBtn} onPress={onViewRoster}>
          <Text style={styles.viewBtnText}>View Roster  ›</Text>
        </TouchableOpacity>
      </View>
    </TouchableOpacity>
  );
}

export default function TeacherScreen() {
  const { userToken } = useAuth();
  const [classes, setClasses] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [rosterVisible, setRosterVisible] = useState(false);
  const [selectedClass, setSelectedClass] = useState<{id: string, name: string} | null>(null);

  const BASE_HOST = (typeof window !== 'undefined') ? 'http://localhost:8080' : (Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080');

  useEffect(() => {
    const fetchClasses = async () => {
      try {
        const resp = await fetch(`${BASE_HOST}/api/teacher/classes`, {
          headers: { Authorization: `Bearer ${userToken}` }
        });
        const data = await resp.json();
        setClasses(Array.isArray(data) ? data : []);
      } catch (e) {
        console.error('Failed to fetch classes:', e);
        setClasses([]);
      } finally {
        setLoading(false);
      }
    };

    if (userToken) {
      fetchClasses();
    } else {
      setLoading(false);
    }
  }, [userToken]);

  return (
    <View style={styles.root}>
      <View style={styles.headerBand}>
        <View style={styles.headerIconWrap}>
          <SymbolView
            name={{ ios: 'book.pages', android: 'book', web: 'book' }}
            tintColor="#6366f1"
            size={26}
          />
        </View>
        <View style={{ flex: 1, marginLeft: 14 }}>
          <Text style={styles.headerTitle}>My Classes</Text>
          <Text style={styles.headerSubtitle}>Greenwood High · Academic Year 2025-26</Text>
        </View>
      </View>

      <View style={styles.sectionLabelRow}>
        <Text style={styles.sectionLabel}>YOUR CLASS ROSTERS</Text>
        {classes.length > 0 && (
          <View style={styles.sectionBadge}>
            <Text style={styles.sectionBadgeText}>{classes.length} Classes</Text>
          </View>
        )}
      </View>

      {loading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#6366f1" />
          <Text style={styles.loadingText}>Loading classes...</Text>
        </View>
      ) : classes.length === 0 ? (
        <View style={styles.emptyContainer}>
          <SymbolView
            name={{ ios: 'book.pages', android: 'book', web: 'book' }}
            tintColor="#6366f1"
            size={32}
          />
          <Text style={styles.emptyText}>No classes assigned</Text>
          <Text style={styles.emptySubtext}>Classes will appear here when assigned</Text>
        </View>
      ) : (
        <ScrollView
          style={styles.scroll}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
        >
          {classes.map((cls) => (
            <RosterCard
              key={cls.id}
              className={cls.className}
              subject={cls.subject}
              studentCount={cls.studentCount}
              status="active"
              onViewRoster={() => {
                setSelectedClass({ id: cls.id, name: cls.className });
                setRosterVisible(true);
              }}
            />
          ))}
        </ScrollView>
      )}

      <ClassRosterModal
        isVisible={rosterVisible}
        onClose={() => setRosterVisible(false)}
        sectionId={selectedClass?.id}
        className={selectedClass?.name}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0f172a' },
  headerBand: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1e293b',
    paddingHorizontal: 20,
    paddingVertical: 18,
    borderBottomWidth: 1,
    borderBottomColor: '#334155',
  },
  headerIconWrap: {
    width: 48, height: 48, borderRadius: 14,
    backgroundColor: '#6366f120',
    justifyContent: 'center', alignItems: 'center',
  },
  headerTitle: { fontSize: 17, fontWeight: '700', color: '#f1f5f9', letterSpacing: 0.2 },
  headerSubtitle: { fontSize: 12, color: '#64748b', marginTop: 2 },
  sectionLabelRow: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: 20, paddingTop: 20, paddingBottom: 10, gap: 10,
  },
  sectionLabel: { fontSize: 11, fontWeight: '700', color: '#64748b', letterSpacing: 1.2 },
  sectionBadge: {
    backgroundColor: '#6366f122', borderRadius: 20,
    paddingHorizontal: 8, paddingVertical: 2,
  },
  sectionBadgeText: { fontSize: 11, color: '#6366f1', fontWeight: '600' },
  loadingContainer: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  loadingText: { marginTop: 12, fontSize: 14, color: '#94a3b8' },
  emptyContainer: { flex: 1, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 20 },
  emptyText: { fontSize: 16, fontWeight: '600', color: '#f1f5f9', marginTop: 12 },
  emptySubtext: { fontSize: 13, color: '#64748b', marginTop: 6, textAlign: 'center' },
  scroll: { flex: 1 },
  scrollContent: { paddingHorizontal: 16, paddingBottom: 32, gap: 12 },
  card: {
    backgroundColor: '#1e293b', borderRadius: 16, padding: 16,
    borderWidth: 1, borderColor: '#334155',
  },
  cardHeader: { flexDirection: 'row', alignItems: 'center' },
  cardIconWrap: {
    width: 42, height: 42, borderRadius: 12,
    backgroundColor: '#6366f115',
    justifyContent: 'center', alignItems: 'center',
  },
  cardTitle: { fontSize: 15, fontWeight: '700', color: '#f1f5f9' },
  cardSubject: { fontSize: 12, color: '#64748b', marginTop: 2 },
  statusBadge: {
    flexDirection: 'row', alignItems: 'center',
    borderRadius: 20, paddingHorizontal: 8, paddingVertical: 4, gap: 5,
  },
  statusDot: { width: 6, height: 6, borderRadius: 3 },
  statusText: { fontSize: 11, fontWeight: '600' },
  cardDivider: { height: 1, backgroundColor: '#334155', marginVertical: 12 },
  cardFooter: { flexDirection: 'row', alignItems: 'center' },
  footerText: { fontSize: 13, color: '#94a3b8', flex: 1 },
  viewBtn: { flexDirection: 'row', alignItems: 'center' },
  viewBtnText: { fontSize: 13, color: '#6366f1', fontWeight: '600' },
});