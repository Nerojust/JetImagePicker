package com.nerojust.jetimagepicker.state

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import com.nerojust.jetimagepicker.launchers.rememberImagePickerLauncher
import com.nerojust.jetimagepicker.result.ImagePickerResult

@Composable
fun rememberJetImagePickerState(
    context: Context,
    config: JetImagePickerConfig = JetImagePickerConfig(),
    onResult: (ImagePickerResult) -> Unit
): JetImagePickerState {
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val selectedImageUris = remember { derivedStateOf { selectedUris } }
    val selectedImageUri = remember { derivedStateOf { selectedUris.firstOrNull() } }

    val (launchGallery, launchCamera) = rememberImagePickerLauncher(
        context = context,
        config = config,
        onImagesPicked = { uris ->
            selectedUris = uris
            onResult(ImagePickerResult.Success(uris))
        },
        onPermissionStateChanged = { permissionState ->
            when {
                permissionState.isGranted -> { /* do nothing */
                }

                permissionState.isPermanentlyDenied -> {
                    onResult(ImagePickerResult.PermissionPermanentlyDenied(permissionState.permission))
                }

                permissionState.shouldShowRationale -> {
                    onResult(ImagePickerResult.ShowRationale(permissionState.permission))
                }

                permissionState.isDenied -> {
                    onResult(ImagePickerResult.PermissionDenied(permissionState.permission))
                }
            }
        }
    )

    return JetImagePickerState(
        _selectedImageUris = selectedImageUris,
        _selectedImageUri = selectedImageUri,
        pickFromGallery = launchGallery,
        captureWithCamera = launchCamera
    )
}
