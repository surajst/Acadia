const { withGradleProperties } = require('expo/config-plugins');

/**
 * Give Gradle enough metaspace to compile this project.
 *
 * `expo prebuild` generates android/gradle.properties with
 * `-Xmx2048m -XX:MaxMetaspaceSize=512m`. That is not enough here: between the
 * React Native codegen, Kotlin compilation and a dozen Expo modules, the build
 * exhausts metaspace, and it does not fail cleanly when it does -- it thrashes.
 * A GitHub Actions run spent four and a half hours emitting
 * `OutOfMemoryError: Metaspace` from idle RMI threads before hitting the
 * six-hour job ceiling and being cancelled.
 *
 * This has to be a config plugin rather than an edit to the generated file for
 * the same reason the signing config does: android/ is gitignored and rewritten
 * on every prebuild, so a local edit fixes one machine and silently does not
 * reach CI. That is exactly how the run above was allowed to happen -- the
 * memory fix existed on my machine and nowhere else.
 *
 * Values are sized for a GitHub-hosted runner (4 cores, 16 GB) and are
 * comfortable on a developer laptop too.
 */
const PROPERTIES = {
  'org.gradle.jvmargs': '-Xmx4096m -XX:MaxMetaspaceSize=2048m -XX:+HeapDumpOnOutOfMemoryError',
  // The Kotlin compile daemon gets its own JVM and its own limits; leaving it
  // at the default reintroduces the same failure one layer down.
  'kotlin.daemon.jvmargs': '-Xmx2048m -XX:MaxMetaspaceSize=1024m',
};

module.exports = function withGradleMemory(config) {
  return withGradleProperties(config, (cfg) => {
    for (const [key, value] of Object.entries(PROPERTIES)) {
      const existing = cfg.modResults.find(
        (item) => item.type === 'property' && item.key === key
      );
      if (existing) {
        existing.value = value;
      } else {
        cfg.modResults.push({ type: 'property', key, value });
      }
    }
    return cfg;
  });
};
