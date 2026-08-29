import T from './theme';

/**
 * Expo's light/dark plumbing. The app is light-only by design -- see theme.ts --
 * so this exists to satisfy the framework, and its tint follows the one brand
 * indigo rather than Expo's default blue.
 */
const tintColorLight = T.brand;
const tintColorDark = '#fff';


export default {
  light: {
    text: '#000',
    background: '#fff',
    tint: tintColorLight,
    tabIconDefault: '#ccc',
    tabIconSelected: tintColorLight,
  },
  dark: {
    text: '#fff',
    background: '#000',
    tint: tintColorDark,
    tabIconDefault: '#ccc',
    tabIconSelected: tintColorDark,
  },
};
