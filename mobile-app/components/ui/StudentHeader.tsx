import React from 'react';
import { View, Text, TouchableOpacity } from 'react-native';
import GradientHeader, { HeaderCard, headerStyles as h } from './GradientHeader';
import ProgressRing from './ProgressRing';
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

export default function StudentHeader({
  firstName, streak = 0, level, schoolXp, parentXp,
  levelProgress, xpToNextLevel, onStreakPress,
}: Props) {
  const pct = Math.max(0, Math.min(100, levelProgress));

  return (
    <GradientHeader
      initial={firstName}
      greeting={`Hello, ${firstName ?? 'there'}`}
      trailing={
        <TouchableOpacity
          style={h.pill}
          onPress={onStreakPress}
          disabled={!onStreakPress}
          accessibilityRole={onStreakPress ? 'button' : undefined}
          accessibilityLabel={`${streak} day streak`}
        >
          <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: T.quest }} />
          <Text style={{ fontSize: 13, fontWeight: '800', color: T.onBrand }}>{streak}</Text>
        </TouchableOpacity>
      }
    >
      <HeaderCard label={`Level ${level}, ${pct} percent through. ${xpToNextLevel} XP to level ${level + 1}.`}>
        <ProgressRing value={level} label="LVL" pct={pct} />

        <View style={{ flex: 1, minWidth: 0, gap: 9 }}>
          <View>
            <Text style={h.title}>Scholar Level {level}</Text>
            <Text style={h.sub}>{xpToNextLevel} XP to Level {level + 1}</Text>
          </View>

          <View style={h.bar}>
            <View style={[h.barFill, { width: `${pct}%`, backgroundColor: T.xpSchool }]} />
          </View>

          <View style={h.legend}>
            <Legend color={T.xpSchool} label="School" value={schoolXp} />
            <Legend color={T.xpParent} label="Parent" value={parentXp} />
          </View>
        </View>
      </HeaderCard>
    </GradientHeader>
  );
}

export function Legend({ color, label, value }: { color: string; label: string; value: number }) {
  return (
    <View style={h.legendItem}>
      <View style={[h.swatch, { backgroundColor: color }]} />
      <Text style={h.legendText}>{label} {value}</Text>
    </View>
  );
}
