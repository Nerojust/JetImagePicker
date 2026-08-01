# JetImagePicker Completion & Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix JetImagePicker's real bugs, migrate gallery picking to Android's Photo Picker, add the missing loading/reset API, and bring it to the tooling/process bar expected of a public Android library (static analysis, KDoc, changelog, contribution guide, CI, dependency automation).

**Architecture:** No structural rewrite — this is a focused bug-fix + modernization pass over the existing small module structure (`config/`, `launchers/`, `model/`, `result/`, `state/`, `ui/`, `utils/`). Pure logic (scaling math, permission→result mapping) gets pulled into small testable functions; Android-framework-coupled code (permission checks, activity result contracts) stays where it is since it can't be unit-tested without Robolectric, which is not being added for this pass.

**Tech Stack:** Kotlin 2.2.0, Jetpack Compose, AndroidX Activity Result APIs (`PickVisualMedia`/`PickMultipleVisualMedia`), Coil, kotlinx.coroutines, JUnit4.

## Global Constraints

- minSdk 21, compileSdk 36, Java/Kotlin toolchain 11 (from `JetImagePicker/build.gradle.kts`) — do not change.
- No new external dependencies except making `kotlinx-coroutines-core` an explicit declared dependency (it is already resolved transitively today; this just stops relying on that implicitly).
- No Robolectric / instrumented tests — unit tests must be plain JUnit with no Android framework calls in the test body itself.
- Public API additions must be additive only: `rememberJetImagePickerState(context, config, onResult)` signature does not change; the `app` example module must keep compiling without modification except manifest permission cleanup (Task 8).
- MIT license and existing `io.github.nerojust` / `jetimagepicker` publishing coordinates in `JetImagePicker/build.gradle.kts` are the single source of truth for distribution — the README must match them, not the other way around.

---

### Task 1: Fix example app's library dependency, remove unedited template stub tests

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Delete: `JetImagePicker/src/test/java/com/nerojust/jetimagepicker/ExampleUnitTest.kt`
- Delete: `JetImagePicker/src/androidTest/java/com/nerojust/jetimagepicker/ExampleInstrumentedTest.kt`
- Delete: `app/src/test/java/com/nerojust/jetimagepicker/ExampleUnitTest.kt`
- Delete: `app/src/androidTest/java/com/nerojust/jetimagepicker/ExampleInstrumentedTest.kt`

**Interfaces:** None — these are build-config and housekeeping changes with no consumers.

**Why the dependency fix matters:** `app/build.gradle.kts` currently has `implementation(project(":JetImagePicker"))` commented out and instead depends on `implementation(libs.jetimagepicker)` — the *published* JitPack artifact (`com.github.nerojust:JetImagePicker:v1`). This means the example app currently builds against a stale published jar, not this repo's own library source. Every later task in this plan verifies its changes by running `./gradlew :app:assembleDebug` — that check is meaningless until the app actually consumes local source.

- [ ] **Step 1: Point the app module at the local library module**

In `app/build.gradle.kts`, replace:

```kotlin
//    implementation(project(":JetImagePicker"))
    implementation(libs.jetimagepicker)
```

with:

```kotlin
    implementation(project(":JetImagePicker"))
```

- [ ] **Step 2: Remove the now-unused catalog entry**

In `gradle/libs.versions.toml`, remove the now-unreferenced version and library entries:

```toml
jetimagepicker = "v1"
```

(from `[versions]`), and:

```toml
jetimagepicker = { module = "com.github.nerojust:JetImagePicker", version.ref = "jetimagepicker" }
```

(from `[libraries]`).

- [ ] **Step 3: Verify the project still builds against local source**

Run: `./gradlew :JetImagePicker:assembleDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit the dependency fix**

```bash
git add app/build.gradle.kts gradle/libs.versions.toml
git commit -m "fix: app example depends on local JetImagePicker module, not stale published artifact"
```

- [ ] **Step 5: Delete the four unedited stub test files and commit**

```bash
git rm JetImagePicker/src/test/java/com/nerojust/jetimagepicker/ExampleUnitTest.kt
git rm JetImagePicker/src/androidTest/java/com/nerojust/jetimagepicker/ExampleInstrumentedTest.kt
git rm app/src/test/java/com/nerojust/jetimagepicker/ExampleUnitTest.kt
git rm app/src/androidTest/java/com/nerojust/jetimagepicker/ExampleInstrumentedTest.kt
git commit -m "test: remove unedited Android Studio template stub tests"
```

---

### Task 2: Aspect-ratio-preserving scale math

**Files:**
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/utils/Utils.kt`
- Test: `JetImagePicker/src/test/java/com/nerojust/jetimagepicker/utils/UtilsTest.kt`

**Interfaces:**
- Produces: `Utils.calculateScaledDimensions(srcWidth: Int, srcHeight: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int>` — pure function, no Android dependencies. Later tasks (Task 5) call this from `compressImage`.

- [ ] **Step 1: Write the failing tests**

Create `JetImagePicker/src/test/java/com/nerojust/jetimagepicker/utils/UtilsTest.kt`:

