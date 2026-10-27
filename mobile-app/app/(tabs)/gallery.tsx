import React, { useCallback, useContext, useEffect, useMemo, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, ActivityIndicator, TouchableOpacity, Modal, Platform, Image,
  useWindowDimensions,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { DataContext } from './_layout';
import {
  getGalleryStatus, getGalleryAlbums, getGalleryPhotos, uploadGalleryPhoto, removeGalleryPhoto,
  type GalleryAlbum, type GalleryPhoto,
} from '../../services/api';
import { useTheme, type Theme } from '../../context/ThemeContext';
import { ask, notify, notifySuccess } from '../../utils/notify';

/**
 * Photos from school.
 *
 * <p>One screen for pupils, parents and teachers, because the server already
 * decides who sees which album and which photos -- a photo of a child without
 * consent never reaches a family at all. A teacher can also add photos from the
 * phone's library to albums for their own classes, and remove them; albums
 * themselves are made on the web portal, where the consent check sits.
 *
 * <p>The image links expire after a few minutes, so nothing here is cached
 * between visits: opening an album asks again.
 */
export default function GalleryScreen() {
  const T = useTheme();
  const styles = useMemo(() => makeStyles(T), [T]);
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const { role } = useContext(DataContext);
  const isTeacher = role === 'TEACHER';

  const [albums, setAlbums] = useState<GalleryAlbum[] | null>(null);
  const [notSetUp, setNotSetUp] = useState<string | null>(null);
  const [album, setAlbum] = useState<GalleryAlbum | null>(null);
  const [photos, setPhotos] = useState<GalleryPhoto[] | null>(null);
  const [viewing, setViewing] = useState<GalleryPhoto | null>(null);
  const [progress, setProgress] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadAlbums = useCallback(async () => {
    try {
      const [status, list] = await Promise.all([getGalleryStatus(), getGalleryAlbums()]);
      setNotSetUp(status.available ? null : status.message);
      setAlbums(list);
      setError(null);
    } catch {
      setAlbums([]);
      setError('Could not load photos. Go back and try again.');
    }
  }, []);

  const loadPhotos = useCallback(async (which: GalleryAlbum) => {
    setPhotos(null);
    try {
      setPhotos(await getGalleryPhotos(which.id));
      setError(null);
    } catch {
      setPhotos([]);
      setError('Could not load this album. Go back and try again.');
    }
  }, []);

  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { void loadAlbums(); }, [loadAlbums]);

  const openAlbum = (which: GalleryAlbum) => {
    setAlbum(which);
    void loadPhotos(which);
  };

  const closeAlbum = () => {
    setAlbum(null);
    setPhotos(null);
    void loadAlbums();
  };

  const addPhotos = async () => {
    if (!album) return;
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      notify('Photos need permission', 'Allow access to your photo library in Settings to add photos.');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsMultipleSelection: true,
      selectionLimit: 20,
      quality: 0.85,
      // An iPhone keeps photos in its own format, which the school's server does
      // not read. Asking for the compatible form makes the picker hand back JPEG.
      preferredAssetRepresentationMode: ImagePicker.UIImagePickerPreferredAssetRepresentationMode.Compatible,
    });
    if (result.canceled || !result.assets.length) return;

    const failures: string[] = [];
    for (let i = 0; i < result.assets.length; i++) {
      setProgress(`Uploading ${i + 1} of ${result.assets.length}…`);
      const asset = result.assets[i];
      try {
        await uploadGalleryPhoto(album.id, { uri: asset.uri, mimeType: asset.mimeType ?? undefined });
      } catch (e) {
        failures.push(e instanceof Error ? e.message : 'Upload failed.');
      }
    }
    setProgress(null);
    const added = result.assets.length - failures.length;
    if (failures.length) {
      notify(added ? `${added} added, ${failures.length} not added` : 'No photos were added', failures[0]);
    } else {
      notifySuccess(added === 1 ? 'Photo added' : `${added} photos added`);
    }
    await loadPhotos(album);
  };

  const removePhoto = async (photo: GalleryPhoto) => {
    const sure = await ask('Remove this photo?', 'It is deleted for everyone.', {
      confirmLabel: 'Remove', destructive: true,
    });
    if (!sure || !album) return;
    try {
      await removeGalleryPhoto(photo.id);
      setViewing(null);
      await loadPhotos(album);
    } catch {
      notify('Could not remove the photo', 'Try again in a moment.');
    }
  };

  // ── One album ────────────────────────────────────────────────────────────
  if (album) {
    const columns = width >= 700 ? 5 : 3;
    const gap = 6;
    const size = Math.floor((Math.min(width, 900) - 32 - gap * (columns - 1)) / columns);
    const canManage = isTeacher && album.canAddPhotos && !notSetUp;
    return (
      <>
        <ScrollView style={styles.page} contentContainerStyle={{ paddingBottom: insets.bottom + T.space.xl }}>
          <View style={styles.albumHeader}>
            <TouchableOpacity onPress={closeAlbum} accessibilityRole="button" accessibilityLabel="All albums">
              <Text style={styles.backLink}>← All albums</Text>
            </TouchableOpacity>
            <Text style={styles.albumTitle} accessibilityRole="header">{album.title}</Text>
            <Text style={styles.cardMeta}>{album.audienceLabel}</Text>
            {canManage && (
              <TouchableOpacity style={styles.primaryBtn} onPress={addPhotos} disabled={!!progress}
                accessibilityRole="button" accessibilityLabel="Add photos">
                <Text style={styles.primaryBtnText}>{progress ?? 'Add photos'}</Text>
              </TouchableOpacity>
            )}
          </View>

          {!!error && <Text style={styles.error} accessibilityRole="alert">{error}</Text>}

          {photos === null ? (
            <ActivityIndicator color={T.brand} style={{ marginTop: 24 }} />
          ) : photos.length === 0 ? (
            <View style={styles.emptyCard}>
              <Text style={styles.emptyTitle}>No photos in this album yet</Text>
              <Text style={styles.emptyBody}>
                {canManage ? 'Use “Add photos” to upload some.' : 'Check back after the event.'}
              </Text>
            </View>
          ) : (
            <View style={[styles.grid, { gap }]}>
              {photos.map((photo) => (
                <TouchableOpacity key={photo.id} onPress={() => setViewing(photo)}
                  accessibilityRole="button" accessibilityLabel="Open photo">
                  <Picture uri={photo.thumbUrl} width={size} height={size} />
                  {isTeacher && photo.withheldForConsent && (
                    <Text style={styles.withheld} numberOfLines={1}>Hidden from families</Text>
                  )}
                </TouchableOpacity>
              ))}
            </View>
          )}
        </ScrollView>

        <Modal visible={!!viewing} transparent animationType="fade" onRequestClose={() => setViewing(null)}>
          <View style={styles.viewer}>
            {!!viewing && (
              <Picture uri={viewing.displayUrl} width={width - 16}
                height={Math.round((width - 16) * ((viewing.height ?? 3) / (viewing.width ?? 4)))} />
            )}
            <View style={[styles.viewerBar, { paddingBottom: insets.bottom + 12 }]}>
              {canManage && !!viewing && (
                <TouchableOpacity style={styles.viewerBtn} onPress={() => removePhoto(viewing)}
                  accessibilityRole="button" accessibilityLabel="Remove photo">
                  <Text style={styles.viewerBtnText}>Remove</Text>
                </TouchableOpacity>
              )}
              <TouchableOpacity style={styles.viewerBtn} onPress={() => setViewing(null)}
                accessibilityRole="button" accessibilityLabel="Close">
                <Text style={styles.viewerBtnText}>Close</Text>
              </TouchableOpacity>
            </View>
          </View>
        </Modal>
      </>
    );
  }

  // ── Albums ───────────────────────────────────────────────────────────────
  if (albums === null) {
    return (
      <View style={styles.centre}>
        <ActivityIndicator color={T.brand} />
      </View>
    );
  }

  return (
    <ScrollView style={styles.page} contentContainerStyle={{ paddingBottom: insets.bottom + T.space.xl }}>
      {!!notSetUp && <Text style={styles.notice}>{notSetUp}</Text>}
      {!!error && <Text style={styles.error} accessibilityRole="alert">{error}</Text>}

      {albums.length === 0 ? (
        <View style={styles.emptyCard}>
          <Text style={styles.emptyTitle}>No albums yet</Text>
          <Text style={styles.emptyBody}>
            {isTeacher ? 'Make an album on the web portal, then add photos from here.'
              : 'Photos your school shares with you will appear here.'}
          </Text>
        </View>
      ) : (
        albums.map((a) => (
          <TouchableOpacity key={a.id} style={styles.card} onPress={() => openAlbum(a)}
            accessibilityRole="button"
            accessibilityLabel={`${a.title}. ${a.audienceLabel}. ${a.photoCount === 1 ? '1 photo' : `${a.photoCount} photos`}.`}>
            <View style={styles.coverWrap}>
              {a.coverUrl ? <Picture uri={a.coverUrl} width={96} height={72} />
                : <Text style={styles.coverEmpty}>📷</Text>}
            </View>
            <View style={{ flex: 1, minWidth: 0 }}>
              <Text style={styles.cardTitle} numberOfLines={2}>{a.title}</Text>
              <Text style={styles.cardMeta} numberOfLines={1}>
                {a.audienceLabel} · {a.photoCount === 1 ? '1 photo' : `${a.photoCount} photos`}
              </Text>
            </View>
          </TouchableOpacity>
        ))
      )}
    </ScrollView>
  );
}

