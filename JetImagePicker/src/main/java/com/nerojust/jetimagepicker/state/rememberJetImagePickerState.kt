package com.nerojust.jetimagepicker.state

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import com.nerojust.jetimagepicker.launchers.deleteOwnedFiles
import com.nerojust.jetimagepicker.launchers.rememberImagePickerLauncherWithResults
import com.nerojust.jetimagepicker.result.ImagePickerResult
import com.nerojust.jetimagepicker.result.toImagePickerResult

/**
 * Creates and remembers a [JetImagePickerState] for picking images from the gallery or capturing
 * one with the camera, handling runtime permissions and optional compression.
 *
 * @param context Must be (or wrap) an [android.app.Activity].
 * @param config Picker behavior — compression, resizing, single vs. multiple selection.
 * @param onResult Invoked with an [ImagePickerResult] on every pick, capture, or permission event.
 */
@Composable
fun rememberJetImagePickerState(
    context: Context,
    config: JetImagePickerConfig = JetImagePickerConfig(),
    onResult: (ImagePickerResult) -> Unit,
): JetImagePickerState {
    var selectedUris by rememberSaveable(stateSaver = UriListSaver) {
        mutableStateOf(emptyList())
    }
    var isLoading by remember { mutableStateOf(false) }
    var ownedUris by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf(emptyList()) }

    OwnedFileCleanup(context, ownedUris)

    val selectedImageUris = remember { derivedStateOf { selectedUris } }
    val selectedImageUri = remember { derivedStateOf { selectedUris.firstOrNull() } }
    val loadingState = remember { derivedStateOf { isLoading } }

    val (launchGallery, launchCamera) =
        rememberImagePickerLauncherWithResults(
            context = context,
            config = config,
            onImagesPicked = { uris ->
                selectedUris = uris
                onResult(ImagePickerResult.Success(uris))
            },
            onPermissionStateChanged = { permissionState ->
                permissionState.toImagePickerResult()?.let(onResult)
            },
            onLoadingChanged = { loading -> isLoading = loading },
            onCancelled = { onResult(ImagePickerResult.Cancelled) },
            onError = onResult,
            onOwnedUrisChanged = { ownedUris = it },
        )

    return JetImagePickerState(
        _selectedImageUris = selectedImageUris,
        _selectedImageUri = selectedImageUri,
        _isLoading = loadingState,
        pickFromGallery = launchGallery,
        captureWithCamera = launchCamera,
        clearSelection = {
            deleteOwnedFiles(context, ownedUris)
            ownedUris = emptyList()
            selectedUris = emptyList()
        },
    )
}
