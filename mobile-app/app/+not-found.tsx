import React, { useMemo } from 'react';
import { Stack, useRouter, usePathname } from 'expo-router';
import { StyleSheet, Text, View, Pressable } from 'react-native';
import { SymbolView } from 'expo-symbols';

import { useTheme, type Theme } from '../context/ThemeContext';

/**
 * Where a mistyped or stale link lands.
 *
 * <p>This was Expo's stock screen: "Oops!" in the header, "This screen doesn't
 * exist." and a blue "Go to home screen!" link. It looks like the app crashed
 * rather than like the app answering, and the two routes people actually guess
 * (/verify and /my-classes, which the menus call Verify and My Classes) both
 * land here.
 *
 * <p>Those two are redirected in the root layout rather than handled as
 * not-found, so this screen is for genuinely unknown addresses. It says which
 * address failed, because "this screen doesn't exist" with no address is
 * unactionable for the person reporting it.
 */
export default function NotFoundScreen() {
  const T = useTheme();
  const styles = useMemo(() => makeStyles(T), [T]);
  const router = useRouter();
  const pathname = usePathname();

  return (
    <>
      {/* A title, not "Oops!" -- and one that is legible in a browser tab on
          the web build, where every screen's title was empty. */}
      <Stack.Screen options={{ title: 'Page not found' }} />
      <View style={styles.root}>
        <View style={styles.badge}>
          <SymbolView
            name={{ ios: 'questionmark.circle', android: 'help', web: 'help' }}
            tintColor={T.brand}
            size={40}
          />
        </View>

        <Text style={styles.title}>We couldn’t find that page</Text>
        <Text style={styles.body}>
          The link may be out of date, or the address may have a typo in it.
        </Text>
        {pathname ? <Text style={styles.path}>{pathname}</Text> : null}

        <Pressable
          onPress={() => router.replace('/')}
          accessibilityRole="button"
          accessibilityLabel="Go home"
          hitSlop={12}
          style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}
        >
          <Text style={styles.buttonText}>Go home</Text>
        </Pressable>
      </View>
    </>
  );
}

// Brand-dependent styles are built from the live T and memoised on it, so a
// theme change repaints this screen too.
const makeStyles = (T: Theme) =>
  StyleSheet.create({
    root: {
      flex: 1,
      alignItems: 'center',
      justifyContent: 'center',
      padding: 24,
      backgroundColor: T.bg,
    },
    badge: {
      width: 72,
      height: 72,
      borderRadius: 36,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: T.brand50,
      marginBottom: 16,
    },
    title: {
      fontSize: 20,
      fontWeight: '700',
      color: T.text,
      textAlign: 'center',
    },
    body: {
      fontSize: 14,
      color: T.text2,
      textAlign: 'center',
      marginTop: 8,
      maxWidth: 320,
      lineHeight: 20,
    },
    // The address that failed, so a bug report can name it.
    path: {
      fontSize: 12,
      color: T.text3,
      marginTop: 10,
      fontFamily: 'monospace',
    },
    button: {
      marginTop: 24,
      paddingVertical: 12,
      paddingHorizontal: 24,
      borderRadius: 14,
      backgroundColor: T.brand,
      // 44dp minimum, plus hitSlop above, so it is reachable rather than merely
      // present.
      minHeight: 44,
      justifyContent: 'center',
    },
    buttonPressed: { opacity: 0.85 },
    buttonText: {
      color: T.onBrand,
      fontSize: 14,
      fontWeight: '700',
    },
  });
