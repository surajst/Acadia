import { Platform } from 'react-native';
import * as Location from 'expo-location';
import { pingDriverLocation } from './api';

// Foreground-only bus location sharing.
//
// v1 on Google Play ships without background location: Play reviews
// ACCESS_BACKGROUND_LOCATION (and the location foreground service) strictly,
// above all in an app used by children, and needs a declaration plus a demo
// video. So the driver's location is shared while the trip screen is open,
// using a foreground watch, and stops when the app leaves the foreground.
// Background sharing can come back in a later release with its own
// declaration. app.json blocks the background permissions so no library can
// slip them back into the manifest.

let subscription = null;

export async function startTrip() {
  if (Platform.OS === 'web') {
    throw new Error('Bus location sharing is only available in the native app, not the web version.');
  }

  const fg = await Location.requestForegroundPermissionsAsync();
  if (fg.status !== 'granted') {
    throw new Error('Location permission was not granted.');
  }

  if (subscription) return;
  subscription = await Location.watchPositionAsync(
    {
      accuracy: Location.Accuracy.High,
      timeInterval: 30000,
      distanceInterval: 25,
    },
    async (loc) => {
      try {
        await pingDriverLocation(loc.coords.latitude, loc.coords.longitude);
      } catch (e) {
        console.log('Failed to ping driver location:', e);
      }
    },
  );
}

export async function stopTrip() {
  if (subscription) {
    subscription.remove();
    subscription = null;
  }
}

export async function isTripActive() {
  return subscription !== null;
}
