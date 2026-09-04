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
    rules: {
      // An unused binding is usually a rename that did not finish. Underscore
      // marks the deliberate ones (a destructured field we are skipping past).
      'no-unused-vars': ['error', {
        args: 'none',
        varsIgnorePattern: '^_',
        caughtErrorsIgnorePattern: '^_',
      }],
    },
  },
]);
