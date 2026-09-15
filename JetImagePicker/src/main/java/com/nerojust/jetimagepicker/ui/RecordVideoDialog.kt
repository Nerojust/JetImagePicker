package com.nerojust.jetimagepicker.ui

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nerojust.jetimagepicker.utils.VideoUtils
import kotlinx.coroutines.delay
import java.io.File

/**
 * Full-screen in-app video recording dialog, shown automatically by
 * [com.nerojust.jetimagepicker.launchers.rememberVideoPickerLauncher] when
 * `captureWithCamera()` is called. Records via CameraX so [durationLimitSeconds] is enforced
 * live, rather than depending on the system camera app to honor a request it can ignore.
 *
 * @param onFinished Called with the recorded [File] on success, or `null` if the user cancelled,
 * the camera failed to bind, or recording finalized with an error (all treated the same way).
 */
@Composable
internal fun RecordVideoDialog(
    durationLimitSeconds: Int?,
    onFinished: (File?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    val recorder = remember { Recorder.Builder().build() }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
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

    LaunchedEffect(Unit) {
        runCatching {
            val provider = ProcessCameraProvider.awaitInstance(context)
            cameraProvider = provider
            val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, videoCapture)
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
            // ponytail: does not await Finalize if the composable is torn down mid-recording
            // for a reason other than the Cancel button (e.g. the whole picker leaving
            // composition) - the async Finalize callback below may still fire onFinished after
            // disposal in that edge case. Acceptable for v1; revisit if it causes issues.
            recording?.stop()
            cameraProvider?.unbindAll()
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000)
            elapsedSeconds += 1
            if (VideoUtils.shouldStopRecording(elapsedSeconds, durationLimitSeconds)) {
                recording?.stop()
            }
        }
    }

    fun startRecording() {
        isCancelled = false
        elapsedSeconds = 0L
        val file = VideoUtils.createVideoFile(context)
        runCatching {
            recorder
                .prepareRecording(context, FileOutputOptions.Builder(file).build())
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        isRecording = false
                        recording = null
                        when {
                            isCancelled -> {
                                if (file.exists()) file.delete()
                                onFinished(null)
                            }
                            event.hasError() -> {
                                Log.e("JetImagePicker", "Video recording failed: ${event.error}")
                                if (file.exists()) file.delete()
                                onFinished(null)
                            }
                            else -> onFinished(file)
                        }
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

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(onClick = { stopAndDiscard() }) {
                    Text("Cancel")
                }

                Text(
                    text = "${elapsedSeconds}s",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )

                Button(onClick = { if (isRecording) recording?.stop() else startRecording() }) {
                    Text(if (isRecording) "Stop" else "Record")
                }
            }
        }
    }
}
