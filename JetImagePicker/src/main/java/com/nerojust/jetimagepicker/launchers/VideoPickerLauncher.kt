package com.nerojust.jetimagepicker.launchers

import android.Manifest
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
import com.nerojust.jetimagepicker.ui.RecordingResult
import com.nerojust.jetimagepicker.ui.TrimVideoDialog
import com.nerojust.jetimagepicker.utils.VideoUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Sets up the gallery picker and in-app camera recorder backing
 * [com.nerojust.jetimagepicker.state.rememberJetVideoPickerState]. Gallery picking uses Android's
 * Photo Picker (video-only) and requires no permission; camera capture requests
 * `android.Manifest.permission.CAMERA` + `android.Manifest.permission.RECORD_AUDIO` together and
 * records in-app via [RecordVideoDialog]. When [JetVideoPickerConfig.enableTrim] is true, a
 * [TrimVideoDialog] step runs before the duration check, for both pick and capture.
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
): Pair<() -> Unit, () -> Unit> =
    rememberVideoPickerLauncherWithResults(
        context = context,
        config = config,
        onVideoPicked = onVideoPicked,
        onDurationExceeded = onDurationExceeded,
        onPermissionsRequired = onPermissionsRequired,
        onLoadingChanged = onLoadingChanged,
        onCancelled = { onVideoPicked(null, null) },
        onError = { onVideoPicked(null, null) },
        onOwnedUrisChanged = {},
    )

