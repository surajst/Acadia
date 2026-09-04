#!/usr/bin/env node
/**
 * A contrast gate for the app's styles.
 *
 * This exists because of a specific class of bug we shipped: a token codemod
 * mapped dark *background* hexes (#14532d, #7f1d1d) onto *ink* tokens, which
 * are text weights. The attendance cells came out at 1.45:1 and one quest badge
 * ended up with `color` and `backgroundColor` set to the same token, so the
 * label was simply invisible. None of it was caught, because nothing in CI ever
 * looked at the app.
 *
 * React Native styles are static object literals, so this is decidable without
 * running anything. We read the real token values out of `constants/theme.ts`,
 * walk every style object in the app, and apply three checks:
 *
 *   1. role-misuse -- a text/ink token used as a background, or a border/track
 *      token used as text. Decidable from the token's job alone, no ratio
 *      needed, and it is exactly the shape of the bug above.
 *   2. pair        -- an object that sets both `color` and `backgroundColor`
 *      must clear the WCAG floor for its own type size.
 *   3. token audit -- every token used as text is measured ONCE, against the
 *      lightest ground it could honestly sit on, and checked against
 *      `contrast-baseline.json`. Per-usage reporting turns one palette decision
 *      into sixty identical lines; per-token reporting turns it into one
 *      decision someone can actually make.
 *
 * Escape hatch: `contrast-ok: <reason>` in a comment on, or just above, the
 * offending line. The reason is required.
 *
 * The script fails when it inspects zero colours. An earlier manual check of
 * mine "passed" a screen that had rendered no cells at all; a checker that
 * measures nothing must never report success.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..');
const THEME = path.join(ROOT, 'constants', 'theme.ts');
const BASELINE = path.join(HERE, 'contrast-baseline.json');
const SKIP_DIRS = new Set(['node_modules', '.expo', '.git', 'dist', 'build', 'android', 'ios', 'scripts']);

/* ---------------------------------------------------------------- colour --- */

/** #RGB, #RRGGBB and #RRGGBBAA. Alpha is composited later, against a ground. */
function parseHex(raw) {
  if (typeof raw !== 'string') return null;
  const m = /^#([0-9a-fA-F]{3,8})$/.exec(raw.trim());
  if (!m) return null;
  let h = m[1];
  if (h.length === 3) h = [...h].map((c) => c + c).join('');
  if (h.length !== 6 && h.length !== 8) return null;
  return {
    r: parseInt(h.slice(0, 2), 16),
    g: parseInt(h.slice(2, 4), 16),
    b: parseInt(h.slice(4, 6), 16),
    a: h.length === 8 ? parseInt(h.slice(6, 8), 16) / 255 : 1,
  };
}

/** Flatten a translucent colour onto an opaque one, the way the screen does. */
function over(fg, bg) {
  if (fg.a >= 1 || !bg) return fg;
  return {
    r: fg.r * fg.a + bg.r * (1 - fg.a),
    g: fg.g * fg.a + bg.g * (1 - fg.a),
    b: fg.b * fg.a + bg.b * (1 - fg.a),
    a: 1,
  };
}

function luminance({ r, g, b }) {
  const f = (v) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
}

function ratio(fg, bg) {
  const a = luminance(fg);
  const b = luminance(bg);
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

/**
 * WCAG's large-text allowance: 18pt, or 14pt bold. React Native sizes are dp,
 * which map to CSS px, so those are 24 and 18.66. When the size is not visible
 * in the same object we assume body text, which is the stricter floor.
 */
function floorFor(fontSize, fontWeight) {
  const bold = Number(fontWeight) >= 700 || fontWeight === 'bold';
  if (typeof fontSize === 'number' && (fontSize >= 24 || (fontSize >= 18.66 && bold))) return 3.0;
  return 4.5;
}

/* ---------------------------------------------------------------- tokens --- */

/**
 * Read theme.ts as an AST rather than importing it -- it is TypeScript with
 * `as const`, and this script has to run under plain node with no build step.
 * Returns a nested plain object, so `T.card.backgroundColor` resolves.
 */
function readTokens() {
  const src = ts.createSourceFile(THEME, fs.readFileSync(THEME, 'utf8'), ts.ScriptTarget.Latest, true);
  const out = {};

  const literal = (node) => {
    if (ts.isAsExpression(node) || ts.isParenthesizedExpression(node)) return literal(node.expression);
    if (ts.isStringLiteral(node)) return node.text;
    if (ts.isNumericLiteral(node)) return Number(node.text);
    if (ts.isArrayLiteralExpression(node)) return node.elements.map(literal);
    if (ts.isObjectLiteralExpression(node)) {
      const o = {};
      for (const p of node.properties) {
        if (ts.isPropertyAssignment(p) && (ts.isIdentifier(p.name) || ts.isStringLiteral(p.name))) {
          o[p.name.text] = literal(p.initializer);
        }
      }
      return o;
    }
    return undefined;
  };

  const visit = (node) => {
    if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.initializer) {
      // Both `const type = {...}` and `export const T = {...} as const`.
      if (node.name.text === 'T') Object.assign(out, literal(node.initializer));
      if (node.name.text === 'type' && !out.type) out.type = literal(node.initializer);
    }
    ts.forEachChild(node, visit);
  };
  visit(src);

  if (!out.text || !out.bg) {
    throw new Error(`Could not read tokens from ${THEME} -- has the shape of T changed?`);
  }
  return out;
}

