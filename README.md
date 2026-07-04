# Eggplant Disease Detector

An offline Android application for live identification of common eggplant leaf and fruit diseases using CameraX, a custom YOLO26m export, and NCNN.

The app performs live on-device inference, draws tappable confidence boxes, analyzes shutter and Gallery images, supports torch control, and saves grouped detections only when manual or automatic saving is selected. Saved snapshots and records remain private on the phone.

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
- System default, metric, and imperial unit preferences
- On-device Room/SQLite persistence for the localized disease catalog, grouped scan history, settings, and notification read state
- Manual save by default, with optional scene-deduplicated automatic saving
- Offline-only runtime with no account, backend, synchronization, analytics, advertisements, payments, or Internet permission

## Architecture

The project has one `app` module, one `MainActivity`, Navigation Compose, CameraX, a lifecycle-aware `DetectionEngine`, JNI/NCNN inference, an activity-scoped `AppViewModel`, and a local repository backed by Room. Database rows retain stable disease IDs and grouped detections:

- disease ID
- confidence
- scan source
- timestamp
- model version

The packaged FP16 NCNN model is checksum-verified before loading. Live analysis uses latest-frame backpressure and stability requires three matching frames over at least 1.25 seconds.

## Validation Boundary

Android unit, migration, build, and native fixture checks can run locally. Final accuracy parity requires the missing labeled `valid/images`, `valid/labels`, `test/images`, and `test/labels` folders. The ≤200 ms median / ≤350 ms p95 performance gate and 15-minute endurance run require the target Infinix HOT 60 Pro+ over USB debugging.

No Internet service is required or planned for inference.
