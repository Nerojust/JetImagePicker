package com.nerojust.jetimagepicker.ui

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.nerojust.jetimagepicker.utils.VideoUtils
import java.io.File

private const val NANOS_PER_SECOND = 1_000_000_000L

// Same as the launchers (baselined): one composable owning the camera/recording lifecycle reads
// more clearly than splitting its tightly-coupled state across helpers.

/**
 * Full-screen in-app video recording dialog, shown automatically by
 * [com.nerojust.jetimagepicker.launchers.rememberVideoPickerLauncher] when
 * `captureWithCamera()` is called. Records via CameraX so [durationLimitSeconds] is enforced
 * live, rather than depending on the system camera app to honor a request it can ignore.
 *
 * @param onFinished Called with the recorded [File] on success (including a recording cut short by
 * the app going to the background), or `null` if the user cancelled, the camera failed to bind, or
 * recording finalized with any other error (all treated the same way).
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun RecordVideoDialog(
    durationLimitSeconds: Int?,
    onFinished: (File?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    val preview =
        remember {
            Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
        }
    val recorder = remember { Recorder.Builder().build() }
    // Save front-camera video mirrored, matching the (always mirrored) front preview the user saw.
    val videoCapture =
        remember { VideoCapture.Builder(recorder).setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY).build() }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var canFlip by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isCancelled by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0L) }

    fun stopAndDiscard() {
        if (isRecording) {
            isCancelled = true
            recording?.stop()
        } else {
            onFinished(null)
        }
    }

    // Re-runs on every flip: rebinding to the other lens gives a new Camera with its torch off.
    LaunchedEffect(lensFacing) {
        runCatching {
            val provider = ProcessCameraProvider.awaitInstance(context)
            cameraProvider = provider
            canFlip =
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) &&
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            // Only unbind this dialog's own use cases - unbindAll() would silently kill any
            // CameraX use cases the consuming app has bound elsewhere and never rebind them.
            provider.unbind(preview, videoCapture)
            isTorchOn = false
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
        }.onFailure { e ->
            // Must not treat coroutine cancellation (e.g. the dialog leaving composition after
            // the user tapped Cancel while awaitInstance was still suspended) as a camera
            // failure - runCatching catches Throwable unconditionally, so CancellationException
            // has to be rethrown to keep normal structured-cancellation propagation and avoid
            // calling onFinished(null) a second time here.
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Can throw InitializationException (no camera hardware) from awaitInstance, or
            // IllegalArgumentException (unsupported use-case combination) from bindToLifecycle -
            // both must degrade to onFinished(null) rather than crash the host app.
            Log.e("JetImagePicker", "Failed to initialize camera", e)
            onFinished(null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Torn down mid-recording without the Cancel button (e.g. Activity recreated by a
            // config change the orientation lock below doesn't cover): the async Finalize can no
            // longer deliver to anyone, so mark it cancelled to have handleFinalize delete the file.
            if (recording != null) isCancelled = true
            recording?.stop()
            cameraProvider?.unbind(preview, videoCapture)
        }
    }

    // Lock to the orientation recording started in, like a stock camera app: rotating would
    // recreate the Activity and tear down the in-progress recording.
    DisposableEffect(isRecording) {
        val activity = context as? Activity
        if (!isRecording || activity == null) return@DisposableEffect onDispose {}
        val previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose { activity.requestedOrientation = previousOrientation }
    }

    fun startRecording() {
        // Checked upstream before the dialog opens, but the dialog survives process death
        // (rememberSaveable) and the user may have revoked the mic in Settings meanwhile.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onFinished(null)
            return
        }
        isCancelled = false
        elapsedSeconds = 0L
        val file = VideoUtils.createVideoFile(context)
        runCatching {
            recorder
                .prepareRecording(context, FileOutputOptions.Builder(file).build())
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    // Drive the counter and the auto-stop from CameraX's own recording clock:
                    // a separate wall-clock ticker drifts from the real recorded duration and can
                    // let a recording overrun the limit, getting the finished file rejected and
                    // deleted by the downstream isDurationExceeded safety net.
                    val statsSeconds = event.recordingStats.recordedDurationNanos / NANOS_PER_SECOND
                    elapsedSeconds = statsSeconds
                    if (VideoUtils.shouldStopRecording(statsSeconds, durationLimitSeconds)) {
                        recording?.stop()
                    }
                    if (event is VideoRecordEvent.Finalize) {
                        // Don't leave the light burning once there's nothing left to record.
                        camera?.cameraControl?.enableTorch(false)
                        isTorchOn = false
                        isRecording = false
                        recording = null
                        handleFinalize(event, file, isCancelled, onFinished)
                    }
                }
        }.onSuccess { newRecording ->
            recording = newRecording
            isRecording = true
        }.onFailure { e ->
            // e.g. SecurityException if RECORD_AUDIO somehow isn't actually granted despite the
            // upstream permission check - must degrade to onFinished(null), not crash.
            Log.e("JetImagePicker", "Failed to start video recording", e)
            if (file.exists()) file.delete()
            onFinished(null)
        }
    }

    Dialog(
        onDismissRequest = { stopAndDiscard() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            AppNameLabel()

            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                CameraToggles(
                    camera = camera,
                    isTorchOn = isTorchOn,
                    onTorchChanged = { isTorchOn = it },
                    onFlip = { lensFacing = oppositeLens(lensFacing) }.takeIf { canFlip },
                    // Switching lens mid-recording would need a persistent recording; out of scope.
                    flipEnabled = !isRecording,
                )
                RecordControls(
                    isRecording = isRecording,
                    elapsedSeconds = elapsedSeconds,
                    onCancel = { stopAndDiscard() },
                    onToggleRecording = { if (isRecording) recording?.stop() else startRecording() },
                )
            }
        }
    }
}

/**
 * Reports the outcome of a finished recording: the [file] is handed back only if the user neither
 * cancelled nor hit a recording error - otherwise it's deleted, since nothing else will.
 */
