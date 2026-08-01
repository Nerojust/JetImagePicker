# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.1] - 2026-08-01

### Added
- `JetImagePickerState.isLoading` — true while picked images are being compressed.
- `JetImagePickerState.clearSelection()` — resets the current selection.
- Unit tests for config defaults, aspect-ratio scaling, and permission-to-result mapping.
- GitHub Actions CI (lint, ktlint, detekt, unit tests, assemble).
- ktlint and detekt static analysis.
- KDoc on the public API.

### Changed
- Gallery picking now uses Android's Photo Picker (`PickVisualMedia`/`PickMultipleVisualMedia`) instead of a custom `ACTION_PICK` contract — no storage runtime permission is required anymore.
- Image compression now runs off the main thread and preserves aspect ratio instead of stretching to the exact target size.
- `ImagePickerResult` moved to its correct `result` package (was misplaced under `model`).
- The example `app` module now depends on the local `JetImagePicker` module source instead of a published artifact.
- README usage example now matches the current API in context (disables pick buttons while `isLoading`, includes the `clearSelection` button) instead of documenting `isLoading`/`clearSelection` in a separate, disconnected section; corrected a stale "even on Android 13+" permission claim.
- `CONTRIBUTING.md` corrected to describe the detekt baseline as a one-time snapshot rather than something that grandfathers ongoing issues.
- Distribution: settled on JitPack (`com.github.nerojust:JetImagePicker`) as the actual, verifiable distribution path for now — neither it nor Maven Central (`io.github.nerojust:jetimagepicker`, still configured in `build.gradle.kts` for a possible future publish) had ever actually been published; JitPack needs no external account/signing setup and works directly off a pushed git tag.
- Added a "Common Gotchas" section to the README covering the three-way permission result and the `targetWidth`/`targetHeight` pairing requirement.

### Fixed
- Compressed images are now returned as `FileProvider` URIs instead of raw `file://` URIs, avoiding `FileUriExposedException` on API 24+.
- Selected images and an in-flight camera capture now survive rotation and process death (previously lost via plain `remember`).
- Stale compressed/temp cache files are now deleted instead of accumulating indefinitely.
- Multi-select compression could silently overwrite one photo with another: compressed output files were named from a millisecond timestamp alone, so two images compressed within the same millisecond collided on the same filename. Filenames are now guaranteed unique.
- Camera permission denial could never be reported as a plain (non-permanent) denial — `isDenied` and `isPermanentlyDenied` were computed identically, so a first-time denial was always reported as permanent. The launcher now remembers whether the camera permission has been requested before to correctly distinguish the two.

### Example app
- Rewritten into a fuller interactive showcase: live toggles for single vs. multiple selection and compression on/off, a loading indicator tied to `isLoading`, a "Clear Selection" button wired to `clearSelection()`, and a per-image file size readout (labeled compressed/original) so the effect of compression is directly visible.
- Fixed content rendering underneath the status bar (`enableEdgeToEdge()` was enabled with no corresponding inset padding).

### Removed
- `PickImagesContract` (replaced by the Photo Picker contracts above).
- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` manifest permissions (no longer needed for gallery picking).

[1.0.1]: https://github.com/nerojust/JetImagePicker/releases/tag/v1.0.1
