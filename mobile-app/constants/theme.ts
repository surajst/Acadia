/**
 * The app's design tokens, matching backend/src/main/resources/static/css/design-system.css.
 *
 * The two surfaces are one product, and until now the app carried its own
 * near-miss palette -- indigo-500 where the web used indigo-600, slate-200
 * borders where the web used #E7EAF2. Close enough to look accidental rather
 * than deliberate when a parent moved between them.
 *
 * Import T instead of writing hex literals. The app is light-only by design:
 * there is no dark theme here, so do not wire useColorScheme into new screens.
 */
export const T = {
  bg: '#F7F9FC',
  surface: '#FFFFFF',
  surface2: '#F8FAFC',
  line: '#E7EAF2',
  lineStrong: '#D9DFEA',
  track: '#F1F4F9',

  text: '#0F172A',
  text2: '#475569',
  text3: '#64748B',
  /** Decoration only -- never a label a user has to read. */
  text4: '#94A0B4',

  brand: '#4F46E5',
  brand50: '#EEF0FF',
  brand100: '#E0E3FF',
  brand700: '#4338CA',

  success: '#059669',
  success50: '#ECFDF5',
  success200: '#A7F3D0',
  warn: '#D97706',
  warn50: '#FFFBEB',
  warn200: '#FDE68A',
  danger: '#DC2626',
  danger50: '#FEF2F2',
  danger200: '#FECACA',
  info: '#2563EB',
  info50: '#EFF6FF',
  info200: '#BFDBFE',

  r: 16,
  rSm: 14,
  rXs: 12,
  pill: 999,

  card: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E7EAF2',
    borderRadius: 16,
    shadowColor: '#101828',
    shadowOpacity: 0.05,
    shadowRadius: 2,
    shadowOffset: { width: 0, height: 1 },
    elevation: 1,
  },
} as const;

export default T;