/**
 * What each token is *for*, which is not always what its value looks like.
 * `questCta` is a near-black, but it is a button FILL that carries white text,
 * so it is not ink. When you add a token to theme.ts, place it here.
 */
function roleOf(name) {
  if (/^text[2-4]?$/.test(name) || /Ink(Muted)?$/.test(name)) return 'ink';
  if (/^onBrand/.test(name)) return 'inverse-ink';
  if (/^(line|lineStrong|track)$/.test(name)) return 'edge';
  return 'fill';
}

/**
 * The lightest ground a given foreground could honestly be sitting on. Rule 3
 * only fires when text fails even here, so a wrong guess errs towards silence
 * rather than towards a false alarm.
 */
function groundFor(token, value, T) {
  const white = /^#(fff|ffffff)$/i.test(String(value).trim());
  if (white || token === 'surface' || (token && /^onBrand/.test(token))) return parseHex(T.brand);
  if (token && /^quest/.test(token)) return parseHex(T.quest50);
  if (token && /Ink$/.test(token)) {
    const tint = T[token.replace(/Ink$/, '50')];
    if (tint) return parseHex(tint);
  }
  return parseHex(T.surface); // #FFFFFF -- the best case on a light-only app
}

/* ------------------------------------------------------------------- AST --- */

/** Walk `T.card.backgroundColor` down to its parts; null if not rooted at T. */
function tokenPath(node) {
  const parts = [];
  let cur = node;
  while (ts.isPropertyAccessExpression(cur)) {
    parts.unshift(cur.name.text);
    cur = cur.expression;
  }
  return ts.isIdentifier(cur) && cur.text === 'T' ? parts : null;
}

/**
 * Resolve a style value to a colour, a number or a string. Anything dynamic
 * (`TONE[tone]`, a ternary, a prop) comes back undefined and is skipped -- this
 * gate is for what we can prove, not for guessing.
 */
function resolve(node, T) {
  if (ts.isAsExpression(node) || ts.isParenthesizedExpression(node)) return resolve(node.expression, T);
  if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
    return { value: node.text, token: null };
  }
  if (ts.isNumericLiteral(node)) return { value: Number(node.text), token: null };
  if (ts.isPrefixUnaryExpression(node) && ts.isNumericLiteral(node.operand)) {
    return { value: -Number(node.operand.text), token: null };
  }
  if (ts.isPropertyAccessExpression(node)) {
    const parts = tokenPath(node);
    if (!parts) return undefined;
    let v = T;
    for (const p of parts) {
      if (v == null || typeof v !== 'object') return undefined;
      v = v[p];
    }
    if (v === undefined || typeof v === 'object') return undefined;
    return { value: v, token: parts.join('.') };
  }
  return undefined;
}

const COLOR_KEYS = new Set(['color', 'backgroundColor']);
const SIZE_KEYS = new Set(['fontSize', 'fontWeight']);

/**
 * Flatten one object literal into the properties that matter here, following
 * `...T.card` and `...T.type.label` spreads so a card's own white background
 * and a type role's own size are both visible to the checks.
 */