```kotlin
package com.nerojust.jetimagepicker.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class UtilsTest {

    @Test
    fun `landscape image scales down preserving aspect ratio`() {
        val (width, height) = Utils.calculateScaledDimensions(
            srcWidth = 4000, srcHeight = 2000, maxWidth = 1024, maxHeight = 1024
        )
        assertEquals(1024, width)
        assertEquals(512, height)
    }

    @Test
    fun `portrait image scales down preserving aspect ratio`() {
        val (width, height) = Utils.calculateScaledDimensions(
            srcWidth = 2000, srcHeight = 4000, maxWidth = 1024, maxHeight = 1024
        )
        assertEquals(512, width)
        assertEquals(1024, height)
    }

    @Test
    fun `image smaller than target box is not upscaled`() {
        val (width, height) = Utils.calculateScaledDimensions(
            srcWidth = 500, srcHeight = 300, maxWidth = 1024, maxHeight = 1024
        )
        assertEquals(500, width)
        assertEquals(300, height)
    }

    @Test
    fun `square image fits exactly into square target`() {
        val (width, height) = Utils.calculateScaledDimensions(
            srcWidth = 2048, srcHeight = 2048, maxWidth = 1024, maxHeight = 1024
        )
        assertEquals(1024, width)
        assertEquals(1024, height)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :JetImagePicker:testDebugUnitTest --tests "com.nerojust.jetimagepicker.utils.UtilsTest"`
Expected: FAIL — `calculateScaledDimensions` is unresolved.

- [ ] **Step 3: Add `calculateScaledDimensions` to `Utils`**

In `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/utils/Utils.kt`, add this function inside `object Utils { ... }` (alongside the existing `createImageUri`/`compressImage`):

```kotlin
    /**
     * Computes the largest width/height that fits within [maxWidth]x[maxHeight]
     * while preserving the source aspect ratio. Never upscales.
     */
    fun calculateScaledDimensions(
        srcWidth: Int,
        srcHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Pair<Int, Int> {
        if (srcWidth <= 0 || srcHeight <= 0) return srcWidth to srcHeight

        val widthRatio = maxWidth.toFloat() / srcWidth
        val heightRatio = maxHeight.toFloat() / srcHeight
        val scale = minOf(widthRatio, heightRatio, 1f)

        val newWidth = (srcWidth * scale).toInt().coerceAtLeast(1)
        val newHeight = (srcHeight * scale).toInt().coerceAtLeast(1)
        return newWidth to newHeight
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :JetImagePicker:testDebugUnitTest --tests "com.nerojust.jetimagepicker.utils.UtilsTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add JetImagePicker/src/main/java/com/nerojust/jetimagepicker/utils/Utils.kt JetImagePicker/src/test/java/com/nerojust/jetimagepicker/utils/UtilsTest.kt
git commit -m "fix: add aspect-ratio-preserving scale calculation"
```

---

### Task 3: Permission→Result mapping + relocate `ImagePickerResult`

**Files:**
- Delete: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/model/ImagePickerResult.kt`
- Create: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/result/ImagePickerResult.kt`
- Create: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/result/PermissionResultMapper.kt`
- Test: `JetImagePicker/src/test/java/com/nerojust/jetimagepicker/result/PermissionResultMapperTest.kt`

**Interfaces:**
- Consumes: `PermissionState` from `com.nerojust.jetimagepicker.model.PermissionState` (existing, unchanged — fields `permission: String`, `isGranted: Boolean`, `isDenied: Boolean`, `isPermanentlyDenied: Boolean`, `shouldShowRationale: Boolean`).
- Produces: `PermissionState.toImagePickerResult(): ImagePickerResult?` extension function — `null` means "no result to emit" (i.e. permission is granted). Task 6 wires this into `rememberJetImagePickerState`.

- [ ] **Step 1: Move `ImagePickerResult.kt` to the `result/` directory**

Its package declaration is already `com.nerojust.jetimagepicker.result` (it was just misplaced under `model/`), so no import elsewhere needs to change.

```bash
mkdir -p JetImagePicker/src/main/java/com/nerojust/jetimagepicker/result
git mv JetImagePicker/src/main/java/com/nerojust/jetimagepicker/model/ImagePickerResult.kt JetImagePicker/src/main/java/com/nerojust/jetimagepicker/result/ImagePickerResult.kt
```

- [ ] **Step 2: Verify the project still builds**

Run: `./gradlew :JetImagePicker:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Write the failing tests for the mapping function**

Create `JetImagePicker/src/test/java/com/nerojust/jetimagepicker/result/PermissionResultMapperTest.kt`:

```kotlin
package com.nerojust.jetimagepicker.result

import com.nerojust.jetimagepicker.model.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionResultMapperTest {

    @Test
    fun `granted permission produces no result`() {
        val state = PermissionState(
            permission = "android.permission.CAMERA",
            isGranted = true,
            isDenied = false,
            isPermanentlyDenied = false,
            shouldShowRationale = false
        )
        assertNull(state.toImagePickerResult())
    }

    @Test
    fun `permanently denied maps to PermissionPermanentlyDenied`() {
        val state = PermissionState(
            permission = "android.permission.CAMERA",
            isGranted = false,
            isDenied = true,
            isPermanentlyDenied = true,
            shouldShowRationale = false
        )
        val result = state.toImagePickerResult()
        assertEquals(ImagePickerResult.PermissionPermanentlyDenied("android.permission.CAMERA"), result)
    }

    @Test
    fun `should-show-rationale maps to ShowRationale`() {
        val state = PermissionState(
            permission = "android.permission.CAMERA",
            isGranted = false,
            isDenied = false,
            isPermanentlyDenied = false,
            shouldShowRationale = true
        )
        val result = state.toImagePickerResult()
        assertEquals(ImagePickerResult.ShowRationale("android.permission.CAMERA"), result)
    }

    @Test
    fun `plain denied maps to PermissionDenied`() {
        val state = PermissionState(
            permission = "android.permission.CAMERA",
            isGranted = false,
            isDenied = true,
            isPermanentlyDenied = false,
            shouldShowRationale = false
        )
        val result = state.toImagePickerResult()
        assertEquals(ImagePickerResult.PermissionDenied("android.permission.CAMERA"), result)
    }
}
```

