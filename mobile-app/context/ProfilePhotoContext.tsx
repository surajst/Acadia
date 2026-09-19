import * as ImagePicker from 'expo-image-picker';
import { Alert } from 'react-native';
import {
  createContext, useCallback, useContext, useEffect, useMemo, useState,
  type PropsWithChildren,
} from 'react';

import { getUserProfile, profilePhotoUrl, uploadProfilePhoto, deleteProfilePhoto } from '../services/api';

/**
 * The signed-in user's profile photograph, in one place.
 *
 * <p>Two surfaces set the same picture -- the centre of the wheel and the
 * profile screen -- and both have to reflect a change the other made. A shared
 * context means picking a photo on one updates the other without either
 * knowing the other exists.
 */
type ProfilePhotoValue = {
  /** Ready to hand to an <Image>, or null when the user has no photo. */
  photoUri: string | null;
  isBusy: boolean;
  /** Opens the picker, uploads, refreshes. Safe to call from anywhere. */
  pickAndUpload: () => Promise<void>;
  removePhoto: () => Promise<void>;
  refresh: () => Promise<void>;
};

const ProfilePhotoContext = createContext<ProfilePhotoValue | undefined>(undefined);

export function ProfilePhotoProvider({ children }: PropsWithChildren) {
  const [photoUri, setPhotoUri] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const profile = await getUserProfile();
      setPhotoUri(profile?.hasPhoto
        ? profilePhotoUrl(profile.userId, profile.photoUpdatedAt)
        : null);
    } catch {
      // A missing photo is not worth interrupting anyone over -- the avatar
      // falls back to the initial.
      setPhotoUri(null);
    }
  }, []);

  // A fetch on mount, which is what this provider is for. The rule is aimed at
  // state derived from props during render; there is nothing to derive from
  // here, the photo only exists on the server. Suppressed rather than raising
  // the repo's warning ceiling, which would hide the next real one.
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { void refresh(); }, [refresh]);

  const pickAndUpload = useCallback(async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert(
        'Photos not allowed',
        'Acadia needs access to your photos to set a profile picture. You can turn this on in Settings.',
      );
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      // Square, and small. The crop is the user's; the resize is what keeps an
      // avatar in the tens of kilobytes rather than the megabytes a modern
      // phone camera produces -- which the server would refuse.
      allowsEditing: true,
      aspect: [1, 1],
      quality: 0.6,
    });
    if (result.canceled || !result.assets?.length) return;

    const asset = result.assets[0];
    setIsBusy(true);
    try {
      await uploadProfilePhoto({ uri: asset.uri, mimeType: asset.mimeType });
      await refresh();
    } catch (err: any) {
      const message = err?.response?.data?.error
        ?? 'Could not save that picture. Please try again.';
      Alert.alert('Not saved', message);
    } finally {
      setIsBusy(false);
    }
  }, [refresh]);

  const removePhoto = useCallback(async () => {
    setIsBusy(true);
    try {
      await deleteProfilePhoto();
      setPhotoUri(null);
    } catch {
      Alert.alert('Not removed', 'Could not remove that picture. Please try again.');
    } finally {
      setIsBusy(false);
    }
  }, []);

  const value = useMemo<ProfilePhotoValue>(
    () => ({ photoUri, isBusy, pickAndUpload, removePhoto, refresh }),
    [photoUri, isBusy, pickAndUpload, removePhoto, refresh],
  );

  return <ProfilePhotoContext.Provider value={value}>{children}</ProfilePhotoContext.Provider>;
}

export function useProfilePhoto(): ProfilePhotoValue {
  const context = useContext(ProfilePhotoContext);
  if (!context) {
    throw new Error('useProfilePhoto must be used within a ProfilePhotoProvider.');
  }
  return context;
}
