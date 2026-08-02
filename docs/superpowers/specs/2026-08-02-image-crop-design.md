# Image Cropping (v2) — Design

**Date:** 2026-08-02
**Status:** Approved
**Branch:** `v2`

## Context

JetImagePicker currently covers pick (gallery/camera), permission handling, and
compression/resize, but stops short of the #1 thing consumers reach for next:
cropping the picked image (most commonly for avatar/profile-picture flows).
This is the first feature added since the 1.0.x polish pass, and is being
treated as a `v2` milestone.

## Goal

Add an optional, in-library crop step that runs automatically between
picking/capturing an image and compressing it, with zero extra composable
code required from the consumer beyond a config flag.

## Non-goals

- Cropping multiple images in a multi-select gallery pick. Sequentially
  cropping N images is a materially bigger, separate feature with unclear
  demand — out of scope for this pass.
- Custom crop shapes (circle, polygon) — square/free/custom-ratio rectangular
  crop covers the realistic use cases (avatars, general photos, arbitrary
  ratios) without over-building.
- Any change to the existing compression/resize behavior — crop is a new step
  that feeds into the existing pipeline unchanged.

## Design

### 1. Dependency

Add `com.mr0xf:easycrop` (Compose-native, MIT licensed, actively maintained,
resolves from Maven Central — no new repository needed). It provides
`rememberImageCropper()` (a composable returning an `ImageCropper` with a
suspend `crop(uri, context)` function and a `cropState`) and
`ImageCropperDialog(state)` to render the crop UI.

### 2. Config surface

`JetImagePickerConfig` gains two new fields, both additive with defaults
(existing callers unaffected):

```kotlin
val enableCrop: Boolean = false
val cropAspectRatio: CropAspectRatio = CropAspectRatio.Free
```

New sealed class in the `config` package:

```kotlin
sealed class CropAspectRatio {
    data object Free : CropAspectRatio()
    data object Square : CropAspectRatio()
    data class Custom(val ratioX: Float, val ratioY: Float) : CropAspectRatio()
}
```

### 3. Scope rule — when crop applies

Crop runs only when exactly **one** image enters the pipeline for a given
pick/capture action:

- Camera capture: always exactly one image → crop applies whenever
  `enableCrop` is true, regardless of `allowMultiple`.
- Gallery pick: crop applies only when the picked result is a single URI
  (i.e. effectively when `allowMultiple = false`, since multi-select can
  return more than one). A multi-select pick that happens to return one URI
  is treated the same as single-select for this purpose — the rule is about
  the actual result cardinality, not the config flag alone.

### 4. Flow

```
pick/capture → [crop, if eligible] → compress (existing, unchanged) → result
```

The cropped output is written to a cache file via `FileProvider`, the same
pattern `Utils.compressImage` already uses for its own output, then handed
to the existing compression step as if it were the original picked URI.
Compression already handles resizing/quality, so cropping first (usually
shrinking the pixel count) is a net win, not extra work.

Cancelling the crop dialog is treated like any other cancellation already in
this library: an empty result, nothing picked.

### 5. UI wiring

Per the approved direction, this is automatic with zero consumer code. The
crop dialog is rendered from inside `rememberImagePickerLauncher`/
`rememberJetImagePickerState` itself — a `remember*`-named composable
emitting real UI as a side effect. This is a deliberate deviation from the
"remember functions only return state" convention, made explicitly to keep
the "just works, no hidden setup" promise for this feature. It's called out
here so it isn't mistaken for an accident later.

### 6. Testing

Consistent with the rest of this library: UI/gesture code is not unit-tested
without Robolectric, which stays out of scope. The one pure, testable unit is
`CropAspectRatio` → numeric ratio conversion (e.g. `Square` → `1f`, `Custom`
→ `ratioX / ratioY`, `Free` → `null`), covered with a plain JUnit test.

## Versioning

This ships as `2.0.0` (`JetImagePicker/build.gradle.kts` `version` and the
`maven-publish` block's `version`), on a dedicated `v2` branch, merged and
tagged separately from the `v1.x` line once complete.

## Open Questions

None outstanding — scope, library choice, and UI wiring were confirmed with
the user before writing this spec.
