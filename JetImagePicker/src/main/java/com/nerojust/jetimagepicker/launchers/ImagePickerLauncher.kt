package com.nerojust.jetimagepicker.launchers

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import com.mr0xf00.easycrop.CropError
import com.mr0xf00.easycrop.CropResult
import com.mr0xf00.easycrop.crop
import com.mr0xf00.easycrop.rememberImageCropper
import com.mr0xf00.easycrop.ui.ImageCropperDialog
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import com.nerojust.jetimagepicker.config.toRatioOrNull
import com.nerojust.jetimagepicker.model.PermissionState
import com.nerojust.jetimagepicker.result.ImagePickerResult
import com.nerojust.jetimagepicker.utils.Utils.compressImage
import com.nerojust.jetimagepicker.utils.Utils.createImageUri
import com.nerojust.jetimagepicker.utils.Utils.writeBitmapToCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Sets up the gallery and camera activity-result launchers backing [rememberJetImagePickerState].
 * Gallery picking uses Android's Photo Picker contracts and requires no storage permission;
 * camera capture requests `android.Manifest.permission.CAMERA` at runtime. When
 * [JetImagePickerConfig.enableCrop] is true, a crop dialog is shown automatically before
 * compression whenever exactly one image was picked/captured (or for every image, when
 * [JetImagePickerConfig.cropMultiple] is true).
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
): Pair<() -> Unit, () -> Unit> =
    rememberImagePickerLauncherWithResults(
        context = context,
        config = config,
        onImagesPicked = onImagesPicked,
        onPermissionStateChanged = onPermissionStateChanged,
        onLoadingChanged = onLoadingChanged,
        onCancelled = { onImagesPicked(emptyList()) },
        onError = { onImagesPicked(emptyList()) },
        onOwnedUrisChanged = {},
    )

