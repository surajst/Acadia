const { withAppBuildGradle } = require('expo/config-plugins');

/**
 * Sign release builds with a real key instead of the debug key.
 *
 * `expo prebuild` generates an android project whose release buildType points at
 * `signingConfigs.debug` -- the generated file even carries a "Caution!" comment
 * saying so. The Android debug key is a well-known published keypair, so a
 * release APK signed with it can be re-signed and impersonated by anyone. EAS
 * hides this because it injects its own credentials; building with Gradle
 * directly does not, so anything we hand out from CI would be debug-signed
 * unless this plugin runs.
 *
 * The android/ directory is gitignored and regenerated on every prebuild, so
 * this cannot be a one-off edit to the generated file -- it has to be applied
 * as part of prebuild, which is what a config plugin is for.
 *
 * The credentials themselves stay out of the repository. Gradle reads them as
 * project properties, supplied by ~/.gradle/gradle.properties locally and by
 * -P flags from GitHub Actions secrets in CI. When they are absent the build
 * deliberately falls back to debug signing rather than failing, so a developer
 * without the key can still produce a local build -- just not a shippable one.
 */
const RELEASE_SIGNING_CONFIG = `
        release {
            if (project.hasProperty('ACADIA_STORE_FILE')) {
                storeFile file(ACADIA_STORE_FILE)
                storePassword ACADIA_STORE_PASSWORD
                keyAlias ACADIA_KEY_ALIAS
                keyPassword ACADIA_KEY_PASSWORD
            }
        }`;

const ABI_SPLITS = `
    splits {
        abi {
            // A universal APK carries every architecture at once, which is most
            // of why the EAS preview build weighs 109 MB. Splitting emits one
            // slim APK per architecture; the universal one stays as a fallback
            // for anything that does not match.
            //
            // x86_64 is included even though no phone uses it: Android
            // emulators do. Dropping it to save build time is what made a
            // launch crash impossible to reproduce locally -- the only APK that
            // would install on an emulator was the old EAS one.
            enable true
            reset()
            include 'armeabi-v7a', 'arm64-v8a', 'x86_64'
            universalApk true
        }
    }`;

module.exports = function withReleaseSigning(config) {
  return withAppBuildGradle(config, (cfg) => {
    let gradle = cfg.modResults.contents;

    if (!gradle.includes('ACADIA_STORE_FILE')) {
      // Append the release config inside the existing signingConfigs block, by
      // anchoring on the debug block's closing brace rather than on whitespace.
      const debugBlock = /(signingConfigs\s*\{[\s\S]*?keyPassword\s+'android'\s*\n\s*\})/;
      if (!debugBlock.test(gradle)) {
        throw new Error(
          'withReleaseSigning: could not find the generated debug signingConfig. ' +
          'The Expo template changed; update this plugin rather than shipping a debug-signed APK.'
        );
      }
      gradle = gradle.replace(debugBlock, `$1${RELEASE_SIGNING_CONFIG}`);
    }

    // Point release at the real key, falling back to debug when unset.
    gradle = gradle.replace(
      /(buildTypes\s*\{[\s\S]*?release\s*\{[\s\S]*?)signingConfig\s+signingConfigs\.debug/,
      "$1signingConfig project.hasProperty('ACADIA_STORE_FILE') ? signingConfigs.release : signingConfigs.debug"
    );

    if (!gradle.includes('splits {')) {
      gradle = gradle.replace(/(\n\s*buildTypes\s*\{)/, `\n${ABI_SPLITS}\n$1`);
    }

    cfg.modResults.contents = gradle;
    return cfg;
  });
};
