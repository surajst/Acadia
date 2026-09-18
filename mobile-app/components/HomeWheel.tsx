import { useContext, useMemo } from 'react';
import { useRouter } from 'expo-router';

import { useAuth } from '@/context/AuthContext';
import { DataContext } from '../app/(tabs)/_layout';
import { wheelOptionsFor } from '../constants/wheel';
import Avatar from './ui/Avatar';
import UserWheel from './ui/UserWheel';

/**
 * The home screen's navigator, wired to whoever is signed in.
 *
 * <p>Sits between the three role dashboards and {@link UserWheel} so the wheel
 * exists once rather than three times: the dashboards keep their own data and
 * their own summary header, and share this.
 */
export default function HomeWheel({ photoUri, onHubPress }: {
  photoUri?: string | null;
  onHubPress?: () => void;
}) {
  const { userRole: role, firstName } = useAuth();
  const { data } = useContext(DataContext);
  const router = useRouter();

  const options = useMemo(() => wheelOptionsFor(role), [role]);

  // AuthContext is the fast path (it is hydrated from AsyncStorage before any
  // request lands); the dashboard payload is the fallback for a cold install.
  const name = firstName || data?.student?.firstName || data?.parent?.firstName || '';

  if (options.length === 0) return null;

  return (
    <UserWheel
      options={options}
      hub={<Avatar uri={photoUri} initial={name} size={92} />}
      hubLabel={photoUri ? 'Your profile picture' : 'Add a profile picture'}
      onHubPress={onHubPress ?? (() => router.push('/profile' as never))}
    />
  );
}
