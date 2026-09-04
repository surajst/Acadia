import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { SymbolView, SymbolViewProps } from 'expo-symbols';
import T from '../../constants/theme';

/**
 * The two empty states, which are not the same thing.
 *
 * `firstRun` is "you have not made one yet" -- the moment to explain what the
 * screen is for and hand over the first action. `noResults` is "your filter
 * matched nothing" -- which needs to name the filter and offer to clear it,
 * because telling someone they have no students when they have 22 and a typo
 * is how a screen loses trust.
 *
 * Both beat what several screens do today, which is render nothing at all and
 * read as broken.
 */

type Props = {
  kind?: 'firstRun' | 'noResults';
  icon?: SymbolViewProps['name'];
  title: string;
  body: string;
  actionLabel?: string;
  onAction?: () => void;
};

export default function EmptyState({
  kind = 'firstRun',
  icon,
  title,
  body,
  actionLabel,
  onAction,
}: Props) {
  const fallback: SymbolViewProps['name'] = kind === 'noResults'
    ? { ios: 'magnifyingglass', android: 'search', web: 'search' }
    : { ios: 'tray', android: 'inbox', web: 'inbox' };

  return (
    <View style={s.wrap}>
      <View style={s.iconWrap}>
        <SymbolView name={icon ?? fallback} tintColor={T.text4} size={26} />
      </View>
      <Text style={s.title}>{title}</Text>
      <Text style={s.body}>{body}</Text>
      {actionLabel && onAction && (
        <TouchableOpacity style={s.action} onPress={onAction} accessibilityRole="button">
          <Text style={s.actionText}>{actionLabel}</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

const s = StyleSheet.create({
  wrap: {
    alignItems: 'center',
    paddingVertical: T.space.xxl,
    paddingHorizontal: T.space.xl,
    gap: T.space.sm,
  },
  iconWrap: {
    width: 52, height: 52, borderRadius: T.rSm,
    backgroundColor: T.track,
    alignItems: 'center', justifyContent: 'center',
    marginBottom: T.space.xs,
  },
  title: { ...T.type.heading, color: T.text, textAlign: 'center' },
  body: {
    ...T.type.body, color: T.text3, textAlign: 'center',
    maxWidth: 300,
  },
  action: {
    marginTop: T.space.md,
    minHeight: 44,
    justifyContent: 'center',
    paddingHorizontal: T.space.xl,
    borderRadius: T.rXs,
    backgroundColor: T.brand,
  },
  actionText: { ...T.type.label, color: '#FFFFFF' },
});
