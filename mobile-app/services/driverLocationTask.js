import { Platform } from 'react-native';
import * as TaskManager from 'expo-task-manager';
import * as Location from 'expo-location';
import { pingDriverLocation } from './api';

export const DRIVER_LOCATION_TASK = 'driver-location-task';

// Background location updates aren't supported on the Expo web export —
// expo-task-manager/expo-location background APIs are native-only. The
// driver's trip screen checks Platform.OS before calling startTrip().
if (Platform.OS !== 'web') {
  TaskManager.defineTask(DRIVER_LOCATION_TASK, async ({ data, error }) => {
    if (error) {
      console.log('Driver location task error:', error);
      return;
    }
    const locations = data?.locations;
    const latest = locations?.[locations.length - 1];
    if (latest) {
      try {
        await pingDriverLocation(latest.coords.latitude, latest.coords.longitude);
      } catch (e) {
        console.log('Failed to ping driver location:', e);
      }
    }
  });
}

export async function startTrip() {
  if (Platform.OS === 'web') {
    throw new Error('Background location sharing is only available in the native app, not the web version.');
  }

  const fg = await Location.requestForegroundPermissionsAsync();
  if (fg.status !== 'granted') {
    throw new Error('Location permission was not granted.');
  }
  const bg = await Location.requestBackgroundPermissionsAsync();
  if (bg.status !== 'granted') {
    throw new Error('Background location permission was not granted — the bus location can only be shared while the app stays open.');
  }

  await Location.startLocationUpdatesAsync(DRIVER_LOCATION_TASK, {
    accuracy: Location.Accuracy.High,
    timeInterval: 30000,
    distanceInterval: 25,
    showsBackgroundLocationIndicator: true,
    foregroundService: {
      notificationTitle: 'Sharing bus location',
      notificationBody: "Parents can see this bus's live location while the trip is active.",
    },
  });
}

export async function stopTrip() {
  if (Platform.OS === 'web') return;
  const started = await TaskManager.isTaskRegisteredAsync(DRIVER_LOCATION_TASK);
  if (started) {
    await Location.stopLocationUpdatesAsync(DRIVER_LOCATION_TASK);
  }
}

export async function isTripActive() {
  if (Platform.OS === 'web') return false;
  return TaskManager.isTaskRegisteredAsync(DRIVER_LOCATION_TASK);
}
