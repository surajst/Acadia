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
  schoolName: string | null;
  academicYearName: string | null;
  isLoading: boolean;
  login: (token: string, role: string, firstName: string, lastName: string, schoolName?: string | null, academicYearName?: string | null) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: PropsWithChildren) {
  const [userToken, setUserToken] = useState<string | null>(null);
  const [userRole, setUserRole] = useState<string | null>(null);
  const [firstName, setFirstName] = useState<string | null>(null);
  const [lastName, setLastName] = useState<string | null>(null);
  const [schoolName, setSchoolName] = useState<string | null>(null);
  const [academicYearName, setAcademicYearName] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;

    const restoreSession = async () => {
      try {
        const [storedToken, storedRole, storedFirstName, storedLastName, storedSchoolName, storedAcademicYearName] = await Promise.all([
          AsyncStorage.getItem('userToken'),
          AsyncStorage.getItem('userRole'),
          AsyncStorage.getItem('firstName'),
          AsyncStorage.getItem('lastName'),
          AsyncStorage.getItem('schoolName'),
          AsyncStorage.getItem('academicYearName'),
        ]);

        if (isMounted) {
          setUserToken(storedToken);
          setUserRole(storedRole);
          setFirstName(storedFirstName);
          setLastName(storedLastName);
          setSchoolName(storedSchoolName);
          setAcademicYearName(storedAcademicYearName);
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

  const login = async (token: string, role: string, firstName: string, lastName: string, schoolName?: string | null, academicYearName?: string | null) => {
    await AsyncStorage.multiSet([
      ['userToken', token],
      ['userRole', role],
      ['firstName', firstName],
      ['lastName', lastName],
      ['schoolName', schoolName ?? ''],
      ['academicYearName', academicYearName ?? ''],
    ]);
    setUserToken(token);
    setUserRole(role);
    setFirstName(firstName);
    setLastName(lastName);
    setSchoolName(schoolName ?? null);
    setAcademicYearName(academicYearName ?? null);
  };

  const logout = async () => {
    setUserToken(null);
    setUserRole(null);
    setFirstName(null);
    setLastName(null);
    setSchoolName(null);
    setAcademicYearName(null);
    await AsyncStorage.clear();
  };

  const value = useMemo(
    () => ({
      userToken,
      userRole,
      firstName,
      lastName,
      schoolName,
      academicYearName,
      isLoading,
      login,
      logout,
    }),
    [userToken, userRole, firstName, lastName, schoolName, academicYearName, isLoading],
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