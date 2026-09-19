import React, { useMemo } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, useWindowDimensions } from 'react-native';
import { SymbolView } from 'expo-symbols';
import { useRouter } from 'expo-router';

import { useTheme, type Theme } from '../../context/ThemeContext';
import type { WheelOption } from '../../constants/wheel';

/**
 * The home screen's navigator: destinations on the rim of a circle, the user at
 * its hub.
 *
 * <p>Built from plain Views rather than react-native-svg, deliberately. Every
 * icon in this app is an `expo-symbols` SymbolView, which is a native host view
 * and cannot be a child of an SVG; going SVG would mean either abandoning the
 * icon set for this one component or overlaying Views on top of the drawing, at
 * which point the SVG earns nothing. Views also keep `accessibilityRole`,
 * `numberOfLines` and font scaling, none of which SVG text has. The only thing
 * SVG would have bought is the orbit ring, and that is one bordered View.
 * (ProgressRing does use SVG -- because it needs a stroked arc. This needs none.)
 */

type Props = {
  options: WheelOption[];
  /** Rendered at the centre, usually the user's photo. */
  hub: React.ReactNode;
  onHubPress?: () => void;
  hubLabel?: string;
};

const SLOT_MAX = 78; // label box -- the widest part of a spoke, so collisions are measured on this
const SLOT_MIN = 56;
const GAP = 10;
const HUB = 96;
const LABEL_LINE = 15;
const PAGE_PAD = 20; // matches the dashboards' horizontal padding
const MIN_TOUCH = 44;

