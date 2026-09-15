package com.nerojust.jetimagepicker.launchers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.nerojust.jetimagepicker.config.JetVideoPickerConfig
import com.nerojust.jetimagepicker.result.VideoPickerResult
import com.nerojust.jetimagepicker.result.toPermissionsRequiredOrNull
import com.nerojust.jetimagepicker.ui.RecordVideoDialog
import com.nerojust.jetimagepicker.utils.VideoUtils
import kotlinx.coroutines.launch

/**
 * Sets up the gallery picker and in-app camera recorder backing
 * [com.nerojust.jetimagepicker.state.rememberJetVideoPickerState]. Gallery picking uses Android's
 * Photo Picker (video-only) and requires no permission; camera capture requests
 * `android.Manifest.permission.CAMERA` + `android.Manifest.permission.RECORD_AUDIO` together and
 * records in-app via [RecordVideoDialog], so [JetVideoPickerConfig.durationLimitSeconds] is
 * enforced live rather than depending on the system camera app to honor a request it can ignore.
 *
 * @return A pair of (launchGallery, launchCamera) functions.
 */
@Composable
fun rememberVideoPickerLauncher(
    context: Context,
    config: JetVideoPickerConfig = JetVideoPickerConfig(),
    onVideoPicked: (uri: Uri?, thumbnailUri: Uri?) -> Unit,
    onDurationExceeded: (Uri, Int) -> Unit,
    onPermissionsRequired: (VideoPickerResult.PermissionsRequired) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
): Pair<() -> Unit, () -> Unit> {
    val activity =
        context as? Activity
            ?: throw IllegalStateException("Context must be an Activity")

    var hasCameraPermissionBeenRequested by rememberSaveable { mutableStateOf(false) }
    var hasAudioPermissionBeenRequested by rememberSaveable { mutableStateOf(false) }
    var showRecordDialog by remember { mutableStateOf(false) }
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
                // Nothing supersedes the raw capture on this path (it is never returned to the
                // caller), so clean it up now that onDurationExceeded has been notified. Never
                // delete a gallery-picked uri (not ours to delete).
                if (VideoUtils.shouldDeleteSource(isCameraCapture, uri, output = null)) {
                    context.contentResolver.delete(uri, null, null)
                }
                return
            }

            onLoadingChanged(true)
            try {
                val output = if (config.enableCompression) VideoUtils.compressVideo(context, uri) ?: uri else uri

                // A compressed output is ours (written to cacheDir via FileProvider) - safe to
                // delete once superseded. Never delete the caller's original picked/captured uri.
                previousOutputUri?.takeIf { it != uri }?.let { context.contentResolver.delete(it, null, null) }
                previousOutputUri = output.takeIf { it != uri }

                // The raw camera capture is also ours - delete it once compression has produced
                // a different, superseding output.
                if (VideoUtils.shouldDeleteSource(isCameraCapture, uri, output)) {
                    context.contentResolver.delete(uri, null, null)
                }

                val thumbnailUri =
                    if (config.enableThumbnail) VideoUtils.extractVideoThumbnail(context, output) else null
                onVideoPicked(output, thumbnailUri)
                Log.d("JetImagePicker", "Video ready: $output, thumbnail: $thumbnailUri")
            } finally {
                // Guaranteed on every exit path once loading started, so the caller's spinner
                // can't stick on after a failure.
                onLoadingChanged(false)
            }
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

    val multiplePermissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            val cameraState =
                calculatePermissionState(
                    activity = activity,
                    context = context,
                    permission = Manifest.permission.CAMERA,
                    hasBeenRequestedBefore = hasCameraPermissionBeenRequested,
                )
            val audioState =
                calculatePermissionState(
                    activity = activity,
                    context = context,
                    permission = Manifest.permission.RECORD_AUDIO,
                    hasBeenRequestedBefore = hasAudioPermissionBeenRequested,
                )
            hasCameraPermissionBeenRequested = true
            hasAudioPermissionBeenRequested = true
            val permissionsRequired = toPermissionsRequiredOrNull(cameraState, audioState)
            if (permissionsRequired != null) {
                onPermissionsRequired(permissionsRequired)
            } else {
                showRecordDialog = true
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
            val cameraState =
                calculatePermissionState(
                    activity = activity,
                    context = context,
                    permission = Manifest.permission.CAMERA,
                    hasBeenRequestedBefore = hasCameraPermissionBeenRequested,
                )
            val audioState =
                calculatePermissionState(
                    activity = activity,
                    context = context,
                    permission = Manifest.permission.RECORD_AUDIO,
                    hasBeenRequestedBefore = hasAudioPermissionBeenRequested,
                )
            if (cameraState.isGranted && audioState.isGranted) {
                showRecordDialog = true
            } else {
                multiplePermissionsLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                )
            }
        }
    }

    if (showRecordDialog) {
        RecordVideoDialog(
            durationLimitSeconds = config.durationLimitSeconds,
            onFinished = { file ->
                showRecordDialog = false
                val uri = file?.let { FileProvider.getUriForFile(context, "${context.packageName}.provider", it) }
                coroutineScope.launch { processPicked(uri, isCameraCapture = true) }
            },
        )
    }

    return Pair(launchGallery, launchCamera)
}
