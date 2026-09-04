import React, { useEffect, useState } from 'react';
import { Animated, View, StyleSheet, Easing, AccessibilityInfo } from 'react-native';
import T from '../../constants/theme';

/**
 * Placeholders shaped like the content that is coming, instead of a spinner
 * over the whole screen. The dashboard's own numbers, cards and rows all
 * arrive at different times; a full-page spinner hides the parts that are
 * already there and then reflows everything at once when the slowest fetch
 * lands.
 *
 * The pulse is switched off when the device asks for reduced motion.
 */

export function Skeleton({ width, height, radius = T.rXs, style }: {
  width?: number | `${number}%`;
  height: number;
  radius?: number;
  style?: any;
}) {
  // Lazy useState, not useRef(new Animated.Value(...)).current: reading .current
  // during render is a hook-rules violation, and the useRef spelling also
  // constructs a throwaway Animated.Value on every single render.
  const [pulse] = useState(() => new Animated.Value(0.5));

  useEffect(() => {
    let loop: Animated.CompositeAnimation | null = null;
    let cancelled = false;

    AccessibilityInfo.isReduceMotionEnabled().then((reduced) => {
      if (cancelled || reduced) return;
      loop = Animated.loop(
        Animated.sequence([
          Animated.timing(pulse, { toValue: 1, duration: 700, easing: Easing.inOut(Easing.quad), useNativeDriver: true }),
          Animated.timing(pulse, { toValue: 0.5, duration: 700, easing: Easing.inOut(Easing.quad), useNativeDriver: true }),
        ])
      );
      loop.start();
    });

    return () => { cancelled = true; loop?.stop(); };
  }, [pulse]);

  return (
    <Animated.View
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      style={[{ width, height, borderRadius: radius, backgroundColor: T.track, opacity: pulse }, style]}
    />
  );
}

/** The three metric tiles, at their real size, so nothing jumps when they fill. */
export function StatRowSkeleton() {
  return (
    <View style={s.statRow}>
      {[0, 1, 2].map((i) => (
        <View key={i} style={s.statBox}>
          <Skeleton width="60%" height={11} radius={4} />
          <Skeleton width="45%" height={24} radius={6} style={{ marginTop: T.space.sm }} />
        </View>
      ))}
    </View>
  );
}

/** Stacked list rows -- the queue, a roster, a task list. */
export function ListSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <View style={{ gap: T.space.md }}>
      {Array.from({ length: rows }).map((_, i) => (
        <View key={i} style={s.row}>
          <Skeleton width={40} height={40} radius={T.pill} />
          <View style={{ flex: 1, gap: T.space.sm }}>
            <Skeleton width="70%" height={13} radius={4} />
            <Skeleton width="40%" height={11} radius={4} />
          </View>
        </View>
      ))}
    </View>
  );
}

const s = StyleSheet.create({
  statRow: { flexDirection: 'row', gap: T.space.md },
  statBox: {
    ...T.card,
    flex: 1,
    padding: T.space.lg,
  },
  row: {
    ...T.card,
    flexDirection: 'row',
    alignItems: 'center',
    gap: T.space.md,
    padding: T.space.lg,
  },
});