private fun handleFinalize(
    event: VideoRecordEvent.Finalize,
    file: File,
    isCancelled: Boolean,
    onFinished: (File?) -> Unit,
) {
    // ERROR_SOURCE_INACTIVE (app backgrounded, e.g. an incoming call) still finalizes a valid,
    // playable file - keep it rather than throwing the user's clip away.
    val failed = event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE
    if (failed) {
        Log.e("JetImagePicker", "Video recording failed: ${event.error}")
    }
    if (isCancelled || failed) {
        file.delete()
        onFinished(null)
    } else {
        onFinished(file)
    }
}

@Composable
private fun BoxScope.AppNameLabel() {
    Text(
        text = "JetImagePicker",
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp),
    )
}

private fun oppositeLens(lensFacing: Int): Int =
    if (lensFacing == CameraSelector.LENS_FACING_BACK) {
        CameraSelector.LENS_FACING_FRONT
    } else {
        CameraSelector.LENS_FACING_BACK
    }

/**
 * Flash and camera-switch buttons. Flash is omitted when [camera]'s lens has no flash unit (most
 * front cameras); switching is omitted when [onFlip] is `null` (device lacks a front or back camera).
 */
@Composable
private fun CameraToggles(
    camera: Camera?,
    isTorchOn: Boolean,
    onTorchChanged: (Boolean) -> Unit,
    onFlip: (() -> Unit)?,
    flipEnabled: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            TextButton(onClick = {
                camera.cameraControl.enableTorch(!isTorchOn)
                onTorchChanged(!isTorchOn)
            }) {
                Text(if (isTorchOn) "Flash: on" else "Flash: off", color = Color.White)
            }
        }
        if (onFlip != null) {
            TextButton(onClick = onFlip, enabled = flipEnabled) {
                Text("Switch camera", color = if (flipEnabled) Color.White else Color.Gray)
            }
        }
    }
}

@Composable
private fun RecordControls(
    isRecording: Boolean,
    elapsedSeconds: Long,
    onCancel: () -> Unit,
    onToggleRecording: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color.White),
        ) {
            Text("Cancel")
        }

        Text(
            text = "${elapsedSeconds}s",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )

        // A red record/stop control is the universal camera-app convention, and stays visually
        // distinct from Cancel regardless of the host app's own theme colors.
        Button(
            onClick = onToggleRecording,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Text(if (isRecording) "Stop" else "Record")
        }
    }
}
