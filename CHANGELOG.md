# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-08-01

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

### Fixed
- Compressed images are now returned as `FileProvider` URIs instead of raw `file://` URIs, avoiding `FileUriExposedException` on API 24+.
- Selected images and an in-flight camera capture now survive rotation and process death (previously lost via plain `remember`).
- Stale compressed/temp cache files are now deleted instead of accumulating indefinitely.

### Removed
- `PickImagesContract` (replaced by the Photo Picker contracts above).
- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` manifest permissions (no longer needed for gallery picking).

[1.0.0]: https://github.com/nerojust/JetImagePicker/releases/tag/v1.0.0