function flatten(objNode, T) {
  const props = {};
  for (const p of objNode.properties) {
    if (ts.isSpreadAssignment(p)) {
      const parts = tokenPath(p.expression);
      if (!parts) continue;
      let v = T;
      for (const seg of parts) v = v?.[seg];
      if (v && typeof v === 'object') {
        for (const [k, val] of Object.entries(v)) {
          if (COLOR_KEYS.has(k) || SIZE_KEYS.has(k)) {
            props[k] = { value: val, token: `${parts.join('.')}.${k}`, node: p };
          }
        }
      }
      continue;
    }
    if (!ts.isPropertyAssignment(p)) continue;
    const key = ts.isIdentifier(p.name) || ts.isStringLiteral(p.name) ? p.name.text : null;
    if (!key || (!COLOR_KEYS.has(key) && !SIZE_KEYS.has(key))) continue;
    const r = resolve(p.initializer, T);
    if (r === undefined) {
      delete props[key]; // a dynamic value overrides the spread; we can't prove anything
      continue;
    }
    props[key] = { ...r, node: p };
  }
  return props;
}

/* ----------------------------------------------------------------- check --- */

/** `contrast-ok: reason` on the offending line or the line above it. */
function suppression(text, lineStarts, pos) {
  let line = 0;
  while (line + 1 < lineStarts.length && lineStarts[line + 1] <= pos) line += 1;
  for (const n of [line, line - 1]) {
    if (n < 0) continue;
    const start = lineStarts[n];
    const end = n + 1 < lineStarts.length ? lineStarts[n + 1] : text.length;
    const m = /contrast-ok:\s*(\S.*?)\s*(?:\*\/)?\s*$/.exec(text.slice(start, end));
    if (m) return m[1];
  }
  return null;
}

