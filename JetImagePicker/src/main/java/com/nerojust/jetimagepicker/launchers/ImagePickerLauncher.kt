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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import com.nerojust.jetimagepicker.model.PermissionState
import com.nerojust.jetimagepicker.utils.Utils.compressImage
import com.nerojust.jetimagepicker.utils.Utils.createImageUri
import kotlinx.coroutines.launch

private val NullableUriSaver =
    Saver<Uri?, String>(
        save = { it?.toString() ?: "" },
        restore = { if (it.isEmpty()) null else Uri.parse(it) },
    )

/**
 * Sets up the gallery and camera activity-result launchers backing [rememberJetImagePickerState].
 * Gallery picking uses Android's Photo Picker contracts and requires no storage permission;
 * camera capture requests `android.Manifest.permission.CAMERA` at runtime.
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
    // Android's shouldShowRequestPermissionRationale() returns false both before the permission
    // has ever been requested and once it's permanently denied - the two are indistinguishable
    // without tracking history ourselves, so we remember whether we've asked before.
    var hasCameraPermissionBeenRequested by rememberSaveable { mutableStateOf(false) }
    var previousCompressedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    // ponytail: boolean gate, not a mutex — fully serializes pick-and-compress cycles so
    // previousCompressedUris is never touched by two overlapping processPicked calls.
    var isProcessing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    suspend fun processPicked(uris: List<Uri>) {
        isProcessing = true
        try {
            if (uris.isEmpty()) {
                onImagesPicked(emptyList())
                return
            }
            if (config.enableCompression) {
                onLoadingChanged(true)
                val compressed = uris.mapNotNull { compressImage(context, it, config) }
                onLoadingChanged(false)
                // Compressed files are ours (written to cacheDir via FileProvider) — safe to delete.
                for (uri in previousCompressedUris) {
                    context.contentResolver.delete(uri, null, null)
                }
                previousCompressedUris = compressed
                onImagesPicked(compressed)
            } else {
                onImagesPicked(uris)
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
                    // now tracked in previousCompressedUris — the raw temp file is otherwise never
                    // cleaned up and would leak in cacheDir. Leave it alone when compression is
                    // disabled: capturedUri is then the live selection handed to onImagesPicked.
                    if (config.enableCompression) {
                        context.contentResolver.delete(capturedUri, null, null)
                    }
                }
            } else {
                // Capture was cancelled/failed — clean up the temp file we created for it.
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
        // Only treat "no rationale offered" as permanent once we know we've asked before -
        // otherwise it's the ambiguous pre-first-request state, reported as a plain denial.
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
