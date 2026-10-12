# CampusMate for Android

A fully native Kotlin app (Jetpack Compose, Material 3) for
https://campusmate-nith.vercel.app. It has every feature of the site:
timetable, notice board, assignments, exams, academic calendar, attendance,
syllabus, mess menu, notice explainer, study planner, Ask CampusMate, chat,
and the admin editors and publishing tools for CRs and admins.

The app talks to the same Flask backend (`/api/...` on the live site), so the
data is always the same as on the website. It signs in with Google through
Android's Credential Manager and sends the ID token to `/api/login`, exactly
like the website does.

## One-time Google Cloud setup (needed for sign-in)

Google only hands an Android app a sign-in token if the app is registered:

1. Open https://console.cloud.google.com/apis/credentials in the **same
   project** as the web client ID used in `GOOGLE_CLIENT_ID`.
2. *Create credentials → OAuth client ID → Application type: Android*.
3. Package name: `app.vercel.campusmate_nith`
4. SHA-1 certificate fingerprint of the debug key (used by CI debug builds):
   `2A:C4:13:D2:04:86:B6:0C:BC:55:56:CC:EA:20:71:8E:B7:BD:0E:67`
5. Save. Nothing needs copying back: the app keeps using the web client ID,
   which it fetches from `/api/config`.

When you add a release key (below), create a second Android client with the
release key's SHA-1 (`keytool -list -v -keystore campusmate-release.jks`).
If you publish on Play with Play App Signing, add Play's app signing SHA-1 too.

## Getting the app

GitHub Actions builds the app (`.github/workflows/android.yml`) on every push
that touches `android/`, and on demand from the **Actions** tab
(*Android app → Run workflow*).

- On a phone, open the repository's **Releases → android-latest** page and
  install `CampusMate.apk` (or `CampusMate-debug.apk` until a release key is set up).
  Android asks once to allow installs from your browser.
- Each workflow run also keeps the files as a downloadable artifact.

## Release key (Play Store)

1. Create a key once and keep the file and passwords somewhere safe. Losing it
   means you can never update the app on the Play Store.
   ```
   keytool -genkeypair -v -keystore campusmate-release.jks -alias campusmate \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. In GitHub, *Settings → Secrets and variables → Actions*, add:
   `ANDROID_KEYSTORE_BASE64` (output of `base64 -w0 campusmate-release.jks`),
   `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (`campusmate`), `ANDROID_KEY_PASSWORD`.
3. Register the release SHA-1 as an Android OAuth client (see above).
4. Re-run the workflow. `CampusMate.apk` (sideload) and `CampusMate.aab`
   (Play Console upload) now appear next to the debug APK.

## Building locally

Needs Android Studio or the Android SDK (platform 35) and JDK 17:
`cd android && ./gradlew assembleDebug`. Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Code map

- `data/Api.kt`: OkHttp client. Keeps the Flask session cookie and adds the
  `X-CampusMate: 1` header every POST needs.
- `data/Models.kt`: the JSON shapes from `data/*.json` and the API.
- `data/Logic.kt`: date, timetable, board and attendance helpers ported from `static/js/app.js`.
- `data/Store.kt`: on-device settings (year, section, hostel, planner, attendance cache).
- `data/GoogleAuth.kt`: Google Sign-In via Credential Manager.
- `ui/AppViewModel.kt`, `ui/CampusMateApp.kt`: app state and navigation.
- `ui/screens/`: one file per screen.

## Changing things

- Site address, app id, version: `app/build.gradle` (`BASE_URL`; bump
  `versionCode` for every Play upload).
- `debug.keystore` is a throwaway key so CI debug builds can update each other
  and so the debug SHA-1 above stays fixed.
