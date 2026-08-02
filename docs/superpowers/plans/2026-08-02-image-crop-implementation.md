# Image Cropping (v2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional, automatic crop step (via `com.mr0xf:easycrop`) between picking/capturing an image and compressing it, per `docs/superpowers/specs/2026-08-02-image-crop-design.md`.

**Architecture:** New `CropAspectRatio` sealed class + two new `JetImagePickerConfig` fields; the crop step and its dialog are wired directly into the existing `rememberImagePickerLauncher`/`processPicked` flow in `ImagePickerLauncher.kt`, so it's automatic with no new consumer-facing composable.

**Tech Stack:** `com.mr0xf:easycrop:0.1.1` (Maven Central, Compose-native cropper).

## Global Constraints

- Same minSdk 21 / compileSdk 36 / Kotlin 2.2.0 / Java 11 toolchain as the rest of the project — unaffected by this feature.
- No Robolectric — unit tests stay plain JUnit, no Android framework calls in test bodies.
- Public API additions must be additive only: existing `JetImagePickerConfig()` callers and `rememberJetImagePickerState(context, config, onResult)`'s signature must not change.
- Crop applies only when exactly one image enters the pipeline (camera capture always; gallery pick only when the result is a single URI) — never for a multi-image gallery pick.
- This ships as version `2.0.0`.

---

### Task 1: Add the `easycrop` dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `JetImagePicker/build.gradle.kts`

**Interfaces:** None — dependency setup only.

- [ ] **Step 1: Add the catalog entry**

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
easycrop = "0.1.1"
```

Add to `[libraries]`:

```toml
easycrop = { group = "io.github.mr0xf00", name = "easycrop", version.ref = "easycrop" }
```

- [ ] **Step 2: Add the dependency**

In `JetImagePicker/build.gradle.kts`, add to the `dependencies { }` block:

```kotlin
    implementation(libs.easycrop)
```

- [ ] **Step 3: Verify it resolves**

Run: `./gradlew :JetImagePicker:assembleDebug`
Expected: BUILD SUCCESSFUL. If `io.github.mr0xf00:easycrop:0.1.1` fails to resolve from Maven Central, check the library's current published coordinates/version (they may have changed) and use the actual current values instead, noting the change.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml JetImagePicker/build.gradle.kts
git commit -m "chore: add easycrop dependency for v2 crop support"
```

---

### Task 2: `CropAspectRatio` sealed class + ratio conversion + test

