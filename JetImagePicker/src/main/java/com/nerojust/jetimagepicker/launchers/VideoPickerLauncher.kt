package com.nerojust.jetimagepicker.launchers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.nerojust.jetimagepicker.config.JetVideoPickerConfig
import com.nerojust.jetimagepicker.model.PermissionState
import com.nerojust.jetimagepicker.utils.VideoUtils
import kotlinx.coroutines.launch

/**
 * A custom [ActivityResultContract] wrapping [MediaStore.ACTION_VIDEO_CAPTURE], since the stock
 * [ActivityResultContracts.CaptureVideo] contract has no way to pass [MediaStore.EXTRA_DURATION_LIMIT].
 */
private class CaptureVideoWithDurationLimit(
    private val durationLimitSeconds: Int?,
) : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(
        context: Context,
        input: Uri,
    ): Intent =
        Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, input)
            durationLimitSeconds?.let { putExtra(MediaStore.EXTRA_DURATION_LIMIT, it) }
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Boolean = resultCode == Activity.RESULT_OK
}

/**
 * Sets up the gallery and camera activity-result launchers backing
 * [com.nerojust.jetimagepicker.state.rememberJetVideoPickerState]. Gallery picking uses Android's
 * Photo Picker (video-only) and requires no permission; camera capture requests
 * `android.Manifest.permission.CAMERA` at runtime — the system camera app itself handles audio
 * recording under its own permission, so no `RECORD_AUDIO` request is needed here.
 *
 * @return A pair of (launchGallery, launchCamera) functions.
 */
@Composable
fun rememberVideoPickerLauncher(
    context: Context,
    config: JetVideoPickerConfig = JetVideoPickerConfig(),
    onVideoPicked: (uri: Uri?, thumbnailUri: Uri?) -> Unit,
    onDurationExceeded: (Uri, Int) -> Unit,
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
    var previousOutputUri by remember { mutableStateOf<Uri?>(null) }
    // ponytail: boolean gate, not a mutex - fully serializes pick-and-process cycles, same
    // pattern as ImagePickerLauncher's isProcessing.
    var isProcessing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    suspend fun processPicked(
        uri: Uri?,
        isCameraCapture: Boolean = false,
    ) {
        if (uri == null) {
            onVideoPicked(null, null)
            return
        }
        isProcessing = true
        try {
            val durationSeconds = VideoUtils.getVideoDurationSeconds(context, uri)
            val limitSeconds = config.durationLimitSeconds
            if (VideoUtils.isDurationExceeded(durationSeconds, limitSeconds) && limitSeconds != null) {
                onDurationExceeded(uri, limitSeconds)
                // The raw capture is ours and is never returned to the caller on this path -
                // clean it up now that onDurationExceeded has been notified. Never delete a
                // gallery-picked uri (not ours to delete).
                if (isCameraCapture) context.contentResolver.delete(uri, null, null)
                return
            }

            onLoadingChanged(true)
            val output = if (config.enableCompression) VideoUtils.compressVideo(context, uri) ?: uri else uri
            onLoadingChanged(false)

            // A compressed output is ours (written to cacheDir via FileProvider) - safe to delete
            // once superseded. Never delete the caller's original picked/captured uri.
            previousOutputUri?.takeIf { it != uri }?.let { context.contentResolver.delete(it, null, null) }
            previousOutputUri = output.takeIf { it != uri }

            // The raw camera capture is also ours (written to cacheDir in createVideoUri) -
            // delete it once compression has produced a different, superseding output. Never
            // delete it when it IS the final returned uri (compression disabled or failed), and
            // never delete a gallery-picked uri.
            if (isCameraCapture && output != uri) {
                context.contentResolver.delete(uri, null, null)
            }

            val thumbnailUri = if (config.enableThumbnail) VideoUtils.extractVideoThumbnail(context, output) else null
            onVideoPicked(output, thumbnailUri)
            Log.d("JetImagePicker", "Video ready: $output, thumbnail: $thumbnailUri")
        } finally {
            isProcessing = false
        }
    }

    // Modern gallery picking: no storage runtime permission required.
    val pickMediaLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            Log.d("JetImagePicker", "Gallery picked video: $uri")
            coroutineScope.launch { processPicked(uri) }
        }

    val captureContract = remember(config.durationLimitSeconds) { CaptureVideoWithDurationLimit(config.durationLimitSeconds) }
    val cameraLauncher =
        rememberLauncherForActivityResult(captureContract) { success ->
            val capturedUri = tempCameraUri
            if (success && capturedUri != null) {
                coroutineScope.launch { processPicked(capturedUri, isCameraCapture = true) }
            } else {
                // Capture was cancelled/failed - clean up the temp file we created for it.
                capturedUri?.let { context.contentResolver.delete(it, null, null) }
                tempCameraUri = null
                coroutineScope.launch { onVideoPicked(null, null) }
            }
        }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(RequestPermission()) { granted ->
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
            val uri = VideoUtils.createVideoUri(context)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    val launchGallery = {
        if (!isProcessing) {
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            pickMediaLauncher.launch(request)
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
