import React, { useMemo } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Linking, Image } from 'react-native';
import { SymbolView } from 'expo-symbols';

import { useTheme, type Theme } from '../../context/ThemeContext';

/**
 * The player on a phone, for now: a card that opens the video in YouTube.
 *
 * <h2>Why this is not an embed</h2>
 *
 * <p>Playing inside the app needs `react-native-webview` or
 * `react-native-youtube-iframe`, and neither is installed. I wrote the webview
 * version first and CI refused it correctly: Metro resolves `require()` when it
 * bundles, not when the branch runs, so a native-only import behind a
 * `Platform.OS` check still has to exist -- and that one did not. It took the whole
 * web bundle down with it, which is the honest failure.
 *
 * <p>Adding the module is not a small thing either. It is a native dependency, so
 * it cannot reach the installed build over the air; it needs a new APK. That is a
 * decision about releases rather than about this file, so the split is explicit:
 * this file exists so the web build -- the one this feature was asked to work on
 * -- bundles and plays inline, and the phone gets something honest in the
 * meantime.
 *
 * <p>What that costs, stated plainly: "Watched ✓" cannot be recorded here. YouTube
 * in its own app has no way to tell us the video ended. Marking it watched because
 * the link was tapped would be a different claim wearing the same tick -- so
 * nothing is recorded, and the list simply says the video is there.
 *
 * <p>Metro picks this file over VideoPlayer.tsx on iOS and Android automatically,
 * so no caller knows the difference and no `Platform.OS` check is needed anywhere
 * else.
 */

type Props = {
  youtubeId: string;
  embedUrl: string;
  title: string;
  onFinished: () => void;
};

export default function VideoPlayer({ youtubeId, title }: Props) {
  const T = useTheme();
  const styles = useMemo(() => makeStyles(T), [T]);

  const open = () => {
    // The app if it is installed, the browser if it is not. Linking resolves
    // both from the same https address.
    Linking.openURL(`https://www.youtube.com/watch?v=${youtubeId}`).catch(() => {});
  };

  return (
    <TouchableOpacity
      style={styles.frame}
      onPress={open}
      accessibilityRole="button"
      accessibilityLabel={`Play ${title} on YouTube. Opens the YouTube app.`}
    >
      <Image
        source={{ uri: `https://i.ytimg.com/vi/${youtubeId}/hqdefault.jpg` }}
        style={StyleSheet.absoluteFill}
        resizeMode="cover"
      />
      <View style={styles.scrim} />
      <View style={styles.badge}>
        <SymbolView
          name={{ ios: 'play.fill', android: 'play_arrow', web: 'play_arrow' }}
          tintColor="#fff"
          size={26}
        />
      </View>
      <Text style={styles.caption}>Watch on YouTube</Text>
    </TouchableOpacity>
  );
}

const makeStyles = (T: Theme) => StyleSheet.create({
  frame: {
    width: '100%',
    aspectRatio: 16 / 9,
    borderRadius: 12,
    overflow: 'hidden',
    backgroundColor: T.line,
    alignItems: 'center',
    justifyContent: 'center',
  },
  scrim: {
    position: 'absolute', left: 0, top: 0, right: 0, bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.35)',
  },
  badge: {
    width: 56, height: 56, borderRadius: 28,
    backgroundColor: 'rgba(0,0,0,0.55)',
    alignItems: 'center', justifyContent: 'center',
  },
  caption: {
    marginTop: 10, color: '#fff', fontSize: 13, fontWeight: '700',
  },
});
