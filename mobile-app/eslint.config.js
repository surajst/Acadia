// Expo's own flat config. It carries the rules that matter for this codebase --
// exhaustive hook dependencies, unreachable code, unused bindings -- and the
// React Native / expo-router globals, so the defaults do not fight the router's
// file conventions.
const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');

module.exports = defineConfig([
  expoConfig,
  {
    ignores: ['dist/*', '.expo/*', 'node_modules/*'],
  },
  {
    // Flat config scopes plugins per config object, so raising the severity of
    // a @typescript-eslint rule means registering the plugin here too -- it
    // being registered inside eslint-config-expo is not enough.
    plugins: { '@typescript-eslint': require('@typescript-eslint/eslint-plugin') },
    rules: {
      // The base rule and its TypeScript counterpart fire on the same bindings,
      // so leaving both on double-counts every finding. Only the TS one
      // understands type-only imports and enums, so that is the one to keep.
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': ['error', {
        args: 'none',
        varsIgnorePattern: '^_',
        caughtErrorsIgnorePattern: '^_',
      }],

      // theme.ts deliberately exports T both by name and as the default, so
      // every `import T from '../constants/theme'` trips this -- 45 times, in
      // a pattern that is intentional and consistent across the app.
      'import/no-named-as-default': 'off',

      // A warning, not an error, and deliberately still on.
      //
      // An earlier version of this comment said the fix was to seed the flag as
      // `useState(true)` and drop the synchronous `setLoading(true)`. That was
      // wrong, and testing it is what showed why: removing the synchronous call
      // from challenges.tsx did not silence the rule. It is not objecting to a
      // sync setState inside an async loader -- it objects to an effect calling
      // anything that eventually sets state, which is fetch-on-mount itself.
      //
      // So the remaining 13 cannot be satisfied without moving data loading out
      // of effects altogether (a query library, or route loaders). That is a
      // real decision to make, not a cleanup, so it stays a warning until it is
      // made. The four that were genuinely fixable -- two flags an effect had to
      // correct on the first frame, one state copy of a value already in props,
      // and one deliberate hydration probe -- are done.
      'react-hooks/set-state-in-effect': 'warn',
    },
  },
]);
