import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  createContext,
  PropsWithChildren,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';

import baseTheme from '../constants/theme';
import { BrandPalette, DEFAULT_PALETTE_ID, PALETTE_LIST, PaletteId, PALETTES } from '../constants/palettes';

const STORAGE_KEY = 'acadia-theme-palette-id';

/**
 * The full token set a component reads: every field of the static `T` export,
 * with the brand-family fields (BrandPalette's keys) widened from theme.ts's
 * `as const` literal types to plain `string` -- since a palette swap really
 * does replace "#17181C" with any of the other four hexes, and the type has
 * to admit that. Everything else keeps whatever type it already had; no
 * palette ever touches it.
 *
 * IMPORTANT for scripts/check-contrast.mjs: keep every call site's local
 * binding named `T`, e.g. `const T = useTheme();` inside a component body,
 * not `const theme = useTheme()`. The checker recognises a property-access
 * chain only when it is literally rooted at an identifier named `T` -- a
 * different name makes it silently stop verifying that call site.
 */
export type Theme = Omit<typeof baseTheme, keyof BrandPalette> & BrandPalette;

type ThemeContextValue = {
  theme: Theme;
  paletteId: PaletteId;
  palettes: typeof PALETTE_LIST;
  isLoading: boolean;
  setPaletteId: (id: PaletteId) => Promise<void>;
};

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

export function ThemeProvider({ children }: PropsWithChildren) {
  const [paletteId, setPaletteIdState] = useState<PaletteId>(DEFAULT_PALETTE_ID);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;

    (async () => {
      try {
        const stored = await AsyncStorage.getItem(STORAGE_KEY);
        if (isMounted && stored && stored in PALETTES) {
          setPaletteIdState(stored as PaletteId);
        }
      } catch (error) {
        console.error('Unable to restore the saved theme:', error);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    })();

    return () => {
      isMounted = false;
    };
  }, []);

  const setPaletteId = async (id: PaletteId) => {
    setPaletteIdState(id);
    try {
      await AsyncStorage.setItem(STORAGE_KEY, id);
    } catch (error) {
      console.error('Unable to save the chosen theme:', error);
    }
  };

  // Base theme spread first, then the active palette's brand-family fields
  // override it -- everything else (text colours, status colours, spacing)
  // is untouched by theme choice. Memoised on paletteId so components doing
  // `useMemo(() => makeStyles(T), [T])` only recompute when it actually changes.
  const theme = useMemo<Theme>(
    () => ({ ...baseTheme, ...PALETTES[paletteId] }),
    [paletteId],
  );

  const value = useMemo(
    () => ({ theme, paletteId, palettes: PALETTE_LIST, isLoading, setPaletteId }),
    [theme, paletteId, isLoading],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

/**
 * Returns the active token set. Bind it locally as `T` -- see the note on
 * `Theme` above for why the name matters to the contrast checker.
 */
export function useTheme(): Theme {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider.');
  }
  return context.theme;
}

/** For the settings screen: the picker itself needs the id list and setter, not just the tokens. */
export function useThemePicker() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useThemePicker must be used within a ThemeProvider.');
  }
  return context;
}
