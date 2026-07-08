# Eggplant Disease Detector

An offline Android application for tap-to-capture eggplant disease screening with hold-to-preview live assistance, CameraX, a custom YOLO26m NCNN export, and on-device inference.

The app uses the center shutter in two modes: tap once to capture and analyze a still image, or hold to preview live detections while framing the subject. Gallery images, captured photos, grouped disease results, and saved snapshots remain private on the phone. Saving happens manually from the Result screen.

## Requirements

- Android Studio with JDK 17 or newer
- Android SDK 36
- Android 8.0 (API 26) or newer device or emulator

Open the repository in Android Studio and allow Gradle to sync. Command-line verification:

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease
```

The release task currently signs a local thesis-demo APK with the standard debug key so it can be installed directly for testing. It is not a production or Play Store signing configuration.

## Included Features

- Home, disease Library, Camera/Gallery workflow, Result, History, Settings, notifications, Scan Quality Tips, Data & Privacy, Help & FAQ, About, and Offline Status
- Six leaf-disease and two fruit-disease references with stable disease IDs
- English and Filipino (Tagalog) application localization
- System default, light, and dark themes
- On-device Room/SQLite persistence for the localized disease catalog, grouped scan history, settings, and notification read state
- Manual save from the Result screen only
- Independent, default-off Healthy Leaf and Healthy Plant detection controls
- Offline-only runtime with no account, backend, synchronization, analytics, advertisements, payments, or Internet permission

## Architecture

The project has one `app` module, one `MainActivity`, Navigation Compose, CameraX, a lifecycle-aware `DetectionEngine`, JNI/NCNN inference, an activity-scoped `EggplantAppViewModel`, and an `EggplantRepository` backed by Room. Runtime code is grouped by app shell, shared core utilities, data, detection, domain models, and user-facing features. See [Project Structure](docs/PROJECT_STRUCTURE.md) for the directory map.

Database rows retain stable disease IDs and grouped detections:

- disease ID
- confidence
- scan source
- timestamp
- model version

The packaged FP16 NCNN model is checksum-verified before loading. Live preview uses latest-frame backpressure, can render tentative boxes from the first accepted frame, and confirms boxes after two matching frames over at least 400 ms with a brief 750 ms hold to reduce flicker while framing.

The current runtime uses a `0.12` base confidence threshold for the exported NCNN score distribution, keeps Gallery/capture no-match states visible on the Result screen, uses a preferred 1024×768 live-analysis and still-capture stream, caps Gallery decode to a 1024 px long edge, preprocesses frames for the approved 768×768 model input, and defaults to bounded CPU inference for consistent mobile behavior.

## Validation Boundary

Android unit, migration, build, and native fixture checks can run locally. Final accuracy parity requires the missing labeled `valid/images`, `valid/labels`, `test/images`, and `test/labels` folders. The ≤200 ms median / ≤350 ms p95 performance gate and 15-minute endurance run require the target Infinix HOT 60 Pro+ over USB debugging.

No Internet service is required or planned for inference.