export default function UserWheel({ options, hub, onHubPress, hubLabel }: Props) {
  const T = useTheme();
  const styles = useMemo(() => makeStyles(T), [T]);
  const router = useRouter();
  const { width, fontScale } = useWindowDimensions();

  const n = options.length;

  const geometry = useMemo(() => {
    const available = Math.min(width - 2 * PAGE_PAD, 380);
    let item = n <= 4 ? 66 : n <= 6 ? 60 : 54;
    let slotW = SLOT_MAX;

    // Adjacent spokes are 2R·sin(π/n) apart along the chord. Require that to
    // clear a whole label box plus a gap, or labels collide from n=6 up. Also
    // keep the rim clear of the hub. n<=1 would divide by zero.
    const spread = n <= 1 ? 0 : (slotW + GAP) / (2 * Math.sin(Math.PI / n));
    const clearsHub = HUB / 2 + item / 2 + 18;
    let r = Math.max(spread, clearsHub);

    let boxW = 2 * r + slotW + 8;
    if (boxW > available) {
      const scale = available / boxW;
      r *= scale;
      boxW = available;
      item = Math.max(MIN_TOUCH, item * scale);
      slotW = Math.max(SLOT_MIN, slotW * scale);
    }

    // Shrinking to fit moves the rim in but left the label box its full width,
    // so eight spokes on a 320dp screen overlapped. Labels get whatever the
    // chord actually allows, and wrap to a third line when that is tight.
    const chord = n <= 1 ? Infinity : 2 * r * Math.sin(Math.PI / n);
    if (Number.isFinite(chord)) slotW = Math.min(slotW, Math.max(SLOT_MIN, chord - 2));
    const labelLines = slotW < 70 ? 3 : 2;

    return { r, item, slotW, labelLines, boxW, boxH: 2 * r + item + labelLines * LABEL_LINE + 12 };
  }, [width, n]);

  const go = (route: string) => router.push(route as never);

  // A circle cannot reflow. At large text sizes the rim would either overlap or
  // clip its own labels, so hand over to a plain grid -- same destinations, same
  // order, no geometry.
  if (fontScale > 1.3) {
    return (
      <View style={styles.fallbackGrid}>
        {onHubPress && (
          <TouchableOpacity style={styles.fallbackHub} onPress={onHubPress} accessibilityRole="imagebutton"
            accessibilityLabel={hubLabel ?? 'Your profile picture'}>
            {hub}
          </TouchableOpacity>
        )}
        {options.map((o, i) => (
          <TouchableOpacity
            key={o.key}
            style={styles.fallbackCard}
            onPress={() => go(o.route)}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityLabel={`${o.label}, ${i + 1} of ${n}`}
            accessibilityHint={`Opens ${o.label}`}
          >
            <SymbolView name={o.icon} tintColor={T.brand} size={24} />
            <Text style={styles.fallbackLabel}>{o.label}</Text>
          </TouchableOpacity>
        ))}
      </View>
    );
  }

  const { r, item, slotW, labelLines, boxW, boxH } = geometry;
  const cx = boxW / 2;
  const cy = boxH / 2;

  return (
    <View style={[styles.wrap, { width: boxW, height: boxH }]}>
      {/* The orbit itself, purely decorative -- borderColor, so it is a line and
          never mistaken for a text colour by the contrast gate. */}
      <View
        pointerEvents="none"
        style={[styles.orbit, {
          width: 2 * r, height: 2 * r, borderRadius: r,
          left: cx - r, top: cy - r,
        }]}
      />

      <TouchableOpacity
        style={[styles.hub, { width: HUB, height: HUB, borderRadius: HUB / 2, left: cx - HUB / 2, top: cy - HUB / 2 }]}
        onPress={onHubPress}
        disabled={!onHubPress}
        activeOpacity={0.85}
        accessibilityRole="imagebutton"
        accessibilityLabel={hubLabel ?? 'Your profile picture'}
        accessibilityHint={onHubPress ? 'Double tap to change your picture' : undefined}
      >
        {hub}
      </TouchableOpacity>

      {options.map((o, i) => {
        // Start at 12 o'clock and go clockwise, which is also the order a
        // screen reader walks them in.
        const angle = -Math.PI / 2 + (2 * Math.PI * i) / n;
        const left = cx + r * Math.cos(angle) - slotW / 2;
        // Align the CIRCLE on the orbit, not the slot box: the label hangs
        // below, so centring the box would lift the 3 and 9 o'clock spokes.
        const top = cy + r * Math.sin(angle) - item / 2;

        return (
          <TouchableOpacity
            key={o.key}
            style={[styles.slot, { width: slotW, left, top }]}
            onPress={() => go(o.route)}
            activeOpacity={0.85}
            hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
            accessibilityRole="button"
            accessibilityLabel={`${o.label}, ${i + 1} of ${n}`}
            accessibilityHint={`Opens ${o.label}`}
          >
            <View style={[styles.spoke, { width: item, height: item, borderRadius: item / 2 }]}>
              <SymbolView name={o.icon} tintColor={T.brand} size={Math.round(item * 0.42)} />
            </View>
            <Text style={styles.label} numberOfLines={labelLines}>{o.label}</Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const makeStyles = (T: Theme) => StyleSheet.create({
  wrap: { alignSelf: 'center', marginVertical: 8 },
  orbit: { position: 'absolute', borderWidth: 1, borderColor: T.line },
  hub: {
    position: 'absolute', alignItems: 'center', justifyContent: 'center',
    backgroundColor: T.surface, borderWidth: 2, borderColor: T.line, overflow: 'hidden',
  },
  slot: { position: 'absolute', alignItems: 'center' },
  spoke: {
    alignItems: 'center', justifyContent: 'center',
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.line,
  },
  label: { marginTop: 4, fontSize: 12, lineHeight: 15, fontWeight: '600', color: T.text, textAlign: 'center' },
  fallbackGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, justifyContent: 'center' },
  fallbackHub: {
    width: '100%', alignItems: 'center', justifyContent: 'center',
    paddingVertical: 12,
  },
  fallbackCard: {
    flexGrow: 1, flexBasis: '45%', minHeight: MIN_TOUCH + 28,
    alignItems: 'center', justifyContent: 'center', gap: 6,
    backgroundColor: T.surface, borderRadius: T.rSm,
    borderWidth: 1, borderColor: T.line, paddingVertical: 14,
  },
  fallbackLabel: { fontSize: 13, fontWeight: '600', color: T.text, textAlign: 'center' },
});
