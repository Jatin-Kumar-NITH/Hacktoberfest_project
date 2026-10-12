# CampusMate for Android

The Android app is a **Trusted Web Activity** (TWA): it opens
https://campusmate-nith.vercel.app full screen inside Chrome. Every web change
shows up in the app straight away, and Google Sign-In works because it runs in
the real browser (Google blocks sign-in inside plain WebViews).

## Getting the app

GitHub Actions builds the app (`.github/workflows/android.yml`) on every push
to `main` that touches `android/`, and on demand from the **Actions** tab
(*Android app → Run workflow*).

- On a phone, open the repository's **Releases → android-latest** page and
  install `CampusMate.apk` (or `CampusMate-debug.apk` until a release key is set up).
  Android asks once to allow installs from your browser.
- Each workflow run also keeps the files as a downloadable artifact.

The debug APK shows a small address bar at the top. That bar disappears once
the app is signed with the release key and the site lists that key (below).

## Release key (full screen, Play Store)

1. Create a key once and keep the file and passwords somewhere safe. Losing it
   means you can never update the app on the Play Store.
   ```
   keytool -genkeypair -v -keystore campusmate-release.jks -alias campusmate \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. In GitHub, *Settings → Secrets and variables → Actions*, add:
   `ANDROID_KEYSTORE_BASE64` (output of `base64 -w0 campusmate-release.jks`),
   `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (`campusmate`), `ANDROID_KEY_PASSWORD`.
3. Get the fingerprint: `keytool -list -keystore campusmate-release.jks -alias campusmate`
   and copy the `SHA256:` line.
4. In Vercel, add the environment variable `ANDROID_CERT_SHA256` with that value
   and redeploy. Check https://campusmate-nith.vercel.app/.well-known/assetlinks.json shows it.
   If you publish on Play with Play App Signing, add Play's app signing key
   fingerprint too (comma separated).
5. Re-run the workflow. `CampusMate.apk` (sideload) and `CampusMate.aab`
   (Play Console upload) now appear next to the debug APK.

## Building locally

Needs Android Studio or the Android SDK (platform 35) and JDK 17:
`cd android && ./gradlew assembleDebug`. Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Changing things

- Site address, app id, version: `app/build.gradle` (bump `versionCode` for every Play upload).
  The app id must match `ANDROID_PACKAGE` in `app.py`.
- Icons: `python tools/build_icons.py` redraws the web and Android icons.
- `debug.keystore` is a throwaway key so CI debug builds can update each other.
  Never put its fingerprint in `ANDROID_CERT_SHA256`.
