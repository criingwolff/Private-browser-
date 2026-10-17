# Private Browser (Android, GeckoView)

A minimal open-source Android browser on Mozilla's GeckoView engine.

- **Ad-blocker compatible:** GeckoView runs Firefox WebExtensions, so uBlock Origin,
  AdGuard, Privacy Badger, etc. install straight from addons.mozilla.org
  (menu → "Install uBlock Origin", or paste any `.xpi` URL).
  Extensions are explicitly allowed in private sessions (they're off there by default).
- **Keeps no data:** all tabs are private sessions, disk cache/history/passwords/form
  data are disabled via Gecko prefs, and all site data is wiped on launch and exit.
  `allowBackup` is off and the URL bar asks the keyboard not to learn.
- No analytics, telemetry or crash reporting.

## Build

Needs Android Studio (or JDK 17 + Android SDK 35) and the Gradle wrapper.

    gradle wrapper        # once, generates ./gradlew
    ./gradlew assembleDebug
    # APK: app/build/outputs/apk/debug/app-debug.apk

### Build without installing anything (GitHub Actions)

1. Create an empty repo on github.com and push this folder to its `main` branch.
2. Open the repo's **Actions** tab → **Build APK** (it also runs on every push; you can
   start it by hand with "Run workflow").
3. When the run finishes, download **PrivateBrowser-debug-apk** from the run's
   Artifacts section, unzip it and install `app-debug.apk` on your phone.

GeckoView comes from `https://maven.mozilla.org/maven2/` (already in settings.gradle.kts).
The pinned version is 158.0.20261007201254; bump it in `app/build.gradle.kts` as needed.

## Known gaps / next steps

- GeckoView's `WebExtensionController.PromptDelegate` signature changes between
  releases. If `onInstallPrompt` doesn't compile, match it to the current Javadoc.
  The delegate auto-accepts installs; replace it with a real permissions dialog
  before distributing.
- Only one tab, no downloads UI, no bookmarks (by design: nothing is saved).
  Tabs and a nicer toolbar are the obvious next features.
- Extensions are Manifest V2 (what GeckoView supports) and extension settings may not
  survive a data wipe, so a blocker's custom rules reset each launch. Remove
  `StorageController.ClearFlags.ALL` from the wipe if you want extension settings kept.
- Not tested on a device. This was written without an Android SDK available.
- Licence: add one (MPL-2.0 is a natural fit with GeckoView).
