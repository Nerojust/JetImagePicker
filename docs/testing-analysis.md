# JetImagePicker testing analysis

## Current stack

- JUnit 4 runs fast local unit tests.
- Robolectric runs Android-framework and Compose behavior tests locally on API 28, which is compatible with the project's Java 11 toolchain.
- Compose UI Test validates semantics and rendered item counts.
- AndroidX Test and the AndroidJUnit4 runner execute integration tests on devices or emulators.
- UI Automator is available for system picker end-to-end journeys.
- JaCoCo generates local unit-test coverage reports.
- No dependency-injection or mocking framework is installed. The library has no application dependency graph; pure helpers and small fakes are preferred.

## Coverage by layer

### Unit tests

Configuration validation, aspect-ratio conversion, permission mapping, gallery limits, request serialization, ownership classification, image sizing, video duration boundaries, trim ranges, and source cleanup decisions.

### Robolectric tests

Wrapped Activity discovery and Compose preview semantics run locally without an emulator.

### Device integration tests

Real image decoding/compression and EXIF handling, Media3 video compression, FileProvider-backed owned-file deletion, stale cache pruning, and crop output failure cleanup run against Android framework implementations.

### End-to-end tests

The sample app has smoke tests for Photo Picker cancellation and a real CameraX video recording on the API 34 emulator. On the API 28 AVD, CameraX never leaves `PENDING_RECORDING` (the AVD advertises a front camera it lacks), so `videoCamera_canRecordAndReturnAClip` fails there with `ERROR_NO_VALID_DATA`; run it on API 34 or a physical device. Image capture through `ACTION_IMAGE_CAPTURE` still needs a device image with a camera app installed; the available AVD has no compatible handler. UI Automator drives the system picker and emulator camera UI where those handlers exist.

The API 28 AVC emulator cannot complete the high bitrate export, so that test verifies the source remains readable for the picker fallback. A successful high bitrate transcode still needs a compatible API 28 codec/device.

## Commands

```bash
./gradlew :JetImagePicker:testDebugUnitTest
./gradlew :JetImagePicker:jacocoTestReport
./gradlew :JetImagePicker:connectedDebugAndroidTest
./gradlew :JetImagePicker:lintDebug :JetImagePicker:detekt :JetImagePicker:ktlintCheck
```

The JaCoCo HTML report is written under `JetImagePicker/build/reports/jacoco/jacocoTestReport/html`.

## Remaining device matrix

Run the instrumented suite on API 23, API 27, API 28, and a current API level. API 23/27 exercise the BitmapFactory and legacy EXIF path; API 28+ exercises ImageDecoder; a current API exercises modern Photo Picker, CameraX, and Media3 behavior.
