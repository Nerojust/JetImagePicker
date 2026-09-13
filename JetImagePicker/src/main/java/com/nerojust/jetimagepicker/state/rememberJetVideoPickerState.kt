package com.nerojust.jetimagepicker.state

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.nerojust.jetimagepicker.config.JetVideoPickerConfig
import com.nerojust.jetimagepicker.launchers.NullableUriSaver
import com.nerojust.jetimagepicker.launchers.rememberVideoPickerLauncher
import com.nerojust.jetimagepicker.result.VideoPickerResult
import com.nerojust.jetimagepicker.result.toVideoPickerResult

/**
 * Creates and remembers a [JetVideoPickerState] for picking video from the gallery or capturing
 * one with the camera, handling runtime permissions and optional compression/thumbnail/duration
 * limiting.
 *
 * @param context Must be (or wrap) an [android.app.Activity].
 * @param config Picker behavior — compression, thumbnail generation, duration limit.
 * @param onResult Invoked with a [VideoPickerResult] on every pick, capture, or permission event.
 */
@Composable
fun rememberJetVideoPickerState(
    context: Context,
    config: JetVideoPickerConfig = JetVideoPickerConfig(),
    onResult: (VideoPickerResult) -> Unit,
): JetVideoPickerState {
    var selectedUri by rememberSaveable(stateSaver = NullableUriSaver) {
        mutableStateOf<Uri?>(null)
    }
    var isLoading by remember { mutableStateOf(false) }

    val selectedVideoUri = remember { derivedStateOf { selectedUri } }
    val loadingState = remember { derivedStateOf { isLoading } }

    val (launchGallery, launchCamera) =
        rememberVideoPickerLauncher(
            context = context,
            config = config,
            onVideoPicked = { uri, thumbnailUri ->
                selectedUri = uri
                onResult(VideoPickerResult.Success(uri = uri, thumbnailUri = thumbnailUri))
            },
            onDurationExceeded = { uri, limitSeconds ->
                onResult(VideoPickerResult.DurationExceeded(uri, limitSeconds))
            },
            onPermissionStateChanged = { permissionState ->
                permissionState.toVideoPickerResult()?.let(onResult)
            },
            onLoadingChanged = { loading -> isLoading = loading },
        )

    return JetVideoPickerState(
        _selectedVideoUri = selectedVideoUri,
        _isLoading = loadingState,
        pickFromGallery = launchGallery,
        captureWithCamera = launchCamera,
        clearSelection = { selectedUri = null },
    )
}
