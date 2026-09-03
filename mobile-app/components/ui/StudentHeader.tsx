import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import LevelRing from './LevelRing';
import T from '../../constants/theme';

/**
 * The student home header: who they are, how far through the level they are,
 * and where the XP came from.
 *
 * Replaces three flat stat tiles (School XP / Parent XP / Streak) and a
 * separate level card. Those answered "what are my numbers"; this answers
 * "how am I doing", which is the question a child actually opens the app with.
 *
 * The bar tracks SCHOOL XP only, because school XP is what the level is
 * computed from in this codebase. Parent XP is shown beside it as its own
 * total rather than as a second segment of the same track -- a segment that
 * did not move the level would be a lie about how the level works.
 */

type Props = {
  firstName?: string;
  streak?: number;
  level: number;
  schoolXp: number;
  parentXp: number;
  /** Percent through the current level, from the server. */
  levelProgress: number;
  xpToNextLevel: number;
  onStreakPress?: () => void;
};

const dateLine = () =>
  new Date()
    .toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'short' })
    .toUpperCase();

export default function StudentHeader({
  firstName, streak = 0, level, schoolXp, parentXp,
  levelProgress, xpToNextLevel, onStreakPress,
}: Props) {
  const pct = Math.max(0, Math.min(100, levelProgress));

  return (
    <LinearGradient
      colors={[T.brand, T.brand700]}
      start={{ x: 0.1, y: 0 }}
      end={{ x: 0.9, y: 1 }}
      style={s.header}
    >
      <View style={s.greetRow}>
        <View style={s.avatar}>
          <Text style={s.avatarText}>{(firstName ?? '?').charAt(0).toUpperCase()}</Text>
        </View>

        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={s.date}>{dateLine()}</Text>
          <Text style={s.greeting} numberOfLines={1}>Hello, {firstName ?? 'there'}</Text>
        </View>

        <TouchableOpacity
          style={s.streak}
          onPress={onStreakPress}
          disabled={!onStreakPress}
          accessibilityRole={onStreakPress ? 'button' : undefined}
          accessibilityLabel={`${streak} day streak`}
        >
          <View style={s.streakDot} />
          <Text style={s.streakNum}>{streak}</Text>
        </TouchableOpacity>
      </View>

      <View
        style={s.levelCard}
        accessible
        accessibilityLabel={`Level ${level}, ${pct} percent through. ${xpToNextLevel} XP to level ${level + 1}.`}
      >
        <LevelRing level={level} pct={pct} />

        <View style={{ flex: 1, minWidth: 0, gap: 9 }}>
          <View>
            <Text style={s.levelTitle}>Scholar Level {level}</Text>
            <Text style={s.levelSub}>{xpToNextLevel} XP to Level {level + 1}</Text>
          </View>

          <View style={s.bar}>
            <View style={[s.barFill, { width: `${pct}%` }]} />
          </View>

          <View style={s.legend}>
            <Legend color={T.xpSchool} label="School" value={schoolXp} />
            <Legend color={T.xpParent} label="Parent" value={parentXp} />
          </View>
        </View>
      </View>
    </LinearGradient>
  );
}

function Legend({ color, label, value }: { color: string; label: string; value: number }) {
  return (
    <View style={s.legendItem}>
      <View style={[s.swatch, { backgroundColor: color }]} />
      <Text style={s.legendText}>{label} {value}</Text>
    </View>
  );
}

const s = StyleSheet.create({
  header: {
    paddingTop: 14, paddingHorizontal: 20, paddingBottom: 22,
    borderBottomLeftRadius: 30, borderBottomRightRadius: 30,
  },

  greetRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  avatar: {
    width: 44, height: 44, borderRadius: 15,
    backgroundColor: 'rgba(255,255,255,0.16)',
    borderWidth: 1, borderColor: 'rgba(255,255,255,0.22)',
    alignItems: 'center', justifyContent: 'center',
  },
  avatarText: { fontSize: 17, fontWeight: '800', color: T.onBrand },
  date: { fontSize: 12, fontWeight: '600', letterSpacing: 0.5, color: 'rgba(255,255,255,0.62)' },
  greeting: { fontSize: 21, fontWeight: '800', letterSpacing: -0.4, color: T.onBrand, marginTop: 2 },

  streak: {
    flexDirection: 'row', alignItems: 'center', gap: 6,
    paddingVertical: 7, paddingLeft: 9, paddingRight: 11,
    borderRadius: T.pill,
    backgroundColor: 'rgba(255,255,255,0.15)',
    borderWidth: 1, borderColor: 'rgba(255,255,255,0.2)',
  },
  streakDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: T.quest },
  streakNum: { fontSize: 13, fontWeight: '800', color: T.onBrand },

  levelCard: {
    flexDirection: 'row', alignItems: 'center', gap: 16,
    padding: 16, borderRadius: 22, marginTop: 16,
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderWidth: 1, borderColor: 'rgba(255,255,255,0.18)',
  },
  levelTitle: { fontSize: 15, fontWeight: '700', color: T.onBrand },
  levelSub: { fontSize: 12.5, fontWeight: '500', color: 'rgba(255,255,255,0.66)', marginTop: 1 },

  bar: {
    height: 7, borderRadius: T.pill,
    backgroundColor: 'rgba(255,255,255,0.18)', overflow: 'hidden',
  },
  barFill: { height: 7, borderRadius: T.pill, backgroundColor: T.xpSchool },

  legend: { flexDirection: 'row', gap: 14 },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  swatch: { width: 7, height: 7, borderRadius: 2 },
  legendText: { fontSize: 11.5, fontWeight: '600', color: 'rgba(255,255,255,0.8)' },
});
