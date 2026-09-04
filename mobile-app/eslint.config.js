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

      // A warning, not an error, and deliberately still on. Sixteen screens
      // share one shape: `useEffect(() => { fetchX(); }, [])`, where fetchX
      // opens with a synchronous setLoading(true). The rule is right -- that is
      // an extra render pass on every mount -- but the fix is to seed the state
      // as `useState(true)` and restructure the loader on all sixteen, which is
      // a refactor, not a lint cleanup. Left visible so it does not get lost.
      'react-hooks/set-state-in-effect': 'warn',
    },
  },
]);
