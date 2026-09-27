import React, { useCallback, useContext, useEffect, useMemo, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, ActivityIndicator, TouchableOpacity, Modal, Platform, Image,
} from 'react-native';
import { SymbolView } from 'expo-symbols';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { DataContext } from './_layout';
import { getStudentVideos, getParentVideos, markVideoWatched } from '../../services/api';
import { useTheme, type Theme } from '../../context/ThemeContext';
import VideoPlayer from '../../components/ui/VideoPlayer';

/**
 * Videos a teacher has pointed this class at.
 *
 * <p>One screen for pupils and parents. A pupil can play and is recorded as having
 * watched; a parent sees the same list with the same "Watched" marks and cannot
 * change them -- the mark is about their child, not about them.
 */

type Video = {
  id: string;
  youtubeId: string;
  embedUrl: string;
  title: string;
  thumbnailUrl: string;
  note?: string | null;
  subjectCode: string;
  sectionName?: string | null;
  watched: boolean;
};

export default function VideosScreen() {
  const T = useTheme();
  const styles = useMemo(() => makeStyles(T), [T]);
  const insets = useSafeAreaInsets();
  const { role } = useContext(DataContext);

  const [videos, setVideos] = useState<Video[] | null>(null);
  const [subject, setSubject] = useState<string | null>(null);
  const [open, setOpen] = useState<Video | null>(null);
  const [sheetUp, setSheetUp] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isParent = role === 'PARENT';

  const load = useCallback(async () => {
    try {
      const list = isParent ? await getParentVideos(undefined) : await getStudentVideos(undefined);
      setVideos(list);
      setError(null);
    } catch {
      setVideos([]);
      setError('Could not load videos. Pull down to try again.');
    }
  }, [isParent]);

  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { void load(); }, [load]);

  /** The subjects actually present, so the filter never offers an empty answer. */
  const subjects = useMemo(() => {
    const seen: string[] = [];
    (videos ?? []).forEach((v) => {
      if (v.subjectCode && !seen.includes(v.subjectCode)) seen.push(v.subjectCode);
    });
    return seen;
  }, [videos]);

  const shown = useMemo(
    () => (videos ?? []).filter((v) => subject === null || v.subjectCode === subject),
    [videos, subject],
  );

  /**
   * The player said the video reached the end.
   *
   * <p>Marked locally straight away rather than waiting for the round trip: the
   * child has just watched it and the tick should be there when the sheet closes.
   * The server is idempotent, so a failure here costs the mark until the next load
   * and nothing worse.
   */
  const onFinished = useCallback(async (video: Video) => {
    setVideos((prev) => (prev ?? []).map((v) => (v.id === video.id ? { ...v, watched: true } : v)));
    try {
      await markVideoWatched(video.id);
    } catch {
      // Left as it is on screen. Re-opening the list asks the server again.
    }
  }, []);

  const openVideo = (video: Video) => {
    setOpen(video);
    setSheetUp(true);
  };

  // Two pieces of state, as on the hand-in sheet: a Modal is still on screen for
  // the length of its slide-out, so clearing the video to dismiss it empties the
  // sheet in front of the child.
  const closeSheet = () => setSheetUp(false);

  if (videos === null) {
    return (
      <View style={styles.centre}>
        <ActivityIndicator color={T.brand} />
      </View>
    );
  }

  return (
    <>
      <ScrollView
        style={styles.page}
        contentContainerStyle={{ paddingBottom: insets.bottom + T.space.xl }}
      >
        {subjects.length > 1 && (
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filterRow}
            contentContainerStyle={{ gap: 8, paddingHorizontal: 16 }}>
            <Chip label="All" on={subject === null} onPress={() => setSubject(null)} T={T} />
            {subjects.map((code) => (
              <Chip key={code} label={code} on={subject === code}
                onPress={() => setSubject(code)} T={T} />
            ))}
          </ScrollView>
        )}

        {!!error && (
          <Text style={styles.error} accessibilityRole="alert">{error}</Text>
        )}

        {shown.length === 0 ? (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyTitle}>No videos yet</Text>
            <Text style={styles.emptyBody}>
              {isParent ? 'Anything a teacher shares will appear here.'
                        : 'Your teacher will add videos here.'}
            </Text>
          </View>
        ) : (
          shown.map((video) => (
            <TouchableOpacity
              key={video.id}
              style={styles.card}
              onPress={() => openVideo(video)}
              accessibilityRole="button"
              accessibilityLabel={
                `${video.title}. ${video.subjectCode}. ${video.watched ? 'Watched.' : 'Not watched yet.'}`
              }
            >
              <View style={styles.thumbWrap}>
                <ThumbNail uri={video.thumbnailUrl} T={T} />
                <View style={styles.playBadge}>
                  <SymbolView name={{ ios: 'play.fill', android: 'play_arrow', web: 'play_arrow' }}
                    tintColor="#fff" size={18} />
                </View>
              </View>
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text style={styles.cardTitle} numberOfLines={2}>{video.title}</Text>
                <Text style={styles.cardMeta} numberOfLines={1}>
                  {video.subjectCode}{video.sectionName ? ` · ${video.sectionName}` : ''}
                </Text>
                {!!video.note && (
                  <Text style={styles.cardNote} numberOfLines={2}>{video.note}</Text>
                )}
                {video.watched && (
                  <Text style={styles.watched} data-watched-mark>Watched ✓</Text>
                )}
              </View>
            </TouchableOpacity>
          ))
        )}
      </ScrollView>

      <Modal visible={sheetUp} transparent animationType="slide" onRequestClose={closeSheet}>
        <View style={styles.modalOverlay}>
          <View style={[styles.modalCard, { paddingBottom: T.space.lg + insets.bottom }]}>
            <Text style={styles.modalTitle} numberOfLines={2}>{open?.title}</Text>
            {!!open && (
              <VideoPlayer
                youtubeId={open.youtubeId}
                embedUrl={open.embedUrl}
                title={open.title}
                onFinished={() => onFinished(open)}
              />
            )}
            {!!open?.note && <Text style={styles.modalNote}>{open.note}</Text>}
            <TouchableOpacity
              style={styles.doneBtn}
              onPress={closeSheet}
              accessibilityRole="button"
              accessibilityLabel="Close"
            >
              <Text style={styles.doneBtnText}>Close</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    </>
  );
}

