package com.nerojust.jetimagepicker.launchers

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore
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
import com.mr0xf00.easycrop.CropResult
import com.mr0xf00.easycrop.crop
import com.mr0xf00.easycrop.rememberImageCropper
import com.mr0xf00.easycrop.ui.ImageCropperDialog
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import com.nerojust.jetimagepicker.config.toRatioOrNull
import com.nerojust.jetimagepicker.model.PermissionState
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
): Pair<() -> Unit, () -> Unit> {
    val activity =
        context as? Activity
            ?: throw IllegalStateException("Context must be an Activity")

    var tempCameraUri by rememberSaveable(stateSaver = NullableUriSaver) {
        mutableStateOf<Uri?>(null)
    }
    var shouldLaunchCamera by remember { mutableStateOf(false) }
    var hasCameraPermissionBeenRequested by rememberSaveable { mutableStateOf(false) }
    var previousOwnedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    // ponytail: boolean gate, not a mutex - fully serializes pick-and-compress cycles so
    // previousOwnedUris is never touched by two overlapping processPicked calls.
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

    imageCropper.cropState?.let { ImageCropperDialog(it) }

    // Crops each of [uris] in turn; null if the user cancels (or a crop fails) partway through.
    @Suppress("TooGenericExceptionCaught") // A crash in the crop library must not take down the host app.
    suspend fun cropAll(uris: List<Uri>): List<Uri>? {
        val cropped = mutableListOf<Uri>()
        for (uri in uris) {
            val result =
                try {
                    imageCropper.crop(uri, context)
                } catch (e: CancellationException) {
                    // The composition left mid-crop: nobody will receive these, so don't orphan them.
                    deleteOwnedFiles(context, cropped)
                    throw e
                } catch (e: Exception) {
                    Log.e("JetImagePicker", "Crop failed for $uri", e)
                    null
                }
            if (result !is CropResult.Success) {
                // Drop crops already made earlier in a multi-select.
                deleteOwnedFiles(context, cropped)
                return null
            }
            writeBitmapToCache(context, result.bitmap.asAndroidBitmap())?.let { cropped += it }
        }
        return cropped
    }

    // Empty input and crop cancellation must notify the caller immediately; the final return
    // carries the successfully processed/fallback list back to the camera cleanup path.
    @Suppress("ReturnCount")
    suspend fun processPicked(
        uris: List<Uri>,
        libraryOwnedInputs: Set<Uri> = emptySet(),
    ): List<Uri> {
        if (uris.isEmpty()) {
            onImagesPicked(emptyList())
            return emptyList()
        }
        isProcessing = true
        try {
            val shouldCrop = config.enableCrop && (uris.size == 1 || config.cropMultiple)
            val cropped =
                if (shouldCrop) {
                    // Cancelled or failed - treat like any other cancellation in this library.
                    cropAll(uris) ?: run {
                        onImagesPicked(emptyList())
                        return emptyList()
                    }
                } else {
                    emptyList()
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
            val ownedFinalUris =
                finalUris.filter { uri ->
                    uri in cropped || uri in libraryOwnedInputs || uri !in toCompress
                }
            deleteOwnedFiles(context, previousOwnedUris.filterNot(finalUris::contains))
            previousOwnedUris = ownedFinalUris
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
            Log.d("JetImagePicker", "Gallery picked ${uris.size} image(s)")
            // Pickers that fall back to the document UI ignore the limit, so enforce it here too.
            val limited = uris.limitedTo(maxSelection)
            coroutineScope.launch { processPicked(limited) }
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(TakePicture()) { success ->
            val capturedUri = tempCameraUri
            if (success && capturedUri != null) {
                coroutineScope.launch {
                    val finalUris = processPicked(listOf(capturedUri), setOf(capturedUri))
                    // Delete the raw capture only when a crop/compression output replaced it, or
                    // the operation was cancelled. If compression failed, capturedUri is the
                    // fallback result and must remain readable by the caller.
                    if (capturedUri !in finalUris) {
                        context.contentResolver.delete(capturedUri, null, null)
                    }
                    tempCameraUri = null
                }
            } else {
                // Capture was cancelled/failed - clean up the temp file we created for it.
                capturedUri?.let { context.contentResolver.delete(it, null, null) }
                tempCameraUri = null
                onImagesPicked(emptyList())
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
                    Log.e("JetImagePicker", missingFileProviderMessage(context), e)
                    onImagesPicked(emptyList())
                    return@LaunchedEffect
                }
            tempCameraUri = uri
            if (!launchSafely("camera") { cameraLauncher.launch(uri) }) {
                tempCameraUri = null
                onImagesPicked(emptyList())
            }
        }
    }

    val launchGallery = {
        if (!isProcessing) {
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            val launched =
                launchSafely("gallery") {
                    if (usesMultiPicker(config)) {
                        multiPickMediaLauncher.launch(request)
                    } else {
                        singlePickMediaLauncher.launch(request)
                    }
                }
            if (!launched) onImagesPicked(emptyList())
        }
    }

    val launchCamera = {
        if (!isProcessing) {
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

/** Whether a gallery pick uses the multi-select picker for this [config]. */
internal fun usesMultiPicker(config: JetImagePickerConfig): Boolean = config.allowMultiple && config.maxSelection != 1

/**
 * The limit to hand the multi-select contract, which rejects anything below 2 or above the
 * system picker's own maximum.
 */
internal fun pickerMaxItems(
    maxSelection: Int,
    systemLimit: Int,
): Int = maxSelection.coerceIn(2, maxOf(2, systemLimit))

/** The first [max] items, or all of them when there is no limit. */
internal fun <T> List<T>.limitedTo(max: Int?): List<T> = if (max == null) this else take(max)

/** Uses a successfully processed output, otherwise preserves the original input as a fallback. */
internal fun <T> compressionOutputOrOriginal(
    original: T,
    output: T?,
): T = output ?: original

/** The most items Android's Photo Picker lets one request ask for, or no limit where it isn't available. */
private fun systemPickLimit(): Int = runCatching { platformPickLimit() }.getOrDefault(Int.MAX_VALUE)

@SuppressLint("NewApi") // getPickImagesMaxLimit exists exactly where the check below says it does.
private fun platformPickLimit(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
        (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2
        )
    ) {
        MediaStore.getPickImagesMaxLimit()
    } else {
        Int.MAX_VALUE
    }
