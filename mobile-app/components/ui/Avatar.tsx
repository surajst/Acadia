import { View, Text, Image, StyleSheet } from 'react-native';
import { useMemo } from 'react';

import { useTheme, type Theme } from '../../context/ThemeContext';

/**
 * One avatar, used everywhere.
 *
 * <p>There were eight hand-rolled copies of this before -- four different corner
 * radii, three colour schemes, some uppercasing the letter and some not, and
 * not one of them able to show a photograph. This renders the photo when there
 * is one and falls back to the same letter-on-tint disc the app already had.
 *
 * <p>`tone` exists because the copies it replaces split into families: on the
 * gradient header the disc is translucent white, and on a page it is a brand
 * tint. `onBrand` is the third -- no disc at all, just a white letter, for when
 * the caller has already painted a solid brand circle underneath (the wheel's
 * hub). Flattening them would mean one of the three sitting on the wrong
 * ground.
 */
export type AvatarTone = 'onGradient' | 'onSurface' | 'onBrand';

export default function Avatar({
  uri, initial, size = 44, radius, tone = 'onSurface',
}: {
  uri?: string | null;
  initial?: string | null;
  size?: number;
  /** Defaults to a circle. Pass a smaller number for the squircle variants. */
  radius?: number;
  tone?: AvatarTone;
}) {
  const T = useTheme();
  const styles = useMemo(() => makeStyles(T), [T]);
  const r = radius ?? size / 2;
  const letter = (initial ?? '?').trim().charAt(0).toUpperCase() || '?';

  return (
    <View
      style={[
        tone === 'onGradient' ? styles.discOnGradient
          : tone === 'onBrand' ? styles.discOnBrand : styles.discOnSurface,
        { width: size, height: size, borderRadius: r },
      ]}
    >
      {uri ? (
        <Image
          source={{ uri }}
          style={{ width: size, height: size, borderRadius: r }}
          accessibilityIgnoresInvertColors
        />
      ) : (
        <Text
          style={[
            tone === 'onSurface' ? styles.letterOnSurface : styles.letterOnBrandSurface,
            { fontSize: Math.round(size * 0.4) },
          ]}
        >
          {letter}
        </Text>
      )}
    </View>
  );
}

const makeStyles = (T: Theme) => StyleSheet.create({
  discOnGradient: {
    alignItems: 'center', justifyContent: 'center', overflow: 'hidden',
    backgroundColor: 'rgba(255,255,255,0.16)',
    borderWidth: 1, borderColor: 'rgba(255,255,255,0.22)',
  },
  discOnSurface: {
    alignItems: 'center', justifyContent: 'center', overflow: 'hidden',
    backgroundColor: T.brand50, borderWidth: 1, borderColor: T.brand100,
  },
  discOnBrand: { alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  // Shared by onGradient and onBrand: both sit on a brand-coloured ground.
  letterOnBrandSurface: { fontWeight: '800', color: T.onBrand },
  letterOnSurface: { fontWeight: '700', color: T.brandInk },
});
