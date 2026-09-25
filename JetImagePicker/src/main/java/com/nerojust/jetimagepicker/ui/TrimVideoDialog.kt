// File: ui/TrimVideoDialog.kt
package com.nerojust.jetimagepicker.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nerojust.jetimagepicker.utils.VideoUtils

private const val MILLIS_PER_SECOND_DISPLAY = 1000f
private const val MIN_TRIM_LENGTH_MS = 1000L

/**
 * Full-screen video trim dialog, shown automatically by
 * [com.nerojust.jetimagepicker.launchers.rememberVideoPickerLauncher] after pick/capture when
 * `JetVideoPickerConfig.enableTrim` is true. Lets the user pick a `[startMs, endMs]` sub-range of
 * [uri] via a range slider, with a static frame preview at each handle's current position.
 *
 * @param onConfirm Called with the chosen trim range once the user confirms.
 * @param onCancel Called if the user cancels, or if the video's duration can't be read at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrimVideoDialog(
    uri: Uri,
    onConfirm: (startMs: Long, endMs: Long) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var durationMs by remember { mutableStateOf<Long?>(null) }
    var range by remember { mutableStateOf(0f..1f) }
    var committedRange by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    var startPreview by remember { mutableStateOf<Bitmap?>(null) }
    var endPreview by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        val duration = VideoUtils.getVideoDurationMillis(context, uri)
        if (duration == null || duration <= 0) {
            onCancel()
            return@LaunchedEffect
        }
        if (duration < MIN_TRIM_LENGTH_MS) {
            onConfirm(0L, duration)
            return@LaunchedEffect
        }
        durationMs = duration
        range = 0f..duration.toFloat()
        committedRange = range
    }

    LaunchedEffect(committedRange?.start) {
        committedRange?.let { startPreview = VideoUtils.extractFramePreview(context, uri, it.start.toLong()) }
    }

    LaunchedEffect(committedRange?.endInclusive) {
        committedRange?.let { endPreview = VideoUtils.extractFramePreview(context, uri, it.endInclusive.toLong()) }
    }

    val duration = durationMs ?: return

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        TrimDialogContent(
            range = range,
            onRangeChange = { value, committed ->
                range = value
                if (committed) committedRange = value
            },
            durationMs = duration,
            previews = FramePreviews(startPreview, endPreview),
            actions = TrimActions(onCancel, onConfirm),
        )
    }
}

/** Bundles the two live frame previews together, just to keep [TrimDialogContent]'s param count down. */
private data class FramePreviews(val start: Bitmap?, val end: Bitmap?)

/** Bundles the dialog's two exit callbacks together, just to keep [TrimDialogContent]'s param count down. */
private class TrimActions(
    val onCancel: () -> Unit,
    val onConfirm: (startMs: Long, endMs: Long) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrimDialogContent(
    range: ClosedFloatingPointRange<Float>,
    onRangeChange: (value: ClosedFloatingPointRange<Float>, committed: Boolean) -> Unit,
    durationMs: Long,
    previews: FramePreviews,
    actions: TrimActions,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Trim Video", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            FramePreviewRow(startPreview = previews.start, endPreview = previews.end)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text =
                    "%.1fs – %.1fs".format(
                        range.start / MILLIS_PER_SECOND_DISPLAY,
                        range.endInclusive / MILLIS_PER_SECOND_DISPLAY,
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )

            RangeSlider(
                value = range,
                onValueChange = { onRangeChange(it, false) },
                onValueChangeFinished = { onRangeChange(range, true) },
                valueRange = 0f..durationMs.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            TrimActionButtons(
                startMs = range.start.toLong(),
                endMs = range.endInclusive.toLong(),
                durationMs = durationMs,
                onCancel = actions.onCancel,
                onConfirm = actions.onConfirm,
            )
        }
    }
}

@Composable
private fun FramePreviewRow(
    startPreview: Bitmap?,
    endPreview: Bitmap?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        FramePreview(bitmap = startPreview, label = "Start")
        FramePreview(bitmap = endPreview, label = "End")
    }
}

@Composable
private fun TrimActionButtons(
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    onCancel: () -> Unit,
    onConfirm: (startMs: Long, endMs: Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(onClick = onCancel) {
            Text("Cancel")
        }
        Button(
            onClick = { onConfirm(startMs, endMs) },
            enabled = VideoUtils.isValidTrimRange(startMs, endMs, durationMs),
        ) {
            Text("Confirm")
        }
    }
}

@Composable
private fun FramePreview(
    bitmap: Bitmap?,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(modifier = Modifier.size(120.dp)) {
            bitmap?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = "$label frame preview")
            }
        }
    }
}
