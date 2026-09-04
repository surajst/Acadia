import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Svg, { Circle } from 'react-native-svg';
import T from '../../constants/theme';

/**
 * The ring on a gradient header: a big figure, a small label under it, and an
 * arc showing how far through something you are. The student header uses it
 * for scholar level; the teacher header for attendance marked today.
 *
 * A real stroked arc rather than the prototype's conic-gradient trick: a conic
 * gradient has no rounded cap and cannot animate a value, and react-native has
 * no equivalent anyway.
 */

const SIZE = 68;
const STROKE = 7;
const R = (SIZE - STROKE) / 2;
const C = 2 * Math.PI * R;

export default function ProgressRing({ value, label = 'LVL', pct, color = T.xpSchool }: {
  value: number | string;
  label?: string;
  pct: number;
  color?: string;
}) {
  const clamped = Math.max(0, Math.min(100, pct));

  return (
    <View style={s.wrap}>
      <Svg width={SIZE} height={SIZE}>
        <Circle
          cx={SIZE / 2} cy={SIZE / 2} r={R}
          stroke="rgba(255,255,255,0.18)" strokeWidth={STROKE} fill="none"
        />
        <Circle
          cx={SIZE / 2} cy={SIZE / 2} r={R}
          stroke={color} strokeWidth={STROKE} fill="none"
          strokeDasharray={`${(C * clamped) / 100} ${C}`}
          strokeLinecap="round"
          // start at 12 o'clock rather than 3
          transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}
        />
      </Svg>
      <View style={s.inner} pointerEvents="none">
        <Text style={s.value}>{value}</Text>
        <Text style={s.label}>{label}</Text>
      </View>
    </View>
  );
}

const s = StyleSheet.create({
  wrap: { width: SIZE, height: SIZE, alignItems: 'center', justifyContent: 'center' },
  inner: {
    position: 'absolute', width: SIZE - STROKE * 2, height: SIZE - STROKE * 2,
    borderRadius: (SIZE - STROKE * 2) / 2, backgroundColor: T.brandRing,
    alignItems: 'center', justifyContent: 'center',
  },
  value: { fontSize: 20, fontWeight: '800', color: T.onBrand, lineHeight: 23 },
  label: { fontSize: 8, fontWeight: '800', letterSpacing: 1, color: 'rgba(255,255,255,0.6)' },
});
