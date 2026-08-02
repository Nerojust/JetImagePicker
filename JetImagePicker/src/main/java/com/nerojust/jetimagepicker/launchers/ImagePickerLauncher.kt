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
import com.mr0xf00.easycrop.crop
import com.mr0xf00.easycrop.rememberImageCropper
import com.mr0xf00.easycrop.ui.ImageCropperDialog
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

    imageCropper.cropState?.let { ImageCropperDialog(it) }

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
            // Compute state using whether we'd asked before THIS request, then record that we have.
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