function Chip({ label, on, onPress, T }: {
  label: string; on: boolean; onPress: () => void; T: Theme;
}) {
  const styles = makeStyles(T);
  return (
    <TouchableOpacity
      style={[styles.chip, on && styles.chipOn]}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected: on }}
    >
      <Text style={[styles.chipText, on && styles.chipTextOn]}>{label}</Text>
    </TouchableOpacity>
  );
}

/**
 * The still frame. Built from the video id by the server, so there is no stored
 * address to go stale -- and if YouTube's CDN is unreachable the card still reads,
 * because the title and note are text beside it rather than on top of it.
 */
function ThumbNail({ uri, T }: { uri: string; T: Theme }) {
  const styles = makeStyles(T);
  if (Platform.OS === 'web') {
    // A plain img rather than RN's Image: this one is decorative beside a title
    // that already says what the video is, so it takes an empty alt, and RN Web
    // renders Image as a background-image div which cannot carry one.
    return React.createElement('img', {
      src: uri, alt: '', style: { width: 108, height: 72, objectFit: 'cover', display: 'block' },
    });
  }
  return <Image source={{ uri }} style={styles.thumb} />;
}

const makeStyles = (T: Theme) => StyleSheet.create({
  page: { flex: 1, backgroundColor: T.bg, paddingTop: 12 },
  centre: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: T.bg },
  filterRow: { flexGrow: 0, marginBottom: 12 },
  chip: {
    minHeight: 36, justifyContent: 'center', paddingHorizontal: 14, borderRadius: T.pill,
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.line,
  },
  chipOn: { backgroundColor: T.brand50, borderColor: T.brand },
  chipText: { fontSize: 13, fontWeight: '600', color: T.text2 },
  chipTextOn: { color: T.brand },
  card: {
    flexDirection: 'row', gap: 12, alignItems: 'flex-start',
    backgroundColor: T.surface, borderRadius: 14, padding: 12,
    borderWidth: 1, borderColor: T.line, marginHorizontal: 16, marginBottom: 12,
  },
  thumbWrap: { width: 108, height: 72, borderRadius: 8, overflow: 'hidden', backgroundColor: T.line },
  thumb: { width: 108, height: 72 },
  playBadge: {
    position: 'absolute', left: 0, top: 0, right: 0, bottom: 0,
    alignItems: 'center', justifyContent: 'center',
  },
  cardTitle: { fontSize: 14, fontWeight: '700', color: T.text, lineHeight: 19 },
  cardMeta: { fontSize: 11, color: T.text3, marginTop: 3 },
  cardNote: { fontSize: 12, color: T.text2, marginTop: 5, lineHeight: 17 },
  watched: { fontSize: 12, fontWeight: '700', color: T.successInk, marginTop: 6 },
  emptyCard: {
    backgroundColor: T.surface, borderRadius: 14, borderWidth: 1, borderColor: T.line,
    marginHorizontal: 16, paddingVertical: 16, paddingHorizontal: 24,
  },
  emptyTitle: { fontSize: 15, fontWeight: '600', color: T.text },
  emptyBody: { fontSize: 13, color: T.text3, marginTop: 4 },
  error: { fontSize: 13, color: T.dangerInk, marginHorizontal: 16, marginBottom: 12 },
  modalOverlay: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.45)' },
  modalCard: {
    backgroundColor: T.surface, borderTopLeftRadius: 18, borderTopRightRadius: 18,
    padding: T.space.lg, gap: 12,
  },
  modalTitle: { fontSize: 16, fontWeight: '700', color: T.text },
  modalNote: { fontSize: 13, color: T.text2, lineHeight: 18 },
  doneBtn: {
    minHeight: 44, borderRadius: T.pill, backgroundColor: T.brand50,
    alignItems: 'center', justifyContent: 'center',
  },
  doneBtnText: { color: T.brand, fontWeight: '700', fontSize: 14 },
});