Note: `ImagePickerResult.PermissionPermanentlyDenied`, `ShowRationale`, and `PermissionDenied` are `data class`es (already defined), so structural equality via `assertEquals` works without a custom equals.

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :JetImagePicker:testDebugUnitTest --tests "com.nerojust.jetimagepicker.result.PermissionResultMapperTest"`
Expected: FAIL — `toImagePickerResult` is unresolved.

- [ ] **Step 5: Implement the mapping function**

Create `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/result/PermissionResultMapper.kt`:

```kotlin
package com.nerojust.jetimagepicker.result

import com.nerojust.jetimagepicker.model.PermissionState

/**
 * Maps a [PermissionState] to the [ImagePickerResult] that should be surfaced
 * to the caller, or `null` if the permission is granted and there is nothing to report.
 */
fun PermissionState.toImagePickerResult(): ImagePickerResult? = when {
    isGranted -> null
    isPermanentlyDenied -> ImagePickerResult.PermissionPermanentlyDenied(permission)
    shouldShowRationale -> ImagePickerResult.ShowRationale(permission)
    isDenied -> ImagePickerResult.PermissionDenied(permission)
    else -> null
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :JetImagePicker:testDebugUnitTest --tests "com.nerojust.jetimagepicker.result.PermissionResultMapperTest"`
Expected: PASS (4 tests)

- [ ] **Step 7: Commit**

```bash
git add JetImagePicker/src/main/java/com/nerojust/jetimagepicker/result/ JetImagePicker/src/test/java/com/nerojust/jetimagepicker/result/
git commit -m "refactor: relocate ImagePickerResult and extract permission-to-result mapping"
```

---

### Task 4: `JetImagePickerConfig` defaults test

**Files:**
- Test: `JetImagePicker/src/test/java/com/nerojust/jetimagepicker/config/JetImagePickerConfigTest.kt`

**Interfaces:**
- Consumes: `JetImagePickerConfig` (existing, unchanged — `enableCompression: Boolean = true`, `compressionQuality: Int = 75`, `targetWidth: Int? = null`, `targetHeight: Int? = null`, `allowMultiple: Boolean = true`).

- [ ] **Step 1: Write the test**

Create `JetImagePicker/src/test/java/com/nerojust/jetimagepicker/config/JetImagePickerConfigTest.kt`:

```kotlin
package com.nerojust.jetimagepicker.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JetImagePickerConfigTest {

    @Test
    fun `default config values`() {
        val config = JetImagePickerConfig()

        assertTrue(config.enableCompression)
        assertEquals(75, config.compressionQuality)
        assertNull(config.targetWidth)
        assertNull(config.targetHeight)
        assertTrue(config.allowMultiple)
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :JetImagePicker:testDebugUnitTest --tests "com.nerojust.jetimagepicker.config.JetImagePickerConfigTest"`
Expected: PASS (this test should pass immediately since it only documents existing behavior — if it fails, `JetImagePickerConfig`'s defaults have drifted and must be fixed to match the documented API, not the other way around)

- [ ] **Step 3: Commit**

```bash
git add JetImagePicker/src/test/java/com/nerojust/jetimagepicker/config/JetImagePickerConfigTest.kt
git commit -m "test: lock in JetImagePickerConfig default values"
```

---

### Task 5: Fix `compressImage` — FileProvider URI, off-main-thread, aspect ratio

**Files:**
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/utils/Utils.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `JetImagePicker/build.gradle.kts`

**Interfaces:**
- Consumes: `Utils.calculateScaledDimensions` (Task 2).
- Produces: `suspend fun Utils.compressImage(context: Context, uri: Uri, config: JetImagePickerConfig): Uri?` — now a suspend function (was a plain function). Task 7's launcher calls this from a coroutine.

- [ ] **Step 1: Declare `kotlinx-coroutines-core` explicitly**

It is already on the classpath transitively (via `androidx.lifecycle:lifecycle-runtime-ktx`), but the library now uses its APIs directly (`withContext`, `Dispatchers.IO`), so it must be an explicit dependency rather than an implicit transitive one.

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
kotlinxCoroutines = "1.7.3"
```

Add to `[libraries]`:

```toml
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
```

In `JetImagePicker/build.gradle.kts`, add to the `dependencies { ... }` block:

```kotlin
    implementation(libs.kotlinx.coroutines.core)
```

- [ ] **Step 2: Rewrite `compressImage` in `Utils.kt`**

Replace the existing `compressImage` function (and add the needed imports) in `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/utils/Utils.kt`:

```kotlin
package com.nerojust.jetimagepicker.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Utils {

    fun createImageUri(context: Context): Uri {
        val file = File(
            context.cacheDir,
            "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
        )
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    fun calculateScaledDimensions(
        srcWidth: Int,
        srcHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Pair<Int, Int> {
        if (srcWidth <= 0 || srcHeight <= 0) return srcWidth to srcHeight

        val widthRatio = maxWidth.toFloat() / srcWidth
        val heightRatio = maxHeight.toFloat() / srcHeight
        val scale = minOf(widthRatio, heightRatio, 1f)

        val newWidth = (srcWidth * scale).toInt().coerceAtLeast(1)
        val newHeight = (srcHeight * scale).toInt().coerceAtLeast(1)
        return newWidth to newHeight
    }

    /**
     * Compresses (and optionally resizes) the image at [uri], writing the result
     * to a cache file exposed via [FileProvider]. Runs on [Dispatchers.IO].
     */
    suspend fun compressImage(
        context: Context,
        uri: Uri,
        config: JetImagePickerConfig
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }

            val targetWidth = config.targetWidth
            val targetHeight = config.targetHeight
            val resized = if (targetWidth != null && targetHeight != null) {
                val (scaledWidth, scaledHeight) = calculateScaledDimensions(
                    bitmap.width, bitmap.height, targetWidth, targetHeight
                )
                bitmap.scale(scaledWidth, scaledHeight)
            } else {
                bitmap
            }

            val file = File(context.cacheDir, "COMP_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use {
                resized.compress(Bitmap.CompressFormat.JPEG, config.compressionQuality, it)
            }

            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
```

- [ ] **Step 3: Run all existing `Utils` tests to make sure nothing broke**

Run: `./gradlew :JetImagePicker:testDebugUnitTest --tests "com.nerojust.jetimagepicker.utils.UtilsTest"`
Expected: PASS (4 tests, unchanged from Task 2)

- [ ] **Step 4: Verify the module still builds**

Run: `./gradlew :JetImagePicker:assembleDebug`
Expected: BUILD SUCCESSFUL (this will fail to compile if any caller of `compressImage` wasn't updated for the new `suspend` signature — that caller is fixed in Task 7; if Task 7 hasn't run yet, expect a compile error here naming `ImagePickerLauncher.kt`, which is fine to leave until Task 7 in the same working session)

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml JetImagePicker/build.gradle.kts JetImagePicker/src/main/java/com/nerojust/jetimagepicker/utils/Utils.kt
git commit -m "fix: compressImage returns FileProvider URI, runs off main thread, preserves aspect ratio"
```

---

### Task 6: State layer — `rememberSaveable`, `isLoading`, `clearSelection`

**Files:**
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/state/JetImagePickerState.kt`
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/state/rememberJetImagePickerState.kt`

**Interfaces:**
- Consumes: `PermissionState.toImagePickerResult()` (Task 3); `rememberImagePickerLauncher(context, config, onImagesPicked, onPermissionStateChanged, onLoadingChanged)` — the `onLoadingChanged: (Boolean) -> Unit` parameter is new and implemented in Task 7, but this task defines the call site expecting it.
- Produces: `JetImagePickerState` now exposes `isLoading: Boolean` and `clearSelection: () -> Unit` in addition to the existing `selectedImageUris`, `selectedImageUri`, `pickFromGallery`, `captureWithCamera`.

- [ ] **Step 1: Update `JetImagePickerState`**

Replace the contents of `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/state/JetImagePickerState.kt`:

```kotlin
package com.nerojust.jetimagepicker.state

import android.net.Uri
import androidx.compose.runtime.State

class JetImagePickerState internal constructor(
    private val _selectedImageUris: State<List<Uri>>,
    private val _selectedImageUri: State<Uri?>,
    private val _isLoading: State<Boolean>,
    val pickFromGallery: () -> Unit,
    val captureWithCamera: () -> Unit,
    val clearSelection: () -> Unit
) {
    val selectedImageUris: List<Uri> get() = _selectedImageUris.value
    val selectedImageUri: Uri? get() = _selectedImageUri.value
    val isLoading: Boolean get() = _isLoading.value
}
```

- [ ] **Step 2: Update `rememberJetImagePickerState`**

Replace the contents of `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/state/rememberJetImagePickerState.kt`:

```kotlin
package com.nerojust.jetimagepicker.state

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import com.nerojust.jetimagepicker.launchers.rememberImagePickerLauncher
import com.nerojust.jetimagepicker.result.ImagePickerResult
import com.nerojust.jetimagepicker.result.toImagePickerResult

private val UriListSaver = listSaver<List<Uri>, String>(
    save = { list -> list.map(Uri::toString) },
    restore = { saved -> saved.map(Uri::parse) }
)

@Composable
fun rememberJetImagePickerState(
    context: Context,
    config: JetImagePickerConfig = JetImagePickerConfig(),
    onResult: (ImagePickerResult) -> Unit
): JetImagePickerState {
    var selectedUris by rememberSaveable(stateSaver = UriListSaver) {
        mutableStateOf(emptyList())
    }
    var isLoading by remember { mutableStateOf(false) }

    val selectedImageUris = remember { derivedStateOf { selectedUris } }
    val selectedImageUri = remember { derivedStateOf { selectedUris.firstOrNull() } }
    val loadingState = remember { derivedStateOf { isLoading } }

    val (launchGallery, launchCamera) = rememberImagePickerLauncher(
        context = context,
        config = config,
        onImagesPicked = { uris ->
            selectedUris = uris
            onResult(ImagePickerResult.Success(uris))
        },
        onPermissionStateChanged = { permissionState ->
            permissionState.toImagePickerResult()?.let(onResult)
        },
        onLoadingChanged = { loading -> isLoading = loading }
    )

    return JetImagePickerState(
        _selectedImageUris = selectedImageUris,
        _selectedImageUri = selectedImageUri,
        _isLoading = loadingState,
        pickFromGallery = launchGallery,
        captureWithCamera = launchCamera,
        clearSelection = { selectedUris = emptyList() }
    )
}
```

- [ ] **Step 3: Verify compile status**

Run: `./gradlew :JetImagePicker:compileDebugKotlin`
Expected: FAILS at this point, naming `rememberImagePickerLauncher` — it does not yet accept `onLoadingChanged`. This is expected; Task 7 updates the launcher to match. Confirm the *only* compile error is in `rememberImagePickerLauncher`'s call signature (not a typo elsewhere) before moving on.

- [ ] **Step 4: Commit**

```bash
git add JetImagePicker/src/main/java/com/nerojust/jetimagepicker/state/
git commit -m "feat: add isLoading and clearSelection to JetImagePickerState, persist selection across process death"
```

---

### Task 7: Launcher — Photo Picker migration, camera URI persistence, coroutine compression

**Files:**
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/launchers/ImagePickerLauncher.kt`
- Delete: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/config/PickImagesContract.kt`

**Interfaces:**
- Consumes: `Utils.compressImage` (suspend, Task 5), `Utils.createImageUri` (existing, unchanged).
- Produces: `rememberImagePickerLauncher(context: Context, config: JetImagePickerConfig, onImagesPicked: (List<Uri>) -> Unit, onPermissionStateChanged: (PermissionState) -> Unit, onLoadingChanged: (Boolean) -> Unit): Pair<() -> Unit, () -> Unit>` — the `onLoadingChanged` parameter is new; everything else in the return shape (`Pair` of gallery/camera launch functions) is unchanged, satisfying Task 6's call site.

This task also cleans up stale cache files: FileProvider URIs support `ContentResolver.delete()` directly (it deletes the backing file), so no manual path parsing is needed. Only URIs the library itself created (compressed output, camera capture temp file) are ever deleted this way — never a raw gallery/MediaStore URI.

- [ ] **Step 1: Delete the now-unused custom gallery contract**

```bash
git rm JetImagePicker/src/main/java/com/nerojust/jetimagepicker/config/PickImagesContract.kt
```

- [ ] **Step 2: Rewrite `ImagePickerLauncher.kt`**

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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import com.nerojust.jetimagepicker.model.PermissionState
import com.nerojust.jetimagepicker.utils.Utils.compressImage
import com.nerojust.jetimagepicker.utils.Utils.createImageUri
import kotlinx.coroutines.launch

private val NullableUriSaver = Saver<Uri?, String>(
    save = { it?.toString() ?: "" },
    restore = { if (it.isEmpty()) null else Uri.parse(it) }
)

@Composable
fun rememberImagePickerLauncher(
    context: Context,
    config: JetImagePickerConfig = JetImagePickerConfig(),
    onImagesPicked: (List<Uri>) -> Unit,
    onPermissionStateChanged: (PermissionState) -> Unit,
    onLoadingChanged: (Boolean) -> Unit
): Pair<() -> Unit, () -> Unit> {

    val activity = context as? Activity
        ?: throw IllegalStateException("Context must be an Activity")

    var tempCameraUri by rememberSaveable(stateSaver = NullableUriSaver) {
        mutableStateOf<Uri?>(null)
    }
    var shouldLaunchCamera by remember { mutableStateOf(false) }
    var previousCompressedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val coroutineScope = rememberCoroutineScope()

    suspend fun processPicked(uris: List<Uri>) {
        if (uris.isEmpty()) {
            onImagesPicked(emptyList())
            return
        }
        if (config.enableCompression) {
            onLoadingChanged(true)
            val compressed = uris.mapNotNull { compressImage(context, it, config) }
            onLoadingChanged(false)
            // Compressed files are ours (written to cacheDir via FileProvider) — safe to delete.
            previousCompressedUris.forEach { context.contentResolver.delete(it, null, null) }
            previousCompressedUris = compressed
            onImagesPicked(compressed)
        } else {
            onImagesPicked(uris)
        }
    }

    // Modern gallery picking: no storage runtime permission required.
    val singlePickMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        Log.d("JetImagePicker", "Gallery picked: $uri")
        coroutineScope.launch { processPicked(listOfNotNull(uri)) }
    }

    val multiPickMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        Log.d("JetImagePicker", "Gallery picked ${uris.size} image(s)")
        coroutineScope.launch { processPicked(uris) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        val capturedUri = tempCameraUri
        if (success && capturedUri != null) {
            coroutineScope.launch { processPicked(listOf(capturedUri)) }
        } else {
            // Capture was cancelled/failed — clean up the temp file we created for it.
            capturedUri?.let { context.contentResolver.delete(it, null, null) }
            tempCameraUri = null
            onImagesPicked(emptyList())
        }
    }

    fun calculatePermissionState(permission: String): PermissionState {
        val isGranted = ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        val shouldShowRationale =
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        val isDenied = !isGranted && !shouldShowRationale
        val isPermanentlyDenied = !isGranted && isDenied
        return PermissionState(
            permission = permission,
            isGranted = isGranted,
            isDenied = isDenied,
            isPermanentlyDenied = isPermanentlyDenied,
            shouldShowRationale = shouldShowRationale
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        val state = calculatePermissionState(Manifest.permission.CAMERA)
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
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (config.allowMultiple) {
            multiPickMediaLauncher.launch(request)
        } else {
            singlePickMediaLauncher.launch(request)
        }
    }

    val launchCamera = {
        val state = calculatePermissionState(Manifest.permission.CAMERA)
        if (state.isGranted) {
            shouldLaunchCamera = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    return Pair(launchGallery, launchCamera)
}
```

- [ ] **Step 3: Verify the whole library and example app compile**

Run: `./gradlew :JetImagePicker:assembleDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run the full unit test suite**

Run: `./gradlew :JetImagePicker:testDebugUnitTest`
Expected: PASS (all tests from Tasks 2–4)

- [ ] **Step 5: Commit**

```bash
git add JetImagePicker/src/main/java/com/nerojust/jetimagepicker/launchers/ImagePickerLauncher.kt
git commit -m "feat: migrate gallery picking to Photo Picker, persist camera capture URI across process death"
```

---

### Task 8: Manifest cleanup — drop now-unnecessary storage permissions

**Files:**
- Modify: `JetImagePicker/src/main/AndroidManifest.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:** None — manifest-only change.

- [ ] **Step 1: Update the library manifest**

`JetImagePicker/src/main/AndroidManifest.xml` currently declares three permissions; gallery picking via Photo Picker (Task 7) needs none of the storage ones. Replace its contents:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.CAMERA" />
</manifest>
```

- [ ] **Step 2: Update the example app manifest**

In `app/src/main/AndroidManifest.xml`, remove the two now-unnecessary lines:

```xml
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

leaving only:

```xml
    <uses-permission android:name="android.permission.CAMERA" />
```

(alongside the existing `<uses-feature android:name="android.hardware.camera" android:required="false" />`, which is unaffected).

- [ ] **Step 3: Verify the app still builds and installs**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add JetImagePicker/src/main/AndroidManifest.xml app/src/main/AndroidManifest.xml
git commit -m "chore: drop storage permissions no longer needed after Photo Picker migration"
```

---

### Task 9: Content descriptions for preview composables

**Files:**
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/ui/ImagePreview.kt`
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/ui/MultiImagePreview.kt`

**Interfaces:**
- Produces: `ImagePreview(uri: Uri, modifier: Modifier = Modifier, contentDescription: String? = "Selected image")`; `MultiImagePreview(imageUris: List<Uri>, modifier: Modifier = Modifier, contentDescription: String? = "Selected image")` — both additive (new param has a default), non-breaking for existing callers.

- [ ] **Step 1: Update `ImagePreview`**

```kotlin
// File: ui/ImagePreview.kt
package com.nerojust.jetimagepicker.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

@Composable
fun ImagePreview(
    uri: Uri,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Selected image"
) {
    val painter = rememberAsyncImagePainter(model = uri)

    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(4.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, Color.LightGray, MaterialTheme.shapes.medium)
    )
}
```

- [ ] **Step 2: Update `MultiImagePreview`**

```kotlin
// File: ui/MultiImagePreview.kt
package com.nerojust.jetimagepicker.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

@Composable
fun MultiImagePreview(
    imageUris: List<Uri>,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Selected image"
) {
    LazyRow(modifier = modifier.fillMaxWidth()) {
        items(imageUris) { uri ->
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = contentDescription,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(120.dp)
            )
        }
    }
}
```

- [ ] **Step 3: Verify the module builds**

Run: `./gradlew :JetImagePicker:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add JetImagePicker/src/main/java/com/nerojust/jetimagepicker/ui/
git commit -m "fix: expose real contentDescription on preview composables instead of hardcoded/null"
```

---

### Task 10: README rewrite — one distribution story, correct usage example

**Files:**
- Modify: `README.md`

**Interfaces:** None — documentation only.

- [ ] **Step 1: Replace the Setup section**

Replace the "🛠️ Setup" section (currently offering both a JitPack `com.github.nerojust:JetImagePicker:v1` coordinate and a project-module include) with the single coordinate that matches `JetImagePicker/build.gradle.kts`'s actual publishing config (`io.github.nerojust` / Maven Central):

```markdown
## 🛠️ Setup

**Add the dependency:**

```kotlin
dependencies {
    implementation("io.github.nerojust:jetimagepicker:1.0.0")
}
```

Or, if you're working inside this repo as a module:

```kotlin
implementation(project(":JetImagePicker"))
```

No extra repository declaration is needed — the artifact is published to Maven Central.
```

- [ ] **Step 2: Update the manifest/permissions instructions**

Replace the current manifest snippet (which lists `CAMERA`, `READ_EXTERNAL_STORAGE`, and `READ_MEDIA_IMAGES`) with:

```markdown
### 1. Configure your `AndroidManifest.xml`

Gallery picking uses Android's Photo Picker and needs **no storage permission**. Only camera capture needs a runtime permission:

```xml
<uses-permission android:name="android.permission.CAMERA" />

<application>
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.provider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />
    </provider>
</application>
```
```

- [ ] **Step 3: Fix the usage example's imports**

In the "🧩 Example" section's code block, correct the import (it currently omits it / implies the wrong package). Ensure the example includes:

```kotlin
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import com.nerojust.jetimagepicker.result.ImagePickerResult
import com.nerojust.jetimagepicker.state.rememberJetImagePickerState
import com.nerojust.jetimagepicker.ui.ImagePreview
import com.nerojust.jetimagepicker.ui.MultiImagePreview
```

- [ ] **Step 4: Document the new `isLoading` / `clearSelection` API**

Add a short subsection after "📤 Result Handling":

```markdown
## ⏳ Loading State & Reset

`pickerState.isLoading` is `true` while picked images are being compressed — use it to show a progress indicator:

```kotlin
if (pickerState.isLoading) {
    CircularProgressIndicator()
}
```

Call `pickerState.clearSelection()` to reset the current selection back to empty.
```

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: fix README to match actual code, permissions, and publishing coordinates"
```

---

### Task 11: GitHub Actions CI

**Files:**
- Create: `.github/workflows/android-ci.yml`

**Interfaces:** None.

- [ ] **Step 1: Create the workflow file**

```yaml
name: Android CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Lint
        run: ./gradlew lint

      - name: Unit tests
        run: ./gradlew testDebugUnitTest

      - name: Assemble
        run: ./gradlew assembleDebug
```

- [ ] **Step 2: Verify it locally**

Run the same commands the workflow runs, in order, to confirm they all succeed before pushing:

```bash
./gradlew lint
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Expected: all three succeed.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/android-ci.yml
git commit -m "ci: add GitHub Actions workflow for lint, unit tests, and assemble"
```

---

### Task 12: ktlint + detekt static analysis, wired into CI

**Files:**
- Modify: `build.gradle.kts` (root)
- Modify: `gradle/libs.versions.toml`
- Modify: `JetImagePicker/build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/android-ci.yml`
- Create: `JetImagePicker/detekt-baseline.xml` (generated, not hand-written)
- Create: `app/detekt-baseline.xml` (generated, not hand-written)
- Any file reformatted by `ktlintFormat` (formatting-only changes)

**Interfaces:** None — build tooling only, no source-level API changes.

- [ ] **Step 1: Add the plugin versions and aliases to the catalog**

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
ktlint = "12.1.1"
detekt = "1.23.6"
```

Add to `[plugins]`:

```toml
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

- [ ] **Step 2: Apply both plugins at the root**

In `build.gradle.kts` (root), add to the `plugins { }` block:

```kotlin
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
```

- [ ] **Step 3: Apply and configure both plugins in `JetImagePicker/build.gradle.kts`**

Add to its `plugins { }` block:

```kotlin
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
```

Add this block after the `dependencies { }` block:

```kotlin
detekt {
    buildUponDefaultConfig = true
    baseline = file("detekt-baseline.xml")
}
```

- [ ] **Step 4: Apply and configure both plugins in `app/build.gradle.kts`**

Add to its `plugins { }` block:

```kotlin
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
```

Add this block after the `dependencies { }` block:

```kotlin
detekt {
    buildUponDefaultConfig = true
    baseline = file("detekt-baseline.xml")
}
```

- [ ] **Step 5: Auto-format the existing codebase**

Run: `./gradlew ktlintFormat`
Expected: completes successfully; any changed files are formatting-only (whitespace, import order) — confirm with `git diff --stat` that no logic changed.

- [ ] **Step 6: Verify formatting is now clean**

Run: `./gradlew ktlintCheck`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Generate detekt baselines to grandfather existing findings**

This is the standard way to adopt detekt on a codebase that predates it: the baseline captures every current finding so only *new* issues fail CI going forward.

Run: `./gradlew :JetImagePicker:detektBaseline :app:detektBaseline`
Expected: BUILD SUCCESSFUL; creates `JetImagePicker/detekt-baseline.xml` and `app/detekt-baseline.xml`.

- [ ] **Step 8: Verify detekt now passes clean**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Add static analysis steps to CI**

In `.github/workflows/android-ci.yml`, insert these steps after "Lint" and before "Unit tests":

```yaml
      - name: Ktlint
        run: ./gradlew ktlintCheck

      - name: Detekt
        run: ./gradlew detekt
```

- [ ] **Step 10: Commit**

```bash
git add build.gradle.kts gradle/libs.versions.toml JetImagePicker/build.gradle.kts app/build.gradle.kts .github/workflows/android-ci.yml JetImagePicker/detekt-baseline.xml app/detekt-baseline.xml
git add -u
git commit -m "chore: add ktlint and detekt static analysis, wired into CI"
```

---

### Task 13: KDoc on the public API

**Files:**
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/config/JetImagePickerConfig.kt`
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/model/PermissionState.kt`
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/result/ImagePickerResult.kt`
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/state/JetImagePickerState.kt`
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/state/rememberJetImagePickerState.kt`
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/launchers/ImagePickerLauncher.kt`
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/ui/ImagePreview.kt`
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/ui/MultiImagePreview.kt`
- Modify: `JetImagePicker/src/main/java/com/nerojust/jetimagepicker/utils/Utils.kt`

**Interfaces:** None — doc comments only, no signature changes. This task must run after Tasks 1-9 since it documents the signatures those tasks produce; `PermissionResultMapper.kt` (Task 3) and `Utils.calculateScaledDimensions`/`compressImage` (Tasks 2, 5) already carry KDoc from when they were written and need no changes here.

- [ ] **Step 1: `JetImagePickerConfig.kt`**

Add directly above `data class JetImagePickerConfig(`:

```kotlin
/**
 * Configuration for [rememberJetImagePickerState] and [rememberImagePickerLauncher].
 *
 * @property enableCompression If true, picked/captured images are compressed (and optionally resized) before the result callback fires.
 * @property compressionQuality JPEG quality (0-100) used when [enableCompression] is true.
 * @property targetWidth Optional max width (px) to fit picked images into, preserving aspect ratio. Requires [targetHeight] to also be set.
 * @property targetHeight Optional max height (px) to fit picked images into, preserving aspect ratio. Requires [targetWidth] to also be set.
 * @property allowMultiple If true, the gallery picker allows selecting more than one image.
 */
```

- [ ] **Step 2: `PermissionState.kt`**

Add directly above `data class PermissionState(`:

```kotlin
/**
 * Snapshot of a single runtime permission's state, computed after a permission request.
 *
 * @property permission The Android permission string this state describes (e.g. `android.Manifest.permission.CAMERA`).
 * @property isGranted True if the permission is currently granted.
 * @property isDenied True if the permission was denied this round (may still be re-requestable).
 * @property isPermanentlyDenied True if the user denied the permission with "Don't ask again", or the system otherwise stopped prompting.
 * @property shouldShowRationale True if the OS recommends showing a rationale before requesting again.
 */
```

- [ ] **Step 3: `ImagePickerResult.kt`**

Add directly above `sealed class ImagePickerResult {`:

```kotlin
/**
 * Result of a pick/capture operation, delivered via [rememberJetImagePickerState]'s `onResult` callback.
 */
```

Add directly above each nested data class:

```kotlin
    /** One or more images were successfully picked or captured. */
    data class Success(val uris: List<Uri>) : ImagePickerResult()

    /** [permission] was denied for this request; it can still be requested again. */
    data class PermissionDenied(val permission: String) : ImagePickerResult()

    /** [permission] was permanently denied ("Don't ask again"); direct the user to app settings. */
    data class PermissionPermanentlyDenied(val permission: String) : ImagePickerResult()

    /** The OS recommends showing a rationale for [permission] before requesting it again. */
    data class ShowRationale(val permission: String) : ImagePickerResult()
```

- [ ] **Step 4: `JetImagePickerState.kt`**

Add directly above `class JetImagePickerState internal constructor(`:

```kotlin
/**
 * Observable state for a single image picker instance, returned by [rememberJetImagePickerState].
 *
 * @property selectedImageUris All currently selected/captured image URIs.
 * @property selectedImageUri The first URI in [selectedImageUris], or null if nothing is selected.
 * @property isLoading True while picked images are being compressed.
 * @property pickFromGallery Launches the gallery picker (Photo Picker on supported devices).
 * @property captureWithCamera Launches the system camera to capture a new photo.
 * @property clearSelection Resets [selectedImageUris]/[selectedImageUri] to empty.
 */
```

- [ ] **Step 5: `rememberJetImagePickerState.kt`**

Add directly above `fun rememberJetImagePickerState(`:

```kotlin
/**
 * Creates and remembers a [JetImagePickerState] for picking images from the gallery or capturing
 * one with the camera, handling runtime permissions and optional compression.
 *
 * @param context Must be (or wrap) an [android.app.Activity].
 * @param config Picker behavior — compression, resizing, single vs. multiple selection.
 * @param onResult Invoked with an [ImagePickerResult] on every pick, capture, or permission event.
 */
```

- [ ] **Step 6: `ImagePickerLauncher.kt`**

Add directly above `fun rememberImagePickerLauncher(`:

```kotlin
/**
 * Sets up the gallery and camera activity-result launchers backing [rememberJetImagePickerState].
 * Gallery picking uses Android's Photo Picker contracts and requires no storage permission;
 * camera capture requests `android.Manifest.permission.CAMERA` at runtime.
 *
 * @return A pair of (launchGallery, launchCamera) functions.
 */
```

- [ ] **Step 7: `ImagePreview.kt`**

Add directly above `fun ImagePreview(`:

```kotlin
/**
 * Displays a single selected image at a fixed 250dp height, cropped to fill the width.
 */
```

- [ ] **Step 8: `MultiImagePreview.kt`**

Add directly above `fun MultiImagePreview(`:

```kotlin
/**
 * Displays a horizontally scrolling row of selected images, each 120dp square.
 */
```

- [ ] **Step 9: `Utils.kt`**

Add directly above `object Utils {`:

```kotlin
/**
 * Internal file/image helpers backing [rememberImagePickerLauncher]. Not intended for direct use by consumers.
 */
```

Add directly above `fun createImageUri(context: Context): Uri {`:

```kotlin
    /** Creates a new empty cache file for a camera capture and returns its [FileProvider] URI. */
```

- [ ] **Step 10: Verify the module still builds and tests still pass**

Run: `./gradlew :JetImagePicker:assembleDebug :JetImagePicker:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, all unit tests PASS

- [ ] **Step 11: Commit**

```bash
git add JetImagePicker/src/main/java/com/nerojust/jetimagepicker/
git commit -m "docs: add KDoc to the public API"
```

---

### Task 14: CHANGELOG.md

**Files:**
- Create: `CHANGELOG.md`

**Interfaces:** None — documentation only.

- [ ] **Step 1: Create the changelog**

```markdown
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
```

- [ ] **Step 2: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs: add CHANGELOG for the 1.0.0 release"
```

---

### Task 15: CONTRIBUTING.md

**Files:**
- Create: `CONTRIBUTING.md`

**Interfaces:** None — documentation only.

- [ ] **Step 1: Create the contribution guide**

```markdown
# Contributing to JetImagePicker

Thanks for considering a contribution!

## Getting started

1. Fork and clone the repo.
2. Open in Android Studio (or run `./gradlew build` from the CLI) — requires JDK 17 and the Android SDK (compileSdk 36).
3. The `app` module is a runnable example that depends on the `JetImagePicker` module's local source (`implementation(project(":JetImagePicker"))`), so changes to the library are immediately reflected when you run `app`.

## Before opening a PR

Run the full local check suite — this is exactly what CI runs:

```bash
./gradlew ktlintCheck
./gradlew detekt
./gradlew lint
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

- Formatting is enforced by [ktlint](https://github.com/pinterest/ktlint) — run `./gradlew ktlintFormat` to auto-fix.
- Static analysis is enforced by [detekt](https://detekt.dev/). New findings must be fixed, not baselined — the baseline file only grandfathers pre-existing issues.
- New logic (a branch, a loop, a parser, anything touching permissions or file I/O) needs a covering unit test.

## Commit / PR conventions

- Keep commits focused — one logical change per commit.
- Write commit messages in the imperative mood (`fix: ...`, `feat: ...`, `docs: ...`) describing *why*, not just *what*.
- Reference the issue number in the PR description if one exists.

## Reporting bugs

Open a GitHub issue with: the `JetImagePickerConfig` you used, the Android version/device, and (if possible) a minimal repro in the `app` module.

## License

By contributing, you agree your contributions are licensed under this project's [MIT License](LICENSE).
```

- [ ] **Step 2: Commit**

```bash
git add CONTRIBUTING.md
git commit -m "docs: add CONTRIBUTING guide"
```

---

### Task 16: README badges

**Files:**
- Modify: `README.md`

**Interfaces:** None — documentation only. Depends on Task 11 (CI workflow filename `android-ci.yml`).

- [ ] **Step 1: Add badges below the title**

Directly below the `# 📸 JetImagePicker` heading, add:

```markdown
[![CI](https://github.com/nerojust/JetImagePicker/actions/workflows/android-ci.yml/badge.svg)](https://github.com/nerojust/JetImagePicker/actions/workflows/android-ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.nerojust/jetimagepicker.svg)](https://central.sonatype.com/artifact/io.github.nerojust/jetimagepicker)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add CI, Maven Central, and license badges to README"
```

---

### Task 17: Dependabot for Gradle + GitHub Actions updates

**Files:**
- Create: `.github/dependabot.yml`

**Interfaces:** None.

- [ ] **Step 1: Create the config**

```yaml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
```

- [ ] **Step 2: Commit**

```bash
git add .github/dependabot.yml
git commit -m "chore: add Dependabot config for Gradle and GitHub Actions"
```
1