@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
internal fun rememberVideoPickerLauncherWithResults(
    context: Context,
    config: JetVideoPickerConfig,
    onVideoPicked: (uri: Uri, thumbnailUri: Uri?) -> Unit,
    onDurationExceeded: (Uri, Int) -> Unit,
    onPermissionsRequired: (VideoPickerResult.PermissionsRequired) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onCancelled: () -> Unit,
    onError: (VideoPickerResult.Error) -> Unit,
    onOwnedUrisChanged: (List<Uri>) -> Unit,
): Pair<() -> Unit, () -> Unit> {
    val activity = checkNotNull(context.findActivity()) { "Context must be or wrap an Activity" }

    var hasCameraPermissionBeenRequested by rememberSaveable { mutableStateOf(false) }
    var hasAudioPermissionBeenRequested by rememberSaveable { mutableStateOf(false) }
    var showRecordDialog by rememberSaveable { mutableStateOf(false) }
    var pendingTrimUri by rememberSaveable(stateSaver = NullableUriSaver) { mutableStateOf(null) }
    var pendingTrimIsLibraryOwned by rememberSaveable { mutableStateOf(false) }
    var previousOutputUri by remember { mutableStateOf<Uri?>(null) }
    var previousThumbnailUri by remember { mutableStateOf<Uri?>(null) }
    // ponytail: boolean gate, not a mutex - fully serializes pick-and-process cycles, same
    // pattern as ImagePickerLauncher's isProcessing.
    var isProcessing by remember { mutableStateOf(false) }
    val requestGate = remember { RequestGate() }

    val coroutineScope = rememberCoroutineScope()

    @Suppress("ReturnCount")
    suspend fun processPicked(
        uri: Uri?,
        isLibraryOwned: Boolean = false,
    ) {
        if (uri == null) {
            onCancelled()
            return
        }
        isProcessing = true
        var output: Uri? = null
        try {
            val durationMillis = VideoUtils.getVideoDurationMillis(context, uri)
            val limitSeconds = config.durationLimitSeconds
            if (durationMillis == null && limitSeconds != null) {
                onError(VideoPickerResult.Error("Could not read video duration to enforce the configured limit"))
                if (isLibraryOwned) context.contentResolver.delete(uri, null, null)
                return
            }
            if (VideoUtils.isDurationExceeded(durationMillis, limitSeconds) && limitSeconds != null) {
                onDurationExceeded(uri, limitSeconds)
                // Nothing supersedes the raw capture on this path (it is never returned to the
                // caller), so clean it up now that onDurationExceeded has been notified. Never
                // delete a caller-owned (gallery-picked) uri.
                if (VideoUtils.shouldDeleteSource(isLibraryOwned, uri, output = null)) {
                    context.contentResolver.delete(uri, null, null)
                }
                return
            }

            onLoadingChanged(true)
            try {
                val ready = if (config.enableCompression) VideoUtils.compressVideo(context, uri) ?: uri else uri
                output = ready

                // A compressed output is ours (written to cacheDir via FileProvider) - safe to
                // delete once superseded. Never delete the caller's original picked/captured uri.
                previousOutputUri?.takeIf { it != uri }?.let { context.contentResolver.delete(it, null, null) }
                previousOutputUri = output.takeIf { it != uri }

                // The raw source is also ours to clean up once superseded, if it's library-owned
                // (a camera capture, or a trim output that already superseded an earlier source).
                if (VideoUtils.shouldDeleteSource(isLibraryOwned, uri, output)) {
                    context.contentResolver.delete(uri, null, null)
                }

                val thumbnailUri =
                    if (config.enableThumbnail) VideoUtils.extractVideoThumbnail(context, output) else null
                previousThumbnailUri?.takeIf { it != thumbnailUri }?.let {
                    context.contentResolver.delete(it, null, null)
                }
                previousThumbnailUri = thumbnailUri
                onOwnedUrisChanged(ownedVideoResults(uri, output, isLibraryOwned, thumbnailUri))
                onVideoPicked(ready, thumbnailUri)
                Log.d("JetImagePicker", "Video ready: $output, thumbnail: $thumbnailUri")
            } finally {
                // Guaranteed on every exit path once loading started, so the caller's spinner
                // can't stick on after a failure.
                onLoadingChanged(false)
            }
        } catch (e: CancellationException) {
            // The composition left mid-processing (e.g. Activity recreated): no result will ever
            // be delivered, so clean up every file we own instead of orphaning it in cacheDir.
            if (isLibraryOwned) context.contentResolver.delete(uri, null, null)
            output?.takeIf { it != uri }?.let { context.contentResolver.delete(it, null, null) }
            throw e
        } finally {
            isProcessing = false
        }
    }

    suspend fun handlePicked(
        uri: Uri?,
        isLibraryOwned: Boolean = false,
    ) {
        if (uri == null) {
            onCancelled()
            return
        }
        if (config.enableTrim) {
            pendingTrimIsLibraryOwned = isLibraryOwned
            pendingTrimUri = uri
        } else {
            processPicked(uri, isLibraryOwned)
        }
    }

    // Modern gallery picking: no storage runtime permission required.
    val pickMediaLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            requestGate.finish()
            Log.d("JetImagePicker", "Gallery picked video: $uri")
            coroutineScope.launch { handlePicked(uri) }
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
                requestGate.finish()
                onPermissionsRequired(permissionsRequired)
            } else {
                showRecordDialog = true
            }
        }

    val launchGallery = {
        if (!isProcessing && requestGate.tryStart()) {
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            if (!launchSafely("gallery") { pickMediaLauncher.launch(request) }) {
                requestGate.finish()
                onError(VideoPickerResult.Error("No app is available to pick a video"))
            }
        }
    }

    val launchCamera = {
        if (!isProcessing && requestGate.tryStart()) {
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
            onFinished = { result ->
                showRecordDialog = false
                requestGate.finish()
                when (result) {
                    RecordingResult.Cancelled -> onCancelled()
                    is RecordingResult.Error -> onError(VideoPickerResult.Error(result.message))
                    is RecordingResult.Success -> {
                        val file = result.file
                        val uri =
                            try {
                                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            } catch (e: IllegalArgumentException) {
                                Log.e("JetImagePicker", missingFileProviderMessage(context), e)
                                file.delete()
                                onError(VideoPickerResult.Error(missingFileProviderMessage(context)))
                                null
                            }
                        if (uri != null) coroutineScope.launch { handlePicked(uri, isLibraryOwned = true) }
                    }
                }
            },
        )
    }

    pendingTrimUri?.let { trimUri ->
        TrimVideoDialog(
            uri = trimUri,
            onConfirm = { startMs, endMs ->
                val wasLibraryOwned = pendingTrimIsLibraryOwned
                pendingTrimUri = null
                pendingTrimIsLibraryOwned = false
                coroutineScope.launch {
                    isProcessing = true
                    onLoadingChanged(true)
                    try {
                        val durationMs = VideoUtils.getVideoDurationMillis(context, trimUri)
                        val isFullRange = durationMs != null && startMs == 0L && endMs >= durationMs
                        if (isFullRange) {
                            processPicked(trimUri, wasLibraryOwned)
                        } else {
                            val trimmedUri = VideoUtils.trimVideo(context, trimUri, startMs, endMs)
                            if (trimmedUri != null) {
                                if (VideoUtils.shouldDeleteSource(wasLibraryOwned, trimUri, trimmedUri)) {
                                    context.contentResolver.delete(trimUri, null, null)
                                }
                                processPicked(trimmedUri, isLibraryOwned = true)
                            } else {
                                // Trim failed - fall back to the untrimmed source, same treatment
                                // as a compressVideo failure already gets.
                                processPicked(trimUri, wasLibraryOwned)
                            }
                        }
                    } catch (e: CancellationException) {
                        // Same as processPicked: abandoned mid-trim, so drop a library-owned
                        // source (no-op if it was already superseded and deleted above).
                        if (wasLibraryOwned) context.contentResolver.delete(trimUri, null, null)
                        throw e
                    } finally {
                        isProcessing = false
                        onLoadingChanged(false)
                    }
                }
            },
            onCancel = {
                val wasLibraryOwned = pendingTrimIsLibraryOwned
                pendingTrimUri = null
                pendingTrimIsLibraryOwned = false
                coroutineScope.launch {
                    // Cancelling trim cancels the whole pick/capture, mirroring the image
                    // picker's crop-cancel behavior. Clean up a library-owned raw capture since
                    // it's being fully abandoned; never touch a caller-owned gallery uri.
                    if (wasLibraryOwned) context.contentResolver.delete(trimUri, null, null)
                    onCancelled()
                }
            },
            onError = { message ->
                val wasLibraryOwned = pendingTrimIsLibraryOwned
                pendingTrimUri = null
                pendingTrimIsLibraryOwned = false
                if (wasLibraryOwned) context.contentResolver.delete(trimUri, null, null)
                onError(VideoPickerResult.Error(message))
            },
        )
    }

    return Pair(launchGallery, launchCamera)
}
