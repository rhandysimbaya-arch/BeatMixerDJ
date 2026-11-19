# Beat Mixer DJ

A two-deck DJ mixer for Android phones and tablets (Kotlin + Jetpack Compose, minSdk 26).
The layout is based on the screenshot: decks A/B, filters, echo, mic / MC talk, auto-mix, REC,
crossfader, a bar of drop pads, EQ, master volume, VU meter, STOP ALL, and a side rail
(DROPS / MIX / MUSIC / PLAY / TOOLS / HELP).

## Build the APK

**Android Studio (easiest):** File > Open this folder, let Gradle sync, press Run (or Build > Build APK).

**Command line:** needs JDK 17 and the Android SDK (platform 35).
    ./gradlew assembleDebug
APK: app/build/outputs/apk/debug/app-debug.apk

**No PC set up?** Push this folder to a GitHub repo. The included workflow
(.github/workflows/build-apk.yml) builds the APK; download it from the run's "Artifacts".

## Layouts
- Width >= 600dp (tablets, phones in landscape): 3-column mixer with a side rail.
- Narrower (phones in portrait): decks side by side, controls below, bottom navigation.
Rotation is allowed and does not interrupt playback.

## Notes
- Audio engine: custom 44.1 kHz mixer (decode with MediaCodec, mix to AudioTrack). Songs are decoded
  into memory when loaded (max 8 minutes each).
- TEMPO changes pitch with speed, like a record (no key-lock). Range +/-30%.
- BPM is auto-detected (70-180 BPM); it can be off on songs without a clear beat.
- Drops are synthesized in code; long-press a drop on the DROPS screen to rename it or use your own file.
- Playback stops if Android closes the app in the background; the screen is kept on while the app is open.
- Rename the app in app/src/main/res/values/strings.xml.
