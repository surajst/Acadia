import React, { useMemo } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { SymbolView } from 'expo-symbols';

import { useTheme, type Theme } from '../../context/ThemeContext';

type Props = {
  /** What went wrong, in words a parent can act on. */
  message: string;
  onRetry?: () => void;
};

/**
 * Shown when a request failed, in place of an empty state.
 *
 * <p>An empty state is a claim about the school's data: "no fees raised yet" says
 * the school has not billed anything. After a failed request the app does not
 * know that, and saying it anyway is how a parent was shown "No fees raised yet.
 * The school has not billed anything for Your child" over 450 rupees
 * outstanding, on a screen whose only real problem was an expired token.
 *
 * <p>So: say the load failed, and offer the one action that might fix it. Never
 * dress a failure as an answer.
 */
export default function LoadFailed({ message, onRetry }: Props) {
  const T = useTheme();
  const s = useMemo(() => makeStyles(T), [T]);

  return (
    <View style={s.root} accessibilityRole="alert" accessibilityLiveRegion="polite">
      <SymbolView
        name={{ ios: 'exclamationmark.triangle', android: 'warning', web: 'warning' }}
        tintColor={T.warnInk}
        size={28}
      />
      <Text style={s.title}>Couldn’t load this</Text>
      <Text style={s.body}>{message}</Text>
      {onRetry ? (
        <Pressable
          onPress={onRetry}
          accessibilityRole="button"
          accessibilityLabel="Try again"
          hitSlop={12}
          style={({ pressed }) => [s.button, pressed && s.buttonPressed]}
        >
          <Text style={s.buttonText}>Try again</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

const makeStyles = (T: Theme) =>
  StyleSheet.create({
    root: {
      alignItems: 'center',
      justifyContent: 'center',
      paddingVertical: 28,
      paddingHorizontal: 24,
      gap: 6,
    },
    title: { fontSize: 15, fontWeight: '700', color: T.text, marginTop: 6 },
    body: {
      fontSize: 13,
      color: T.text2,
      textAlign: 'center',
      lineHeight: 19,
      maxWidth: 300,
    },
    button: {
      marginTop: 12,
      minHeight: 44,
      justifyContent: 'center',
      paddingHorizontal: 22,
      borderRadius: 12,
      backgroundColor: T.brand,
    },
    buttonPressed: { opacity: 0.85 },
    buttonText: { color: T.onBrand, fontSize: 14, fontWeight: '700' },
  });
