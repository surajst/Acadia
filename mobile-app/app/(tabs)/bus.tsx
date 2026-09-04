import React, { useEffect, useRef, useState } from 'react';
import { View, Text, StyleSheet, ActivityIndicator, Platform } from 'react-native';
import { SymbolView } from 'expo-symbols';
import { getParentBusLocation } from '../../services/api';
import T from '../../constants/theme';

// react-native-maps is a native module with no meaningful web support — the
// Expo web export (the version actually deployed and tested this session)
// falls back to a simple "last seen" status card instead of crashing.
let MapView: any = null;
let Marker: any = null;
if (Platform.OS !== 'web') {
  const maps = require('react-native-maps');
  MapView = maps.default;
  Marker = maps.Marker;
}

const POLL_INTERVAL_MS = 20000;

export default function BusScreen() {
  const [location, setLocation] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  // "Last seen 3 min ago" is a clock reading, so it belongs in state that ticks
  // rather than a Date.now() call during render -- which is impure, and left the
  // label frozen at whatever it said when the screen first painted.
  const [now, setNow] = useState(() => Date.now());
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const load = async () => {
    try {
      const data = await getParentBusLocation();
      setLocation(data);
    } catch (e) {
      console.log('Failed to fetch bus location:', e);
    } finally {
      setLoading(false);
      setNow(Date.now());
    }
  };

  useEffect(() => {
    load();
    pollRef.current = setInterval(load, POLL_INTERVAL_MS);
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mount-only poll
  }, []);

  const lastSeenText = () => {
    if (!location?.lastPingAt) return 'No location shared yet';
    const minutesAgo = Math.round((now - new Date(location.lastPingAt).getTime()) / 60000);
    if (minutesAgo < 1) return 'Last seen just now';
    return `Last seen ${minutesAgo} min ago`;
  };

  return (
    <View style={styles.root}>
      <View style={styles.headerBand}>
        <View style={styles.headerIconWrap}>
          <SymbolView name={{ ios: 'bus', android: 'directions_bus', web: 'directions_bus' }} tintColor={T.brand} size={26} />
        </View>
        <View style={{ flex: 1, marginLeft: 14 }}>
          <Text style={styles.headerTitle}>Bus Tracker</Text>
          <Text style={styles.headerSubtitle}>{location?.routeName || 'Live pickup status'}</Text>
        </View>
      </View>

      {loading ? (
        <ActivityIndicator size="large" color={T.brand} style={{ marginTop: 40 }} />
      ) : !location?.assigned ? (
        <View style={styles.emptyState}>
          <Text style={styles.emptyText}>No bus route assigned yet. Ask your school admin to link your child’s class to a bus route.</Text>
        </View>
      ) : (
        <>
          {Platform.OS === 'web' || !location.latitude || !location.longitude ? (
            <View style={styles.statusCard}>
              <Text style={styles.statusRoute}>{location.routeName}</Text>
              <Text style={styles.statusText}>{lastSeenText()}</Text>
              {location.latitude && location.longitude && (
                <Text style={styles.statusCoords}>
                  {location.latitude.toFixed(4)}, {location.longitude.toFixed(4)}
                </Text>
              )}
              {Platform.OS === 'web' && (
                <Text style={styles.webNotice}>Open the native app for a live map view.</Text>
              )}
            </View>
          ) : (
            <MapView
              style={styles.map}
              initialRegion={{
                latitude: location.latitude,
                longitude: location.longitude,
                latitudeDelta: 0.01,
                longitudeDelta: 0.01,
              }}
              region={{
                latitude: location.latitude,
                longitude: location.longitude,
                latitudeDelta: 0.01,
                longitudeDelta: 0.01,
              }}
            >
              <Marker
                coordinate={{ latitude: location.latitude, longitude: location.longitude }}
                title={location.routeName}
                description={lastSeenText()}
              />
            </MapView>
          )}
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: T.bg },
  headerBand: {
    flexDirection: 'row', alignItems: 'center',
    backgroundColor: T.surface, paddingHorizontal: 20, paddingVertical: 18,
    borderBottomWidth: 1, borderBottomColor: T.line,
  },
  headerIconWrap: { width: 48, height: 48, borderRadius: 14, backgroundColor: T.brand50, justifyContent: 'center', alignItems: 'center' },
  headerTitle: { fontSize: 17, fontWeight: '700', color: T.text },
  headerSubtitle: { fontSize: 12, color: T.text3, marginTop: 2 },
  emptyState: { padding: 24 },
  emptyText: { color: T.text3, fontSize: 14, textAlign: 'center' },
  statusCard: { margin: 16, backgroundColor: T.surface, borderRadius: 16, padding: 20, borderWidth: 1, borderColor: T.line },
  statusRoute: { color: T.text, fontSize: 16, fontWeight: '700' },
  statusText: { color: T.text3, fontSize: 14, marginTop: 6 },
  statusCoords: { color: T.text3, fontSize: 12, marginTop: 6, fontVariant: ['tabular-nums'] },
  webNotice: { color: T.warnInk, fontSize: 12, marginTop: 12 },
  map: { flex: 1 },
});