@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
internal fun rememberImagePickerLauncherWithResults(
    context: Context,
    config: JetImagePickerConfig,
    onImagesPicked: (List<Uri>) -> Unit,
    onPermissionStateChanged: (PermissionState) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onCancelled: () -> Unit,
    onError: (ImagePickerResult.Error) -> Unit,
    onOwnedUrisChanged: (List<Uri>) -> Unit,
): Pair<() -> Unit, () -> Unit> {
    val activity = checkNotNull(context.findActivity()) { "Context must be or wrap an Activity" }

    var tempCameraUri by rememberSaveable(stateSaver = NullableUriSaver) {
        mutableStateOf<Uri?>(null)
    }
    var shouldLaunchCamera by remember { mutableStateOf(false) }
    var hasCameraPermissionBeenRequested by rememberSaveable { mutableStateOf(false) }
    var previousOwnedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    // ponytail: boolean gate, not a mutex - fully serializes pick-and-compress cycles so
    // previousOwnedUris is never touched by two overlapping processPicked calls.
    var isProcessing by remember { mutableStateOf(false) }
    val requestGate = remember { RequestGate() }

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

    imageCropper.cropState?.let { ImageCropperDialog(it) }

    // Crops each of [uris] in turn and preserves cancellation versus processing failures.
    suspend fun cropAll(uris: List<Uri>): CropBatchResult =
        cropBatch(
            uris = uris,
            crop = { uri -> imageCropper.crop(uri, context) },
            save = { bitmap -> writeBitmapToCache(context, bitmap.asAndroidBitmap()) },
            delete = { owned -> deleteOwnedFiles(context, owned) },
            onCropException = { uri, error -> Log.e("JetImagePicker", "Crop failed for $uri", error) },
        )

    // Empty input and crop cancellation must notify the caller immediately; the final return
    // carries the successfully processed/fallback list back to the camera cleanup path.
    @Suppress("ReturnCount")
    suspend fun processPicked(
        uris: List<Uri>,
        libraryOwnedInputs: Set<Uri> = emptySet(),
    ): List<Uri> {
        if (uris.isEmpty()) {
            onCancelled()
            return emptyList()
        }
        isProcessing = true
        try {
            val shouldCrop = config.enableCrop && (uris.size == 1 || config.cropMultiple)
            val cropResult =
                if (shouldCrop) {
                    cropAll(uris)
                } else {
                    CropBatchResult.Success(emptyList())
                }
            val cropped =
                when (cropResult) {
                    CropBatchResult.Cancelled -> {
                        onCancelled()
                        return emptyList()
                    }
                    is CropBatchResult.Failed -> {
                        onError(ImagePickerResult.Error(cropResult.message))
                        return emptyList()
                    }
                    is CropBatchResult.Success -> cropResult.uris
                }
            val toCompress = if (shouldCrop) cropped else uris
            val finalUris =
                if (config.enableCompression) {
                    onLoadingChanged(true)
                    val processed =
                        try {
                            toCompress.map { source ->
                                compressionOutputOrOriginal(source, compressImage(context, source, config))
                            }
                        } finally {
                            onLoadingChanged(false)
                        }
                    // Delete only intermediate crops that compression actually replaced. A crop
                    // whose compression failed is now the fallback result and must stay readable.
                    deleteOwnedFiles(context, cropped.filterNot(processed::contains))
                    processed
                } else {
                    toCompress
                }

            // Track only files the library created. In particular, a gallery URI used as a
            // compression fallback must never enter this list or a later pick would delete it.
            val ownedFinalUris = ownedImageResults(finalUris, cropped, libraryOwnedInputs, toCompress)
            deleteOwnedFiles(context, previousOwnedUris.filterNot(finalUris::contains))
            previousOwnedUris = ownedFinalUris
            onOwnedUrisChanged(ownedFinalUris)
            onImagesPicked(finalUris)
            return finalUris
        } finally {
            isProcessing = false
        }
    }

    // Modern gallery picking: no storage runtime permission required.
    val singlePickMediaLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            requestGate.finish()
            Log.d("JetImagePicker", "Gallery picked: $uri")
            coroutineScope.launch { processPicked(listOfNotNull(uri)) }
        }

    val maxSelection = config.maxSelection
    val multiPickMediaLauncher =
        rememberLauncherForActivityResult(
            if (maxSelection != null) {
                // The contract rejects limits below 2 or above the system picker's own maximum.
                ActivityResultContracts.PickMultipleVisualMedia(pickerMaxItems(maxSelection, systemPickLimit()))
            } else {
                ActivityResultContracts.PickMultipleVisualMedia()
            },
        ) { uris ->
            requestGate.finish()
            Log.d("JetImagePicker", "Gallery picked ${uris.size} image(s)")
            // Pickers that fall back to the document UI ignore the limit, so enforce it here too.
            val limited = uris.limitedTo(maxSelection)
            coroutineScope.launch { processPicked(limited) }
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(TakePicture()) { success ->
            requestGate.finish()
            val capturedUri = tempCameraUri
            if (success && capturedUri != null) {
                coroutineScope.launch {
                    var finalUris: List<Uri>? = null
                    try {
                        finalUris = processPicked(listOf(capturedUri), setOf(capturedUri))
                    } finally {
                        // Also runs when the composition is cancelled during crop/compression, so
                        // a raw capture cannot be orphaned across Activity recreation.
                        if (capturedUri !in finalUris.orEmpty()) {
                            context.contentResolver.delete(capturedUri, null, null)
                        }
                        tempCameraUri = null
                    }
                }
            } else {
                // Capture was cancelled/failed - clean up the temp file we created for it.
                capturedUri?.let { context.contentResolver.delete(it, null, null) }
                tempCameraUri = null
                onCancelled()
            }
        }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(RequestPermission()) { granted ->
            // Compute state using whether we'd asked before THIS request, then record that we have.
            val state =
                calculatePermissionState(
                    activity = activity,
                    context = context,
                    permission = Manifest.permission.CAMERA,
                    hasBeenRequestedBefore = hasCameraPermissionBeenRequested,
                )
            hasCameraPermissionBeenRequested = true
            onPermissionStateChanged(state)
            if (granted) {
                shouldLaunchCamera = true
            } else {
                requestGate.finish()
            }
        }

    // Defer camera launch to avoid re-entry issues
    LaunchedEffect(shouldLaunchCamera) {
        if (shouldLaunchCamera) {
            shouldLaunchCamera = false
            val uri =
                try {
                    createImageUri(context)
                } catch (e: IllegalArgumentException) {
                    requestGate.finish()
                    Log.e("JetImagePicker", missingFileProviderMessage(context), e)
                    onError(ImagePickerResult.Error(missingFileProviderMessage(context)))
                    return@LaunchedEffect
                }
            tempCameraUri = uri
            if (!launchSafely("camera") { cameraLauncher.launch(uri) }) {
                context.contentResolver.delete(uri, null, null)
                tempCameraUri = null
                requestGate.finish()
                onError(ImagePickerResult.Error("No app is available to capture an image"))
            }
        }
    }

    val launchGallery = {
        if (!isProcessing && requestGate.tryStart()) {
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            val launched =
                launchSafely("gallery") {
                    if (usesMultiPicker(config)) {
                        multiPickMediaLauncher.launch(request)
                    } else {
                        singlePickMediaLauncher.launch(request)
                    }
                }
            if (!launched) {
                requestGate.finish()
                onError(ImagePickerResult.Error("No app is available to pick an image"))
            }
        }
    }

    val launchCamera = {
        if (!isProcessing && requestGate.tryStart()) {
            val state =
                calculatePermissionState(
                    activity = activity,
                    context = context,
                    permission = Manifest.permission.CAMERA,
                    hasBeenRequestedBefore = hasCameraPermissionBeenRequested,
                )
            if (state.isGranted) {
                shouldLaunchCamera = true
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    return Pair(launchGallery, launchCamera)
}

/**
 * Runs [launch], returning false instead of crashing when the device has no app to handle it
 * (e.g. no camera app, or a stripped-down build with no picker or document UI).
 */
internal inline fun launchSafely(
    what: String,
    launch: () -> Unit,
): Boolean =
    try {
        launch()
        true
    } catch (e: ActivityNotFoundException) {
        Log.e("JetImagePicker", "No app available to handle the $what request", e)
        false
    }

internal sealed interface CropBatchResult {
    data class Success(val uris: List<Uri>) : CropBatchResult

    data object Cancelled : CropBatchResult

    data class Failed(val message: String) : CropBatchResult
}

/** Processes a crop batch with injectable I/O so save failures and partial-output cleanup are testable. */
@Suppress("TooGenericExceptionCaught", "ReturnCount")
internal suspend fun cropBatch(
    uris: List<Uri>,
    crop: suspend (Uri) -> CropResult,
    save: (androidx.compose.ui.graphics.ImageBitmap) -> Uri?,
    delete: (List<Uri>) -> Unit,
    onCropException: (Uri, Exception) -> Unit = { _, _ -> },
): CropBatchResult {
    val cropped = mutableListOf<Uri>()
    for (uri in uris) {
        val result =
            try {
                crop(uri)
            } catch (e: CancellationException) {
                delete(cropped)
                throw e
            } catch (e: Exception) {
                onCropException(uri, e)
                delete(cropped)
                return CropBatchResult.Failed("Failed to crop the selected image")
            }
        when (result) {
            CropResult.Cancelled -> {
                delete(cropped)
                return CropBatchResult.Cancelled
            }
            is CropError -> {
                delete(cropped)
                return CropBatchResult.Failed("Crop failed: ${result.name}")
            }
            is CropResult.Success -> Unit
        }
        val saved = save(result.bitmap)
        if (saved == null) {
            delete(cropped)
            return CropBatchResult.Failed("Failed to save the cropped image")
        }
        cropped += saved
    }
    return CropBatchResult.Success(cropped)
}

/** Deletes cache files this library wrote itself (never call it with a gallery URI). */
internal fun deleteOwnedFiles(
    context: Context,
    uris: List<Uri>,
) {
    for (uri in uris) context.contentResolver.delete(uri, null, null)
}

/** Logged when the consumer's manifest lacks the FileProvider the README's setup asks for. */
internal fun missingFileProviderMessage(context: Context): String =
    "No FileProvider for \"${context.packageName}.provider\" - see the README setup"