**Files:**
- Create: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/config/CropAspectRatio.kt`
- Test: `JetImagePicker/src/test/java/com/nerojust/jetimagepicker/config/CropAspectRatioTest.kt`

**Interfaces:**
- Produces: `CropAspectRatio` sealed class (`Free`, `Square`, `Custom(ratioX: Float, ratioY: Float)`) and `CropAspectRatio.toRatioOrNull(): Float?` extension — `null` means unconstrained (free) crop. Consumed by Task 4's crop-region-setup code.

- [ ] **Step 1: Write the failing test**

Create `JetImagePicker/src/test/java/com/nerojust/jetimagepicker/config/CropAspectRatioTest.kt`:

```kotlin
package com.nerojust.jetimagepicker.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropAspectRatioTest {

    @Test
    fun `free has no ratio`() {
        assertNull(CropAspectRatio.Free.toRatioOrNull())
    }

    @Test
    fun `square is 1 to 1`() {
        assertEquals(1f, CropAspectRatio.Square.toRatioOrNull())
    }

    @Test
    fun `custom divides ratioX by ratioY`() {
        val ratio = CropAspectRatio.Custom(ratioX = 16f, ratioY = 9f).toRatioOrNull()
        assertEquals(16f / 9f, ratio!!, 0.0001f)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :JetImagePicker:testDebugUnitTest --tests "com.nerojust.jetimagepicker.config.CropAspectRatioTest"`
Expected: FAIL — `CropAspectRatio` is unresolved.

- [ ] **Step 3: Implement**

Create `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/config/CropAspectRatio.kt`:

```kotlin
package com.nerojust.jetimagepicker.config

/**
 * Constrains the crop region shown when [JetImagePickerConfig.enableCrop] is true.
 */
sealed class CropAspectRatio {

    /** No constraint — the user can freely resize the crop region. */
    data object Free : CropAspectRatio()

    /** 1:1 — the classic avatar/profile-picture crop. */
    data object Square : CropAspectRatio()

    /** An arbitrary width:height ratio, e.g. `Custom(16f, 9f)` for 16:9. */
    data class Custom(val ratioX: Float, val ratioY: Float) : CropAspectRatio()
}

/** The numeric width/height ratio for this [CropAspectRatio], or `null` if unconstrained. */
fun CropAspectRatio.toRatioOrNull(): Float? =
    when (this) {
        is CropAspectRatio.Free -> null
        is CropAspectRatio.Square -> 1f
        is CropAspectRatio.Custom -> ratioX / ratioY
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :JetImagePicker:testDebugUnitTest --tests "com.nerojust.jetimagepicker.config.CropAspectRatioTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add JetImagePicker/src/main/java/com/nerojust/jetimagepicker/config/CropAspectRatio.kt JetImagePicker/src/test/java/com/nerojust/jetimagepicker/config/CropAspectRatioTest.kt
git commit -m "feat: add CropAspectRatio"
```

---

### Task 3: `JetImagePickerConfig` gains `enableCrop`/`cropAspectRatio`

**Files:**
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/config/JetImagePickerConfig.kt`

**Interfaces:**
- Consumes: `CropAspectRatio` (Task 2).
- Produces: `JetImagePickerConfig.enableCrop: Boolean = false`, `JetImagePickerConfig.cropAspectRatio: CropAspectRatio = CropAspectRatio.Free` — additive, existing call sites unaffected.

- [ ] **Step 1: Add the fields and update the KDoc**

Replace the contents of `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/config/JetImagePickerConfig.kt`:

```kotlin
// File: config/JetImagePickerConfig.kt
package com.nerojust.jetimagepicker.config

/**
 * Configuration for [rememberJetImagePickerState] and [rememberImagePickerLauncher].
 *
 * @property enableCompression If true, picked/captured images are compressed (and optionally resized) before the result callback fires.
 * @property compressionQuality JPEG quality (0-100) used when [enableCompression] is true.
 * @property targetWidth Optional max width (px) to fit picked images into, preserving aspect ratio. Requires [targetHeight] to also be set.
 * @property targetHeight Optional max height (px) to fit picked images into, preserving aspect ratio. Requires [targetWidth] to also be set.
 * @property allowMultiple If true, the gallery picker allows selecting more than one image.
 * @property enableCrop If true, a crop step runs before compression whenever exactly one image was picked/captured (camera capture always; gallery pick only when a single image was selected).
 * @property cropAspectRatio The aspect ratio the crop region is constrained to when [enableCrop] is true.
 */
data class JetImagePickerConfig(
    val enableCompression: Boolean = true,
    val compressionQuality: Int = 75, // 0–100
    val targetWidth: Int? = null,     // Optional resizing
    val targetHeight: Int? = null,
    val allowMultiple: Boolean = true, // For future extensibility
    val enableCrop: Boolean = false,
    val cropAspectRatio: CropAspectRatio = CropAspectRatio.Free
)
```

- [ ] **Step 2: Run the existing config test to confirm no regression**

Run: `./gradlew :JetImagePicker:testDebugUnitTest --tests "com.nerojust.jetimagepicker.config.JetImagePickerConfigTest"`
Expected: PASS (the existing test only asserts the pre-existing fields' defaults, which are unchanged)

- [ ] **Step 3: Commit**

```bash
git add JetImagePicker/src/main/java/com/nerojust/jetimagepicker/config/JetImagePickerConfig.kt
git commit -m "feat: add enableCrop and cropAspectRatio to JetImagePickerConfig"
```

---

### Task 4: Wire the crop step into the launcher (automatic dialog, before compression)

**Files:**
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/launchers/ImagePickerLauncher.kt`

**Interfaces:**
- Consumes: `CropAspectRatio.toRatioOrNull()` (Task 2), `config.enableCrop`/`config.cropAspectRatio` (Task 3).
- Produces: no public signature change to `rememberImagePickerLauncher` — the crop dialog is rendered internally.

This is the integration point with the most external-library uncertainty in this plan. `com.mr0xf.easycrop` (confirmed from its current source):
- `rememberImageCropper(): ImageCropper` — composable; `ImageCropper.cropState: CropState?` (non-null while a crop is in progress).
- `suspend fun ImageCropper.crop(uri: Uri, context: Context, maxResultSize: IntSize? = DefaultMaxCropSize, cacheBeforeUse: Boolean = true): CropResult` — `CropResult` is `CropResult.Success(bitmap: ImageBitmap)`, `CropResult.Cancelled`, or `CropError`.
- `CropState.region: Rect` (mutable), `CropState.aspectLock: Boolean` (mutable) — there is no direct "set ratio" call; a fixed ratio is achieved by setting `region` to a centered rect of the desired ratio, then setting `aspectLock = true` so further drags preserve it.
- `ImageCropperDialog(state: CropState)` — composable that renders the actual crop UI.

Before writing code, confirm these signatures still match the resolved `easycrop:0.1.1` sources (e.g. via "Go to definition" in the IDE, or by inspecting the sources jar Gradle downloaded) — the design doc flags this as verified-but-not-guaranteed-stable across versions.

- [ ] **Step 1: Add the crop step to `processPicked` and render the dialog**

Replace the full contents of `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/launchers/ImagePickerLauncher.kt`:

```kotlin
package com.nerojust.jetimagepicker.launchers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mr0xf00.easycrop.CropResult
import com.mr0xf00.easycrop.ImageCropperDialog
import com.mr0xf00.easycrop.crop
import com.mr0xf00.easycrop.rememberImageCropper
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import com.nerojust.jetimagepicker.config.toRatioOrNull
import com.nerojust.jetimagepicker.model.PermissionState
import com.nerojust.jetimagepicker.utils.Utils.compressImage
import com.nerojust.jetimagepicker.utils.Utils.createImageUri
import com.nerojust.jetimagepicker.utils.Utils.writeBitmapToCache
import kotlinx.coroutines.launch

private val NullableUriSaver =
    Saver<Uri?, String>(
        save = { it?.toString() ?: "" },
        restore = { if (it.isEmpty()) null else Uri.parse(it) },
    )

/**
 * Sets up the gallery and camera activity-result launchers backing [rememberJetImagePickerState].
 * Gallery picking uses Android's Photo Picker contracts and requires no storage permission;
 * camera capture requests `android.Manifest.permission.CAMERA` at runtime. When
 * [JetImagePickerConfig.enableCrop] is true, a crop dialog is shown automatically before
 * compression whenever exactly one image was picked/captured.
 *
 * @return A pair of (launchGallery, launchCamera) functions.
 */
@Composable
fun rememberImagePickerLauncher(
    context: Context,
    config: JetImagePickerConfig = JetImagePickerConfig(),
    onImagesPicked: (List<Uri>) -> Unit,
    onPermissionStateChanged: (PermissionState) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
): Pair<() -> Unit, () -> Unit> {
    val activity =
        context as? Activity
            ?: throw IllegalStateException("Context must be an Activity")

    var tempCameraUri by rememberSaveable(stateSaver = NullableUriSaver) {
        mutableStateOf<Uri?>(null)
    }
    var shouldLaunchCamera by remember { mutableStateOf(false) }
    var hasCameraPermissionBeenRequested by rememberSaveable { mutableStateOf(false) }
    var previousCompressedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    // ponytail: boolean gate, not a mutex - fully serializes pick-and-compress cycles so
    // previousCompressedUris is never touched by two overlapping processPicked calls.
    var isProcessing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val imageCropper = rememberImageCropper()

    // Lock the crop region to the configured aspect ratio the moment a crop starts.
    LaunchedEffect(imageCropper.cropState, config.cropAspectRatio) {
        val state = imageCropper.cropState ?: return@LaunchedEffect
        val ratio = config.cropAspectRatio.toRatioOrNull() ?: return@LaunchedEffect
        val current = state.region
        val targetWidth = minOf(current.width, current.height * ratio)
        val targetHeight = targetWidth / ratio
        val centerX = current.left + current.width / 2f
        val centerY = current.top + current.height / 2f
        state.region =
            Rect(
                left = centerX - targetWidth / 2f,
                top = centerY - targetHeight / 2f,
                right = centerX + targetWidth / 2f,
                bottom = centerY + targetHeight / 2f,
            )
        state.aspectLock = true
    }

    imageCropper.cropState?.let { ImageCropperDialog(state = it) }

    suspend fun processPicked(uris: List<Uri>) {
        if (uris.isEmpty()) {
            onImagesPicked(emptyList())
            return
        }
        isProcessing = true
        try {
            val toCompress =
                if (config.enableCrop && uris.size == 1) {
                    when (val result = imageCropper.crop(uris.first(), context)) {
                        is CropResult.Success -> {
                            val cropped = writeBitmapToCache(context, result.bitmap.asAndroidBitmap())
                            listOfNotNull(cropped)
                        }
                        else -> {
                            // Cancelled or failed - treat like any other cancellation in this library.
                            onImagesPicked(emptyList())
                            return
                        }
                    }
                } else {
                    uris
                }

            if (config.enableCompression) {
                onLoadingChanged(true)
                val compressed = toCompress.mapNotNull { compressImage(context, it, config) }
                onLoadingChanged(false)
                // Compressed files are ours (written to cacheDir via FileProvider) - safe to delete.
                for (uri in previousCompressedUris) {
                    context.contentResolver.delete(uri, null, null)
                }
                previousCompressedUris = compressed
                onImagesPicked(compressed)
            } else {
                onImagesPicked(toCompress)
            }
        } finally {
            isProcessing = false
        }
    }

    // Modern gallery picking: no storage runtime permission required.
    val singlePickMediaLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            Log.d("JetImagePicker", "Gallery picked: $uri")
            coroutineScope.launch { processPicked(listOfNotNull(uri)) }
        }

    val multiPickMediaLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(),
        ) { uris ->
            Log.d("JetImagePicker", "Gallery picked ${uris.size} image(s)")
            coroutineScope.launch { processPicked(uris) }
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(TakePicture()) { success ->
            val capturedUri = tempCameraUri
            if (success && capturedUri != null) {
                coroutineScope.launch {
                    processPicked(listOf(capturedUri))
                    // Compression (if enabled) superseded this raw capture with a new file that's
                    // now tracked in previousCompressedUris - the raw temp file is otherwise never
                    // cleaned up and would leak in cacheDir. Leave it alone when compression is
                    // disabled: capturedUri is then the live selection handed to onImagesPicked.
                    if (config.enableCompression) {
                        context.contentResolver.delete(capturedUri, null, null)
                    }
                }
            } else {
                // Capture was cancelled/failed - clean up the temp file we created for it.
                capturedUri?.let { context.contentResolver.delete(it, null, null) }
                tempCameraUri = null
                onImagesPicked(emptyList())
            }
        }

    fun calculatePermissionState(permission: String): PermissionState {
        val isGranted =
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        val shouldShowRationale =
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        val isPermanentlyDenied = !isGranted && !shouldShowRationale && hasCameraPermissionBeenRequested
        val isDenied = !isGranted && !shouldShowRationale && !hasCameraPermissionBeenRequested
        return PermissionState(
            permission = permission,
            isGranted = isGranted,
            isDenied = isDenied,
            isPermanentlyDenied = isPermanentlyDenied,
            shouldShowRationale = shouldShowRationale,
        )
    }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(RequestPermission()) { granted ->
            val state = calculatePermissionState(Manifest.permission.CAMERA)
            hasCameraPermissionBeenRequested = true
            onPermissionStateChanged(state)
            if (granted) {
                shouldLaunchCamera = true
            }
        }

    // Defer camera launch to avoid re-entry issues
    LaunchedEffect(shouldLaunchCamera) {
        if (shouldLaunchCamera) {
            shouldLaunchCamera = false
            val uri = createImageUri(context)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    val launchGallery = {
        if (!isProcessing) {
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            if (config.allowMultiple) {
                multiPickMediaLauncher.launch(request)
            } else {
                singlePickMediaLauncher.launch(request)
            }
        }
    }

    val launchCamera = {
        if (!isProcessing) {
            val state = calculatePermissionState(Manifest.permission.CAMERA)
            if (state.isGranted) {
                shouldLaunchCamera = true
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    return Pair(launchGallery, launchCamera)
}
```

- [ ] **Step 2: Add `writeBitmapToCache` to `Utils`**

The cropped result comes back as an in-memory `ImageBitmap`, not a `Uri` — it needs writing to a cache file (mirroring how `compressImage` already produces its output) before it can flow into the existing compression step. Add this function to `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/utils/Utils.kt`, inside `object Utils { ... }`, alongside the existing functions (do not change `createImageUri`, `calculateScaledDimensions`, or `compressImage`):

```kotlin
    /**
     * Writes [bitmap] to a new cache file as JPEG and returns its [FileProvider] URI.
     * Used to turn an in-memory cropped bitmap back into a [Uri] the rest of the pipeline expects.
     */
    fun writeBitmapToCache(context: Context, bitmap: Bitmap): Uri? =
        try {
            val file = File(context.cacheDir, "CROP_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, MAX_QUALITY, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
```

Add the constant near the top of `object Utils { ... }` (detekt will otherwise flag the literal as a magic number):

```kotlin
    private const val MAX_QUALITY = 100
```

- [ ] **Step 3: Verify the whole project builds and existing tests still pass**

Run: `./gradlew :JetImagePicker:assembleDebug :app:assembleDebug :JetImagePicker:testDebugUnitTest ktlintCheck detekt`
Expected: BUILD SUCCESSFUL, all unit tests PASS. If `ImageCropperDialog`, `CropState.region`, `CropState.aspectLock`, `ImageCropper.crop(...)`, or `CropResult.Success.bitmap` don't compile as written here, inspect the actual resolved `easycrop` sources (Android Studio "Go to declaration," or the sources jar under the Gradle cache) and adjust the calls to match — the shapes above are confirmed from the library's current public source, not guessed, but pin the exact spelling against what's actually resolved.

- [ ] **Step 4: Commit**

```bash
git add JetImagePicker/src/main/java/com/nerojust/jetimagepicker/launchers/ImagePickerLauncher.kt JetImagePicker/src/main/java/com/nerojust/jetimagepicker/utils/Utils.kt
git commit -m "feat: wire automatic crop step into the picker launcher before compression"
```

---

### Task 5: Demonstrate crop in the example app; update docs

**Files:**
- Modify: `app/src/main/java/com/nerojust/jetimagepicker/example/ImagePickerScreen.kt`
- Modify: `README.md`
- Modify: `CHANGELOG.md`

**Interfaces:** None — consumer-facing usage only.

- [ ] **Step 1: Add a crop toggle to the demo screen**

In `ImagePickerScreen.kt`, add a third toggle alongside the existing "Multiple selection" and "Compression" switches:

```kotlin
var enableCrop by remember { mutableStateOf(false) }
```

Pass it into the config:

```kotlin
config =
    JetImagePickerConfig(
        enableCompression = enableCompression,
        compressionQuality = 70,
        allowMultiple = allowMultiple,
        targetWidth = 1024,
        targetHeight = 1024,
        enableCrop = enableCrop,
        cropAspectRatio = CropAspectRatio.Square,
    ),
```

(add the `com.nerojust.jetimagepicker.config.CropAspectRatio` import), and add a third `Row` with a `Switch` for it, following the exact pattern of the existing two toggles:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(if (enableCrop) "Crop to square on" else "Crop to square off")
    Switch(checked = enableCrop, onCheckedChange = { enableCrop = it })
}
```

- [ ] **Step 2: Verify the app builds and passes static analysis**

Run: `./gradlew :app:assembleDebug ktlintCheck detekt`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Update README**

Add `enableCrop`/`cropAspectRatio` to the "📦 Configuration Options" code block, and add one sentence to the "🧩 Example" section's demo callout mentioning the crop toggle. Add a short "✂️ Cropping" section after "📋 `JetImagePickerState` Reference" documenting `CropAspectRatio`'s three variants.

- [ ] **Step 4: Update CHANGELOG**

Add a new `## [2.0.0] - <today's date>` section at the top (above `## [1.0.1]`) documenting the crop feature under `### Added`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nerojust/jetimagepicker/example/ImagePickerScreen.kt README.md CHANGELOG.md
git commit -m "docs: demonstrate crop in the example app, document in README/CHANGELOG"
```

---

### Task 6: Version bump to 2.0.0

**Files:**
- Modify: `JetImagePicker/build.gradle.kts`

**Interfaces:** None.

- [ ] **Step 1: Bump the version**

In `JetImagePicker/build.gradle.kts`, change both `version = "1.0.0"` (top-level) and `version = "1.0.0"` (inside the `MavenPublication` block) to `"2.0.0"`.

- [ ] **Step 2: Verify**

Run: `./gradlew :JetImagePicker:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add JetImagePicker/build.gradle.kts
git commit -m "chore: bump version to 2.0.0"
```
