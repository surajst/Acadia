import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Svg, { Circle } from 'react-native-svg';
import T from '../../constants/theme';

/**
 * The level ring on the student header.
 *
 * A real stroked arc rather than the prototype's conic-gradient trick: a conic
 * gradient has no rounded cap and cannot animate a value, and react-native has
 * no equivalent anyway.
 */

const SIZE = 68;
const STROKE = 7;
const R = (SIZE - STROKE) / 2;
const C = 2 * Math.PI * R;

export default function LevelRing({ level, pct }: { level: number; pct: number }) {
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
          stroke={T.xpSchool} strokeWidth={STROKE} fill="none"
          strokeDasharray={`${(C * clamped) / 100} ${C}`}
          strokeLinecap="round"
          // start at 12 o'clock rather than 3
          transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}
        />
      </Svg>
      <View style={s.inner} pointerEvents="none">
        <Text style={s.level}>{level}</Text>
        <Text style={s.lvl}>LVL</Text>
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
  level: { fontSize: 20, fontWeight: '800', color: T.onBrand, lineHeight: 23 },
  lvl: { fontSize: 8, fontWeight: '800', letterSpacing: 1, color: 'rgba(255,255,255,0.6)' },
});
