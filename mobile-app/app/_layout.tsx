import { useFonts } from 'expo-font';
import { DarkTheme, DefaultTheme, Stack, ThemeProvider, Redirect, usePathname } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import 'react-native-reanimated';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { useColorScheme } from '@/components/useColorScheme';
import { AuthProvider, useAuth } from '@/context/AuthContext';
import { canOpen, ROLE_HOME } from '../constants/routeAccess';
import { SCREEN_TITLES } from '../constants/screenTitles';
import DocumentTitle from '../components/DocumentTitle';
// Aliased: expo-router also exports a `ThemeProvider` (light/dark nav chrome,
// used below for the Stack) -- unrelated to the app's own brand-colour theme.
import { ThemeProvider as AppThemeProvider } from '@/context/ThemeContext';
import { ProfilePhotoProvider } from '@/context/ProfilePhotoContext';
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

  // SafeAreaProvider is what makes useSafeAreaInsets() return real numbers.
  // Without it every inset is zero, and nothing says so: the tab bar sat under
  // the Android navigation bar, bottom sheets had their last row cut off by it,
  // and ClassRosterModal's own edges={['bottom']} quietly did nothing. It has
  // to wrap everything, because react-navigation's tab bar reads the insets too.
  return (
    <SafeAreaProvider>
      <AppThemeProvider>
        <AuthProvider>
          {/* Inside AuthProvider: it reads the profile, which needs a token. */}
          <ProfilePhotoProvider>
            <RootLayoutGate fontsLoaded={loaded} />
          </ProfilePhotoProvider>
        </AuthProvider>
      </AppThemeProvider>
    </SafeAreaProvider>
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

/**
 * Sends a signed-in user back to their own home if they reach a screen that is
 * not theirs.
 *
 * <p>The API was already locked down -- a parent's token gets 403 on every teacher
 * endpoint -- but the app let a parent open /gradebook by address, which rendered
 * "Create assessments and enter scores" above "No classes assigned". Nothing
 * leaked, because no data arrived; what leaked was the impression that a parent
 * belongs there, and a screen of controls that cannot work.
 *
 * <p>A redirect rather than Expo Router route groups: groups would mean moving a
 * dozen files and rewriting every router.push target, and the outcome asked for is
 * that the person ends up at their own home. constants/routeAccess.ts holds the
 * map, so the rule is one list rather than a check scattered across screens.
 */
function RouteGuard({ children }: { children: React.ReactNode }) {
  const { userRole } = useAuth();
  const pathname = usePathname();

  if (!canOpen(userRole, pathname)) {
    return <Redirect href={ROLE_HOME as never} />;
  }
  return <>{children}</>;
}

function ProtectedStack() {
  const colorScheme = useColorScheme();

  return (
    <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
      {/* Names the browser tab after whichever screen is open. Outside the
          guard, so a redirect still leaves the tab correctly named. */}
      <DocumentTitle />
      <RouteGuard>
      {/* headerTintColor was #fff on a light header, so every stack screen's
          title and back arrow were white on near-white -- invisible. That is
          why the child profile and recognition screens appeared to have no
          heading at all. */}
      {/* headerBackTitle is set once here rather than per screen: without it the
          back control is named after the route it returns to, so a screen reader
          announced "(tabs), back" on the teacher Gradebook and "index, back" on
          student Challenges. Route names are not words for a person. */}
      <Stack screenOptions={{
        contentStyle: { backgroundColor: T.bg },
        headerStyle: { backgroundColor: T.bg },
        headerTintColor: T.text,
        headerBackTitle: 'Back',
        headerBackButtonDisplayMode: 'minimal',
      }}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="modal" options={{ presentation: 'modal' }} />
        {/* Had no title anywhere -- not declared here, and no inline
            Stack.Screen in the file -- so its header read the raw route name. */}
        <Stack.Screen name="notifications" options={{ title: SCREEN_TITLES.notifications }} />
        <Stack.Screen name="teacher" options={{ title: SCREEN_TITLES.teacher }} />
        <Stack.Screen name="verification" options={{ title: SCREEN_TITLES.verification }} />
        <Stack.Screen name="tasks" options={{ title: SCREEN_TITLES.tasks }} />
        <Stack.Screen name="task-new" options={{ title: SCREEN_TITLES['task-new'] }} />
        <Stack.Screen name="gradebook" options={{ title: SCREEN_TITLES.gradebook }} />
        <Stack.Screen name="timetable" options={{ title: SCREEN_TITLES.timetable }} />
        <Stack.Screen name="student-timetable" options={{ title: SCREEN_TITLES['student-timetable'] }} />
        <Stack.Screen name="student-news" options={{ title: SCREEN_TITLES['student-news'] }} />
        <Stack.Screen name="student-results" options={{ title: SCREEN_TITLES['student-results'] }} />
        <Stack.Screen name="marketplace" options={{ title: SCREEN_TITLES.marketplace }} />
      </Stack>
      </RouteGuard>
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