function checkFile(file, T, stats, textUses) {
  const text = fs.readFileSync(file, 'utf8');
  const src = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
  const lineStarts = src.getLineStarts();
  const rel = path.relative(ROOT, file).replace(/\\/g, '/');
  const findings = [];

  const site = (node) => `${rel}:${src.getLineAndCharacterOfPosition(node.getStart(src)).line + 1}`;
  const muted = (node) => {
    const why = suppression(text, lineStarts, node.getStart(src));
    if (why) stats.suppressed += 1;
    return Boolean(why);
  };

  const visit = (node) => {
    if (ts.isObjectLiteralExpression(node)) {
      const p = flatten(node, T);
      const fg = p.color;
      const bg = p.backgroundColor;

      // 1. role-misuse -- decidable from the token's job alone.
      if (bg?.token && !muted(bg.node)) {
        const role = roleOf(bg.token.split('.').pop());
        if (role === 'ink' || role === 'inverse-ink') {
          stats.checked += 1;
          findings.push({
            site: site(bg.node), rule: 'role-misuse',
            message: `\`T.${bg.token}\` is a text weight, not a surface -- as a backgroundColor it`,
            detail: 'reproduces the dark-block-on-dark-text bug. Use the matching 50/200 tint.',
          });
        }
      }
      if (fg?.token && !muted(fg.node) && roleOf(fg.token.split('.').pop()) === 'edge') {
        stats.checked += 1;
        findings.push({
          site: site(fg.node), rule: 'role-misuse',
          message: `\`T.${fg.token}\` is a border/track colour being used as text`,
          detail: 'it is sized to be barely visible. Use T.text3 for a muted label.',
        });
      }

      // 2. pair -- both halves present, so the ratio is knowable exactly.
      const f = fg && parseHex(fg.value);
      const b = bg && parseHex(bg.value);
      if (f && b && !muted(fg.node)) {
        stats.checked += 1;
        const ground = over(b, parseHex(T.bg));
        const front = over(f, ground);
        const need = floorFor(p.fontSize?.value, p.fontWeight?.value);
        const got = ratio(front, ground);
        const name = (x) => (x.token ? `T.${x.token}` : x.value);
        if (String(fg.value).toLowerCase() === String(bg.value).toLowerCase()) {
          findings.push({
            site: site(fg.node), rule: 'pair',
            message: `text and background are both ${name(fg)} -- the text is invisible`,
            detail: '1.00:1',
          });
        } else if (got < need) {
          findings.push({
            site: site(fg.node), rule: 'pair',
            message: `${name(fg)} on ${name(bg)} is ${got.toFixed(2)}:1`,
            detail: `needs ${need.toFixed(1)}:1`,
          });
        }
      }

      // 3. Collect for the per-token audit: text whose ground we cannot see here.
      if (f && !b && !muted(fg.node)) {
        const key = fg.token ? `T.${fg.token}` : String(fg.value);
        const entry = textUses.get(key) ?? { key, token: fg.token, value: fg.value, sites: [] };
        entry.sites.push(site(fg.node));
        textUses.set(key, entry);
        stats.textUses += 1;
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(src);
  return findings;
}

/* --------------------------------------------------------------- baseline --- */

/**
 * Tokens whose contrast we have looked at and accepted as they are. A baseline
 * is how a gate gets added to an existing codebase without turning "add the
 * gate" into "redesign the palette" -- but every entry needs a written reason,
 * and the recorded ratio is a ratchet: a token may improve, never slip.
 */
function readBaseline() {
  if (!fs.existsSync(BASELINE)) return new Map();
  const raw = JSON.parse(fs.readFileSync(BASELINE, 'utf8'));
  const map = new Map();
  for (const e of raw.accepted ?? []) {
    if (!e.reason) throw new Error(`contrast-baseline.json: "${e.token}" has no reason.`);
    map.set(e.token, e);
  }
  return map;
}

/* ------------------------------------------------------------------ main --- */

function* sources(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.isDirectory()) {
      if (SKIP_DIRS.has(e.name)) continue;
      yield* sources(path.join(dir, e.name));
    } else if (/\.tsx?$/.test(e.name) && !e.name.endsWith('.d.ts')) {
      yield path.join(dir, e.name);
    }
  }
}

const T = readTokens();
const baseline = readBaseline();
const stats = { checked: 0, suppressed: 0, files: 0, textUses: 0, tokens: 0 };
const textUses = new Map();
const findings = [];

for (const f of sources(ROOT)) {
  stats.files += 1;
  findings.push(...checkFile(f, T, stats, textUses));
}

// Measure each text colour once, against the lightest ground it could sit on.
const accepted = [];
for (const use of [...textUses.values()].sort((a, b) => a.key.localeCompare(b.key))) {
  const fg = parseHex(use.value);
  const ground = groundFor(use.token, use.value, T);
  if (!fg || !ground) continue;
  stats.tokens += 1;
  const got = ratio(over(fg, ground), ground);
  const known = baseline.get(use.key);
  if (got >= 4.5) continue;
  if (known && got >= known.ratio - 0.005) {
    accepted.push({ ...use, got, reason: known.reason });
    continue;
  }
  findings.push({
    site: use.sites[0], rule: 'token',
    message: known
      ? `${use.key} has slipped to ${got.toFixed(2)}:1 (baseline ${known.ratio.toFixed(2)}:1)`
      : `${use.key} is ${got.toFixed(2)}:1 as text, below the 4.5:1 floor`,
    detail: `${use.sites.length} use(s), e.g. ${use.sites.slice(0, 3).join(', ')}` +
      (known ? '' : '\n    Fix it, or record it in scripts/contrast-baseline.json with a reason.'),
  });
}

const ORDER = ['role-misuse', 'pair', 'token'];
findings.sort((a, b) => ORDER.indexOf(a.rule) - ORDER.indexOf(b.rule) || a.site.localeCompare(b.site));
for (const f of findings) console.log(`${f.site}  [${f.rule}]  ${f.message}\n    ${f.detail}`);

if (accepted.length) {
  console.log('\nAccepted by baseline (still below 4.5:1):');
  for (const a of accepted) {
    console.log(`  ${a.key.padEnd(18)} ${a.got.toFixed(2)}:1  ${a.sites.length} use(s)  -- ${a.reason}`);
  }
}

console.log(
  `\n${stats.files} files scanned, ${stats.checked} style role/pair check(s), ` +
  `${stats.textUses} text colour use(s) across ${stats.tokens} token(s), ` +
  `${stats.suppressed} suppressed, ${findings.length} problem(s).`);

// An empty run is a broken run, not a clean one. This app has hundreds of
// styled colours; if the walk suddenly finds none, the parser or theme.ts has
// drifted and the "0 problems" above is meaningless.
if (stats.checked + stats.textUses < 50) {
  console.error(`\nOnly ${stats.checked + stats.textUses} colours were inspected, which is far ` +
    'below what this app contains. The parser or the token file has drifted; that is a failure, ' +
    'not a pass.');
  process.exit(2);
}

process.exit(findings.length > 0 ? 1 : 0);