/**
 * One image. A plain img on the web, with an alt, because React Native Web draws
 * Image as a background on a div, which cannot carry one.
 */
function Picture({ uri, width, height }: { uri: string | null; width: number; height: number }) {
  if (!uri) {
    return <View style={{ width, height, backgroundColor: '#e2e8f0' }} />;
  }
  if (Platform.OS === 'web') {
    return React.createElement('img', {
      src: uri, alt: 'Photo',
      style: { width, height, objectFit: 'cover', display: 'block', borderRadius: 6 },
    });
  }
  return <Image source={{ uri }} style={{ width, height, borderRadius: 6 }} accessibilityLabel="Photo" />;
}

const makeStyles = (T: Theme) => StyleSheet.create({
  page: { flex: 1, backgroundColor: T.bg, paddingTop: 12 },
  centre: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: T.bg },
  card: {
    flexDirection: 'row', gap: 12, alignItems: 'center',
    backgroundColor: T.surface, borderRadius: 14, padding: 12,
    borderWidth: 1, borderColor: T.line, marginHorizontal: 16, marginBottom: 12,
  },
  coverWrap: {
    width: 96, height: 72, borderRadius: 8, overflow: 'hidden', backgroundColor: T.line,
    alignItems: 'center', justifyContent: 'center',
  },
  coverEmpty: { fontSize: 24 },
  cardTitle: { fontSize: 14, fontWeight: '700', color: T.text, lineHeight: 19 },
  cardMeta: { fontSize: 12, color: T.text3, marginTop: 3 },
  albumHeader: { marginHorizontal: 16, marginBottom: 12, gap: 4 },
  backLink: { fontSize: 13, fontWeight: '600', color: T.brand, paddingVertical: 4 },
  albumTitle: { fontSize: 18, fontWeight: '700', color: T.text },
  primaryBtn: {
    marginTop: 8, minHeight: 44, borderRadius: T.pill, backgroundColor: T.brand,
    alignItems: 'center', justifyContent: 'center', paddingHorizontal: 16,
  },
  primaryBtnText: { color: '#fff', fontWeight: '700', fontSize: 14 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', marginHorizontal: 16 },
  withheld: { fontSize: 10, fontWeight: '700', color: T.dangerInk, marginTop: 2, maxWidth: 110 },
  emptyCard: {
    backgroundColor: T.surface, borderRadius: 14, borderWidth: 1, borderColor: T.line,
    marginHorizontal: 16, paddingVertical: 16, paddingHorizontal: 24,
  },
  emptyTitle: { fontSize: 15, fontWeight: '600', color: T.text },
  emptyBody: { fontSize: 13, color: T.text3, marginTop: 4 },
  notice: {
    fontSize: 13, color: T.text2, marginHorizontal: 16, marginBottom: 12,
    backgroundColor: T.surface, borderRadius: 12, borderWidth: 1, borderColor: T.line, padding: 12,
  },
  error: { fontSize: 13, color: T.dangerInk, marginHorizontal: 16, marginBottom: 12 },
  viewer: { flex: 1, backgroundColor: 'rgba(0,0,0,0.92)', alignItems: 'center', justifyContent: 'center', padding: 8 },
  viewerBar: { position: 'absolute', left: 0, right: 0, bottom: 0, flexDirection: 'row', justifyContent: 'center', gap: 12 },
  viewerBtn: {
    minHeight: 44, minWidth: 96, borderRadius: T.pill, backgroundColor: 'rgba(255,255,255,0.16)',
    alignItems: 'center', justifyContent: 'center', paddingHorizontal: 16,
  },
  viewerBtnText: { color: '#fff', fontWeight: '700', fontSize: 14 },
});
