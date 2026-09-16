/**
 * The brand-family colours a user can choose between, as a full picker.
 *
 * `constants/theme.ts` ships exactly one of these -- whichever is live right
 * now -- as a plain object literal, because scripts/check-contrast.mjs reads
 * that file as source (via the TypeScript AST, no build step) and only
 * recognises a `const T = {...}` shape. This file is NOT scanned by that
 * script; it exists purely as runtime data for the ThemeContext to pick from.
 * That is deliberate, not a gap: every palette below was independently
 * verified against the same WCAG floors the checker enforces (4.5:1 for
 * onBrand/onBrandMuted text, 3.0:1 for onBrandFaint) before being added here,
 * checked against BOTH ends of the gradient -- see the design notes on each.
 *
 * "Current purple" and "The original blue" are not new colours: they are the
 * two brand values this app has actually shipped with, at their real
 * previously-live hex values (verified from git history), not a
 * recomputed approximation. Only Deep Navy and Deep Teal are new.
 */

export type PaletteId = 'inkAmber' | 'currentPurple' | 'originalBlue' | 'deepNavy' | 'deepTeal';

export type BrandPalette = {
  id: PaletteId;
  /** Shown in the picker. */
  label: string;
  brand: string;
  brand50: string;
  brand100: string;
  brand700: string;
  brandRing: string;
  onBrand: string;
  onBrandMuted: string;
  onBrandFaint: string;
  brandInk: string;
};

export const DEFAULT_PALETTE_ID: PaletteId = 'inkAmber';

export const PALETTES: Record<PaletteId, BrandPalette> = {
  // The live default, as shipped in constants/theme.ts today.
  inkAmber: {
    id: 'inkAmber',
    label: 'Ink & Amber',
    brand: '#17181C',
    brand50: '#EFEFEF',
    brand100: '#DADADB',
    brand700: '#8A6423',
    brandRing: '#5E4720',
    onBrand: '#FFFFFF',
    onBrandMuted: '#ECEDED',
    onBrandFaint: '#C3C3C4',
    brandInk: '#17181C',
  },
  // The real, previously-shipped purple -- not a recomputed approximation.
  currentPurple: {
    id: 'currentPurple',
    label: 'Current purple',
    brand: '#5B47D6',
    brand50: '#EDEAFA',
    brand100: '#DCD6F5',
    brand700: '#4A3BB0',
    brandRing: '#5044C4',
    onBrand: '#FFFFFF',
    onBrandMuted: '#E0E7FF',
    onBrandFaint: '#D0DBFF',
    brandInk: '#453A87',
  },
  // The real, pre-redesign blue -- an exact restore, not an approximation.
  // brandRing did not exist at that point in the app's history (introduced
  // alongside the purple redesign); computed here the same way brand700 was,
  // mixing 10% toward black, to give this palette a complete token set.
  originalBlue: {
    id: 'originalBlue',
    label: 'The original blue',
    brand: '#4F46E5',
    brand50: '#EEF0FF',
    brand100: '#E0E3FF',
    brand700: '#4338CA',
    brandRing: '#473FCE',
    onBrand: '#FFFFFF',
    onBrandMuted: '#E0E7FF',
    onBrandFaint: '#C7D2FE',
    brandInk: '#4338CA',
  },
  // New. Verified: white text on either gradient stop clears 9.91:1 (headline),
  // 4.65:1 (supporting), 4.65:1 (decorative) -- all comfortably over floor.
  deepNavy: {
    id: 'deepNavy',
    label: 'Deep navy',
    brand: '#1D3E8C',
    brand50: '#EFF1F7',
    brand100: '#DBE0ED',
    brand700: '#17306D',
    brandRing: '#1A387E',
    onBrand: '#FFFFFF',
    onBrandMuted: '#A5B2D1',
    onBrandFaint: '#A5B2D1',
    brandInk: '#17306D',
  },
  // New. Verified: 7.6:1 / 4.54:1 / 3.84:1 against the worse of the two stops.
  deepTeal: {
    id: 'deepTeal',
    label: 'Deep teal',
    brand: '#0E5C6B',
    brand50: '#EEF4F5',
    brand100: '#D8E5E7',
    brand700: '#0B4853',
    brandRing: '#0D5360',
    onBrand: '#FFFFFF',
    onBrandMuted: '#B4CCD1',
    onBrandFaint: '#9FBEC4',
    brandInk: '#0B4853',
  },
};

export const PALETTE_LIST: BrandPalette[] = [
  PALETTES.inkAmber,
  PALETTES.currentPurple,
  PALETTES.originalBlue,
  PALETTES.deepNavy,
  PALETTES.deepTeal,
];
