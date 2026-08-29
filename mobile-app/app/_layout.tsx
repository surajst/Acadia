import { useFonts } from 'expo-font';
import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import 'react-native-reanimated';

import { useColorScheme } from '@/components/useColorScheme';
import { AuthProvider, useAuth } from '@/context/AuthContext';
import T from '../constants/theme';
import LoginScreen from './index';

export {
  // Catch any errors thrown by the Layout component.
  ErrorBoundary,
} from 'expo-router';

export const unstable_settings = {
  // Ensure that reloading on `/modal` keeps a back button present.
  initialRouteName: '(tabs)',
};

// Prevent the splash screen from auto-hiding before asset loading is complete.
SplashScreen.preventAutoHideAsync();

export default function RootLayout() {
  const [loaded, error] = useFonts({
    SpaceMono: require('../assets/fonts/SpaceMono-Regular.ttf'),
  });

  // Expo Router uses Error Boundaries to catch errors in the navigation tree.
  useEffect(() => {
    if (error) throw error;
  }, [error]);

  useEffect(() => {
    if (loaded) {
      SplashScreen.hideAsync();
    }
  }, [loaded]);

  return (
    <AuthProvider>
      <RootLayoutGate fontsLoaded={loaded} />
    </AuthProvider>
  );
}

type RootLayoutGateProps = {
  fontsLoaded: boolean;
};

function RootLayoutGate({ fontsLoaded }: RootLayoutGateProps) {
  const { userToken, isLoading } = useAuth();

  if (!fontsLoaded || isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#4F46E5" />
      </View>
    );
  }

  if (!userToken) {
    return <LoginScreen />;
  }

  return <ProtectedStack />;
}

function ProtectedStack() {
  const colorScheme = useColorScheme();

  return (
    <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
      {/* headerTintColor was #fff on a light header, so every stack screen's
          title and back arrow were white on near-white -- invisible. That is
          why the child profile and recognition screens appeared to have no
          heading at all. */}
      <Stack screenOptions={{ contentStyle: { backgroundColor: T.bg }, headerStyle: { backgroundColor: T.bg }, headerTintColor: T.text }}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="modal" options={{ presentation: 'modal' }} />
        <Stack.Screen name="teacher" options={{ title: 'My Classes' }} />
        <Stack.Screen name="tasks" options={{ title: 'Tasks' }} />
        <Stack.Screen name="gradebook" options={{ title: 'Gradebook' }} />
        <Stack.Screen name="timetable" options={{ title: 'Timetable' }} />
      </Stack>
    </ThemeProvider>
  );
}

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#F7F9FC',
  },
});
