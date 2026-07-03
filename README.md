# Eggplant Disease Detector

An offline-first Android UI prototype for identifying eggplant leaf and fruit problems. It is implemented entirely with Kotlin and Jetpack Compose. Detection is deterministic mock behavior; no camera API, network, backend, database, analytics, authentication, payment service, or AI model is connected.

## Setup

Requirements:

- Android Studio with JDK 17 or newer
- Android SDK 36
- An Android 8.0 (API 26) or newer device/emulator

Open this repository in Android Studio and allow Gradle to sync. Command-line verification uses Android Studio's bundled JDK:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
```

## Architecture

The project has one `app` module, one `MainActivity`, Navigation Compose, reusable UI components, immutable models, deterministic mock data, and one activity-scoped `AppViewModel`. Runtime state is intentionally in memory and resets when the process restarts.

## Screens

- Home
- Library and Disease Detail
- Mock Camera
- Result
- History and History Detail
- Settings

The Library contains exactly six Leaf Disease entries and two Fruit Disease entries. Confidence is shown only on Result, History, and History Detail. Risk/severity labels and broad disease categories are intentionally absent.

## Mock Detection

Capture returns Leaf Spot at 87% confidence. Gallery selection returns Fruit Borer at 91%. Saving is idempotent and updates in-memory history only.

## Future YOLO 26 Medium Integration

The future model adapter should implement `DetectionProvider` and return the existing `ScanResult` model. It must replace `MockDetectionProvider` without changing screen contracts. Real inference, image acquisition, model packaging, and performance work are out of scope for this UI milestone.
