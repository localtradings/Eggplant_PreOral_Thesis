# Eggplant Disease Detector

An offline-first Android application for eggplant leaf-or-fruit disease screening with CameraX, a custom YOLO26m NCNN export, and on-device inference. The optional cloud layer adds anonymous Global Scans, private missing-disease requests, and a protected administration dashboard without moving inference off the device.

The center shutter supports a quick still scan and hold-to-detect live assistance. The first accepted live frame can show a provisional result; a second spatially compatible frame confirms it without an artificial confirmation delay. Gallery analysis remains local and is never eligible for Global Scans.

## Requirements

- Android Studio with JDK 17 or newer
- Android SDK 36
- Android 8.0 (API 26) or newer device or emulator

Open the repository in Android Studio and allow Gradle to sync. Command-line verification:

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug assembleDemo
./gradlew assembleDebugAndroidTest
./gradlew assembleRelease
```

The `demo` variant has release-equivalent behavior and is deliberately debug-signed so the thesis APK can be installed directly. It is not a Play Store signing configuration.

## Included Features

- Home, swipeable disease Library, Camera/Gallery workflow, Result, Scans, Settings, notifications, Scan Quality Tips, Data & Privacy, Help & FAQ, About, and Offline Status
- Six leaf-disease and two fruit-disease references with stable disease IDs
- English and Filipino (Tagalog) application localization
- System default, light, and dark themes
- System, Full, and Reduced motion preferences with accessible Compose transitions and micro-interactions
- On-device Room/SQLite persistence for the catalog, My Scans, cached Global Scans, outbox, missing-disease request status, settings, and notifications
- Manual local save by default, with deduplicated opt-in auto-save for eligible confirmed disease results
- Independent, default-off Healthy Leaf and Healthy Plant detection controls
- Anonymous, explicit, default-off sharing of eligible in-app camera results at 50% confidence or higher
- Private missing-disease requests with one to three real plant photos, explicit photo-rights consent, upload retry, and no training consent
- No advertisements, payments, user profiles, or cloud inference

## Architecture

The Android project has one `app` module, one `MainActivity`, Navigation Compose, CameraX, an application-scoped NCNN detector, a lifecycle-aware camera controller, and an `EggplantRepository` backed by Room. WorkManager drains an idempotent offline outbox only when the network is available. The app contains only the public mobile API URL, Supabase URL, and publishable key; privileged credentials remain server-side.

The `admin/` directory contains a Next.js App Router application deployed at [eggplant-disease-admin.vercel.app](https://eggplant-disease-admin.vercel.app). Protected server routes use Supabase magic-link sessions plus an `admin_members` authorization check. Mobile writes are validated by the server, rate-limited, owner-partitioned, and governed by Supabase RLS. The database and Storage schema lives in reviewed, forward-only migrations under `supabase/migrations/`.

Database rows retain stable disease IDs and grouped detections:

- disease ID
- confidence
- scan source
- timestamp
- model version

The packaged FP16 NCNN model is checksum-verified once per installed model version and warmed in the background. Live preview uses latest-frame backpressure, direct RGBA buffer preprocessing, bounded CPU inference, provisional first-frame boxes, two-frame confirmation, and a short visual hold to reduce flicker. Native failures are typed failures rather than healthy/no-match results.

The current runtime uses a `0.12` base confidence threshold for the exported NCNN score distribution, keeps Gallery/capture no-match states visible on the Result screen, uses a preferred 1024×768 live-analysis and still-capture stream, caps Gallery decode to a 1024 px long edge, preprocesses frames for the approved 768×768 model input, and defaults to bounded CPU inference for consistent mobile behavior.

## Privacy and cloud behavior

- Inference, catalog access, My Scans, and queued work remain available offline.
- A supported Global Scan requires an explicit Share action, an in-app camera source, at least 50% confidence, and a server-validated JPEG. Public API responses never include an owner identity.
- Two unique reports automatically quarantine a public scan pending review. Public photos expire after 180 days while anonymous aggregate counts may remain.
- Missing-disease photos are private to their anonymous owner and administrators. They are not public and are not approved for training.
- “Delete my shared cloud data” unpublishes contributions immediately and queues scoped object/row deletion.

## Validation boundary

Android unit, migration, build, and native fixture checks can run locally. Final accuracy parity requires the missing labeled `valid/images`, `valid/labels`, `test/images`, and `test/labels` folders. The ≤200 ms median / ≤350 ms p95 performance gate and 15-minute endurance run require the target Infinix HOT 60 Pro+ over USB debugging.

No Internet service is required for inference. Physical-phone latency, endurance, camera alignment, thermal behavior, and field accuracy still require the user’s final device and real-plant test pass; local builds do not establish those claims.

## License and third-party notices

The packaged model metadata identifies the Ultralytics YOLO model/export as AGPL-3.0; its license text is preserved at `third_party/licenses/AGPL-3.0.txt`. NCNN and its bundled components retain the notices in `app/src/main/cpp/third_party/ncnn/LICENSE.txt`. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for attribution and the unresolved repository-wide licensing review; this notice does not relicense the original application code.
