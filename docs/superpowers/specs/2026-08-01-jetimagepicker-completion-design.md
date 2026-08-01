# JetImagePicker Completion & Polish — Design

**Date:** 2026-08-01
**Status:** Approved

## Context

JetImagePicker is a small Jetpack Compose library (~10 source files) wrapping
gallery/camera image picking with runtime permission handling and basic
compression. It was built quickly last year and has never been properly
finished: it has real bugs, an outdated README, a contradictory publishing
story, and no tests. The goal now is to bring it to a genuinely publishable
state (public library, real users), not just patch it for personal use.

## Goals

- Fix correctness bugs that would bite real consumers of the library.
- Modernize gallery picking to Android's Photo Picker, removing unnecessary
  permission requirements on modern Android.
- Make the public API consistent (loading state, reset, docs that match code).
- Make it actually publishable: one coherent distribution story, real tests,
  CI.

## Non-goals

- Video picking, cropping/editing UI, multi-select reordering — out of scope,
  not requested.
- Instrumented/UI (Espresso/Compose UI) tests — low value for a wrapper this
  thin; unit tests cover the logic that matters.

## Design

### 1. Picker migration — Photo Picker as primary

Replace the hand-rolled `PickImagesContract` (`ACTION_PICK` + manual
`READ_EXTERNAL_STORAGE`/`READ_MEDIA_IMAGES` permission dance) with androidx's
built-in `PickVisualMedia` / `PickMultipleVisualMedia` activity result
contracts.

- These contracts already resolve device compatibility internally: native
  Photo Picker on API 33+, Google Play Services backport on older OEM
  devices that support it, `ACTION_GET_CONTENT` fallback otherwise.
- Gallery picking therefore needs **no runtime storage permission** in the
  common case. The gallery permission request path
  (`galleryPermissionLauncher`, `getGalleryPermission()`) is deleted
  entirely.
- `config.allowMultiple` selects `PickMultipleVisualMedia()` vs
  `PickVisualMedia()`.
- `PickImagesContract.kt` is deleted.
- Camera capture is unaffected — `CAMERA` permission is still requested via
  the existing `RequestPermission` flow.

### 2. Camera + state persistence fixes

- `tempCameraUri` (in `rememberImagePickerLauncher`) and `selectedUris` (in
  `rememberJetImagePickerState`) move from `remember` to `rememberSaveable`
  (via a `Uri` ↔ `String` saver) so both survive rotation and process death
  — notably, process death while the system camera app is foregrounded is a
  common real-world case that currently silently breaks the capture flow.
- `Utils.compressImage` returns a `FileProvider` URI
  (`FileProvider.getUriForFile`) instead of `Uri.fromFile(file)`, matching
  `createImageUri` and avoiding `FileUriExposedException` on API 24+ if the
  URI is ever passed to another app/component.
- Stale cache files are cleaned up: the previous compressed file is deleted
  when a new selection replaces it, and a pending camera temp file is
  deleted if the capture is cancelled.

### 3. Compression off the main thread + loading state

- `Utils.compressImage` becomes a `suspend fun`, doing decode/scale/compress
  on `Dispatchers.IO`. Called from a `rememberCoroutineScope` in
  `rememberImagePickerLauncher` instead of inline in the `ActivityResult`
  callback.
- `JetImagePickerState` gains `isLoading: Boolean`, true while compression
  is in flight (covers the multi-image case where several files compress
  sequentially).
- The `bitmap.scale(width, height)` stretch (distorts aspect ratio) is
  replaced with aspect-ratio-preserving scaling that fits within the
  requested target box.

### 4. Small API additions

- `JetImagePickerState.clearSelection()` resets `selectedImageUris` /
  `selectedImageUri` to empty/null.
- `ImagePreview` / `MultiImagePreview` take a real `contentDescription`
  parameter (sensible default) instead of a hardcoded string / `null`.

### 5. Docs & publishing consistency

- One distribution story: Maven Central, `io.github.nerojust:jetimagepicker`
  — matches the `maven-publish` + `signing` setup already present in
  `build.gradle.kts`. The conflicting JitPack (`com.github.nerojust`)
  install instructions in the README are removed.
- README usage example is corrected to match actual code: `ImagePickerResult`
  imports from `com.nerojust.jetimagepicker.result` (and the file itself
  moves from `model/ImagePickerResult.kt` to `result/ImagePickerResult.kt`
  to match its own package declaration).
- README documents the new permission story: gallery picking needs no
  permission on modern Android; camera capture still requires `CAMERA`.
- README's manifest/`file_paths.xml` setup instructions are re-verified
  against what the library actually requires post-migration (gallery
  permission entries removed if no longer needed).

### 6. Tests & CI

- Delete the unedited Android Studio template tests (`ExampleUnitTest`,
  `ExampleInstrumentedTest` in both `JetImagePicker` and `app` modules).
- Add unit tests for the pure-logic units:
  - Permission state calculation (`calculatePermissionState` logic —
    extracted to a testable pure function if not already easily testable).
  - `JetImagePickerConfig` defaults.
  - `ImagePickerResult` mapping from `PermissionState`.
  - Aspect-ratio-preserving scale math.
- Add a GitHub Actions workflow: assemble + lint + unit test on push/PR.

## Testing Strategy

Unit tests only, targeting the logic pieces listed above. No
instrumented/UI test investment — the composables here are thin wrappers
around platform contracts and Coil; the value is in the pure logic, not in
rendering.

## Open Questions

None outstanding — scope and key architectural decisions (Photo Picker
migration, loading state, test depth) were confirmed with the user before
writing this spec.
