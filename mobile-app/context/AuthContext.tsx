import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  createContext,
  PropsWithChildren,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';

type AuthContextValue = {
  userToken: string | null;
  userRole: string | null;
  firstName: string | null;
  lastName: string | null;
  isLoading: boolean;
  login: (token: string, role: string, firstName: string, lastName: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: PropsWithChildren) {
  const [userToken, setUserToken] = useState<string | null>(null);
  const [userRole, setUserRole] = useState<string | null>(null);
  const [firstName, setFirstName] = useState<string | null>(null);
  const [lastName, setLastName] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;

    const restoreSession = async () => {
      try {
        const [storedToken, storedRole, storedFirstName, storedLastName] = await Promise.all([
          AsyncStorage.getItem('userToken'),
          AsyncStorage.getItem('userRole'),
          AsyncStorage.getItem('firstName'),
          AsyncStorage.getItem('lastName'),
        ]);

        if (isMounted) {
          setUserToken(storedToken);
          setUserRole(storedRole);
          setFirstName(storedFirstName);
          setLastName(storedLastName);
        }
      } catch (error) {
        console.error('Unable to restore the authentication session:', error);
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };

    void restoreSession();

    return () => {
      isMounted = false;
    };
  }, []);

  const login = async (token: string, role: string, firstName: string, lastName: string) => {
    await AsyncStorage.multiSet([
      ['userToken', token],
      ['userRole', role],
      ['firstName', firstName],
      ['lastName', lastName],
    ]);
    setUserToken(token);
    setUserRole(role);
    setFirstName(firstName);
    setLastName(lastName);
  };

  const logout = async () => {
    setUserToken(null);
    setUserRole(null);
    setFirstName(null);
    setLastName(null);
    await AsyncStorage.clear();
  };

  const value = useMemo(
    () => ({
      userToken,
      userRole,
      firstName,
      lastName,
      isLoading,
      login,
      logout,
    }),
    [userToken, userRole, firstName, lastName, isLoading],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider.');
  }

  return context;
}