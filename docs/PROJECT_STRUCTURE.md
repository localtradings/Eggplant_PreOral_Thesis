# Project Structure

The repository contains one Android application module. Runtime code and local model-development files are deliberately separated so each folder has one clear purpose.

## Top-Level Folders

- `app/` — Android application source, resources, tests, Room schemas, native libraries, and packaged NCNN model.
- `ml/` — dataset configuration and evaluation evidence; runtime models and source checkpoints do not live here.
- `tools/model-export/` — the dependency-free validator/installer for an approved external NCNN export.
- `docs/` — project documentation and UI reference images.
- `gradle/` — Gradle wrapper and dependency configuration.

## Android Source

Android Kotlin source is under `app/src/main/java/com/eggplant/detector/`:

- `app/` — application startup, the main activity, app-wide ViewModel, and navigation.
- `core/` — formatting, reusable Compose components, and theme definitions.
- `data/catalog/` — bundled disease descriptions and Room seed data.
- `data/database/` — the canonical Room database, DAOs, entities, schema migrations, and persistence mappers.
- `data/files/` — private scan-snapshot storage.
- `data/repository/` — the local repository connecting Room and private files to the UI.
- `detection/api/` — runtime-neutral detection contracts and result types.
- `detection/ncnn/` — model metadata, NCNN engine, JNI bridge contract, and native-result mapping.
- `detection/tracking/` — multi-frame detection stability and scene deduplication.
- `domain/model/` — app-level disease, navigation, and scan-result models.
- `feature/` — still-capture camera flow with hold-to-preview live assistance, home, disease library, results, history, settings, notifications, and information screens.

The C++ bridge is `app/src/main/cpp/detection/eggplant_ncnn_bridge.cpp`. The Android app loads only the checksum-verified 768×768 NCNN files in `app/src/main/assets/models/eggplant-yolo26m/`; input size and class count are passed to the native engine from the pinned Kotlin model metadata.

## Database

`EggplantDatabase` in `data/database/` is the only database connected to the application. It opens the private on-device file `eggplant_detector.db` and remains at schema version 2. Historical schemas are stored under `app/schemas/com.eggplant.detector.data.database.EggplantDatabase/`.

Files outside this repository, including the OpenToonz profile database reference folder, are not part of the Android application and are never loaded at runtime.

## Tests

- `app/src/test/` mirrors the production package structure for unit tests.
- `app/src/androidTest/` mirrors the production package structure for Room migration, Compose flow, camera-controller, and real-model instrumentation tests.
