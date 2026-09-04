import React from 'react';
import { View, Text } from 'react-native';
import GradientHeader, { HeaderCard, headerStyles as h } from './GradientHeader';
import ProgressRing from './ProgressRing';
import { Legend } from './StudentHeader';
import T from '../../constants/theme';

/**
 * The parent home header.
 *
 * A parent opens the app to ask about their child, not themselves, so the card
 * is the child's progress -- the same ring and split the child sees, named for
 * them. The greeting is the only part that is about the parent.
 */

type Props = {
  parentName?: string;
  childName?: string;
  level: number;
  schoolXp: number;
  parentXp: number;
  levelProgress: number;
  xpToNextLevel: number;
};

export default function ParentHeader({
  parentName, childName, level, schoolXp, parentXp, levelProgress, xpToNextLevel,
}: Props) {
  const pct = Math.max(0, Math.min(100, levelProgress));
  const who = childName || 'Your child';

  return (
    <GradientHeader
      initial={parentName}
      greeting={`Hello, ${parentName ?? 'there'}`}
    >
      <HeaderCard label={`${who} is Scholar Level ${level}, ${pct} percent through.`}>
        <ProgressRing value={level} label="LVL" pct={pct} />

        <View style={{ flex: 1, minWidth: 0, gap: 9 }}>
          <View>
            <Text style={h.title} numberOfLines={1}>{who} · Level {level}</Text>
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
