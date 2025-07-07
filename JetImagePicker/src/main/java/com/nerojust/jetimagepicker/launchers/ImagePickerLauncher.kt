package com.nerojust.jetimagepicker.launchers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import com.nerojust.jetimagepicker.config.PickImagesContract
import com.nerojust.jetimagepicker.model.PermissionState
import com.nerojust.jetimagepicker.utils.Utils.compressImage
import com.nerojust.jetimagepicker.utils.Utils.createImageUri

@Composable
fun rememberImagePickerLauncher(
    context: Context,
    config: JetImagePickerConfig = JetImagePickerConfig(),
    onImagesPicked: (List<Uri>) -> Unit,
    onPermissionStateChanged: (PermissionState) -> Unit
): Pair<() -> Unit, () -> Unit> {

    val activity = context as? Activity
        ?: throw IllegalStateException("Context must be an Activity")

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    var shouldLaunchGallery by remember { mutableStateOf(false) }
    var shouldLaunchCamera by remember { mutableStateOf(false) }

    // Custom image picker supporting single or multiple
    val galleryLauncher = rememberLauncherForActivityResult(
        PickImagesContract(config.allowMultiple)
    ) { uris ->
        val finalUris = if (config.enableCompression) {
            uris.mapNotNull { compressImage(context, it, config) }
        } else uris

        Log.d("JetImagePicker", "Selected ${finalUris.size} image(s)")
        onImagesPicked(finalUris)
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            val uri = if (config.enableCompression) {
                compressImage(context, tempCameraUri!!, config)
            } else tempCameraUri
            onImagesPicked(listOfNotNull(uri))
        } else {
            onImagesPicked(emptyList())
        }
    }

    // Permission state calculation
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

    val galleryPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        val permission = getGalleryPermission()
        val state = calculatePermissionState(permission)
        onPermissionStateChanged(state)

        if (granted) {
            shouldLaunchGallery = true
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        val state = calculatePermissionState(Manifest.permission.CAMERA)
        onPermissionStateChanged(state)

        if (granted) {
            shouldLaunchCamera = true
        }
    }

    // Defer gallery launch to avoid re-entry issues
    LaunchedEffect(shouldLaunchGallery) {
        if (shouldLaunchGallery) {
            shouldLaunchGallery = false
            galleryLauncher.launch("image/*")
        }
    }

    // Defer camera launch
    LaunchedEffect(shouldLaunchCamera) {
        if (shouldLaunchCamera) {
            shouldLaunchCamera = false
            val uri = createImageUri(context)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    // External calls
    val launchGallery = {
        val permission = getGalleryPermission()
        val state = calculatePermissionState(permission)
        if (state.isGranted) {
            shouldLaunchGallery = true
        } else {
            galleryPermissionLauncher.launch(permission)
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

private fun getGalleryPermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES
    else
        Manifest.permission.READ_EXTERNAL_STORAGE
}
