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
    // 34, not a token gap: the dashed connectors run through this space, and
    // at 18 they came out 5-6px long on the low spoke counts -- specks rather
    // than lines. Checked across n=2..8 at 320/360/412dp.
    const clearsHub = HUB / 2 + item / 2 + 34;
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
      {/* A dashed spoke out to each destination. Drawn as a thin bordered
          View rotated to the angle rather than an SVG path: the wheel is
          already Views, and borderStyle 'dashed' gives the dashes for free.
          Rotation is about the View's own centre, so each line is positioned
          at the midpoint of the run it covers -- no transformOrigin needed. */}
      {options.map((o, i) => {
        const angle = -Math.PI / 2 + (2 * Math.PI * i) / n;
        const from = HUB / 2 + 4;          // clear of the hub
        const to = r - item / 2 - 4;       // stop short of the spoke
        const length = Math.max(0, to - from);
        const mid = from + length / 2;
        return (
          <View
            key={`connector-${o.key}`}
            pointerEvents="none"
            style={[styles.connector, {
              width: length,
              left: cx + mid * Math.cos(angle) - length / 2,
              top: cy + mid * Math.sin(angle),
              transform: [{ rotate: `${(angle * 180) / Math.PI}deg` }],
            }]}
          />
        );
      })}

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
  // marginTop gives the wheel room to breathe under the summary card.
  wrap: { alignSelf: 'center', marginTop: 18, marginBottom: 8 },
  connector: {
    position: 'absolute', height: 0,
    borderTopWidth: 1, borderStyle: 'dashed', borderColor: T.lineStrong,
  },
  // Solid brand disc, not a tint: the hub is the one thing on this screen that
  // should read as the person rather than as a control.
  hub: {
    position: 'absolute', alignItems: 'center', justifyContent: 'center',
    backgroundColor: T.brand, overflow: 'hidden',
    shadowColor: '#101828', shadowOpacity: 0.18, shadowRadius: 12,
    shadowOffset: { width: 0, height: 6 }, elevation: 6,
  },
  slot: { position: 'absolute', alignItems: 'center' },
  // quest50/quest200 rather than the brand tints: the quest family is the
  // design system's existing "something good is waiting for you" warm accent,
  // so the rim stays warm on every palette instead of turning pale teal on one
  // and pale violet on another.
  spoke: {
    alignItems: 'center', justifyContent: 'center',
    backgroundColor: T.quest50, borderWidth: 1, borderColor: T.quest200,
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
