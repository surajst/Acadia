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
/**
 * The type scale, by role rather than by size. Declared outside the token
 * object because `as const` would freeze `fontVariant` into a readonly tuple,
 * which React Native's style types reject.
 *
 * `metric` carries tabular-nums so a column of figures lines up on the
 * decimal -- the point of a number a teacher scans rather than reads.
 */
const type = {
  display:  { fontSize: 28, lineHeight: 34, fontWeight: '700' as const, letterSpacing: -0.5 },
  title:    { fontSize: 20, lineHeight: 26, fontWeight: '700' as const, letterSpacing: -0.2 },
  heading:  { fontSize: 16, lineHeight: 22, fontWeight: '600' as const },
  body:     { fontSize: 14.5, lineHeight: 21, fontWeight: '400' as const },
  label:    { fontSize: 13, lineHeight: 18, fontWeight: '600' as const },
  caption:  { fontSize: 12, lineHeight: 16, fontWeight: '400' as const },
  overline: { fontSize: 11, lineHeight: 14, fontWeight: '700' as const, letterSpacing: 0.9 },
  metric:   { fontSize: 26, lineHeight: 31, fontWeight: '700' as const, letterSpacing: -0.6,
              fontVariant: ['tabular-nums' as const] },
};

export const T = {
  type,

  bg: '#F7F9FC',
  surface: '#FFFFFF',
  /** Behind a sheet or modal, so the page reads as sent to the back. */
  scrim: '#00000090',
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

  /**
   * On a brand fill the page tokens invert -- text3 on indigo is unreadable.
   * These are the three weights that work there: a heading, a supporting line,
   * and an overline label.
   */
  onBrand: '#FFFFFF',
  onBrandMuted: '#E0E7FF',
  onBrandFaint: '#C7D2FE',

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

  /**
   * Ink steps: the readable weight of each status colour for TEXT sitting on
   * its own 50 tint. The base colours are sized for fills and icons and do not
   * all survive as small text -- success on success50 is 3.6:1, under the 4.5:1
   * floor. These clear it: 5.2, 4.9, 5.9 and 6.2:1 respectively.
   */
  successInk: '#047857',
  warnInk: '#B45309',
  dangerInk: '#B91C1C',
  infoInk: '#1D4ED8',
  /** Readable brand weight for small text on brand50. */
  brandInk: '#4338CA',

  r: 16,
  rSm: 14,
  rXs: 12,
  pill: 999,

  /** One spacing scale. Reach for a step, not an arbitrary number. */
  space: { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 },

  /**
   * Content sits flat on the page; only things that genuinely float get
   * `raised`. A shadow on every card is the fastest way a dashboard stops
   * looking like a product -- nothing is elevated when everything is.
   */
  card: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E7EAF2',
    borderRadius: 16,
  },

  raised: {
    shadowColor: '#101828',
    shadowOpacity: 0.10,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 8 },
    elevation: 8,
  },
} as const;

export default T;
