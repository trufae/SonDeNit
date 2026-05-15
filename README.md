<h1 align="center">Son de Nit</h1>

<p align="center">
  <strong>Sleep sound tracking for Android, private by default.</strong>
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Version" src="https://img.shields.io/badge/version-0.3-6C63FF">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-yellow">
</p>

Son de Nit is a quiet, local-first Android app for following what happens while
you sleep. Start a session before going to bed, let the phone listen beside you,
and review the night through audio events, interruptions, screen activity,
quality hints, and a visual timeline.

The UI is currently in Catalan.

## Highlights

- Record sleep sessions with a foreground microphone service.
- Save detected audio chunks locally as AAC/M4A files.
- Show live ambient level, waveform activity, elapsed time, and recent events.
- Review past sessions with quality score, sleep phase estimate, interruption
  count, ambient noise, and event timeline.
- Play back detected sound groups from the detail view.
- Adjust grouping and minimum interruption thresholds per review.
- Rename or delete sessions from the app.
- Track screen-on events during the night.
- Classify sounds with a lightweight rule-based classifier for hints such as
  speech, cough, movement, snoring, dog barks, and cat meows.

## Privacy

Son de Nit is designed to keep sleep data on the device.

- There is no `INTERNET` permission in the Android manifest.
- Sessions are stored under the app's private files directory.
- Each session contains metadata, JSONL events, computed stats, and local audio
  chunks.
- Sound classification is best-effort and rule-based. It is not a medical
  device, diagnosis tool, or clinical sleep analysis system.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Android Gradle Plugin
- Foreground microphone service
- Local filesystem session store
- AAC/M4A chunk encoding

## Project Layout

```text
app/src/main/java/com/example/sondenit/
  audio/        Audio capture, encoding, grouping, playback, classification
  data/         Sessions, events, stats, filesystem repository
  service/      Recording controller and foreground service
  ui/           Compose screens, components, and theme
  util/         Date and duration formatting helpers
```

## Requirements

- Android Studio or the Android SDK command-line tools
- JDK 11 or newer
- Android SDK 36 installed
- Android 7.0+ device or emulator

## Build

Build a debug APK:

```sh
./gradlew :app:assembleDebug
```

Install a debug build on a connected device:

```sh
./gradlew :app:installDebug
```

Build, align, and self-sign the release APK in the repository root:

```sh
make release
```

The release target creates:

```text
SonDeNit-0.3.apk
```

## Permissions

Son de Nit asks only for permissions needed for overnight recording:

- `RECORD_AUDIO` to detect and record sound events.
- `WAKE_LOCK` to keep session tracking active while the device sleeps.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE` for long-running
  microphone capture.
- `POST_NOTIFICATIONS` on Android 13+ so the recording service can show its
  status notification.

## License

MIT. See [LICENSE](LICENSE).
