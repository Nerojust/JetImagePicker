// File: example/VideoPickerScreen.kt
package com.nerojust.jetimagepicker.example

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nerojust.jetimagepicker.config.JetVideoPickerConfig
import com.nerojust.jetimagepicker.result.VideoPickerResult
import com.nerojust.jetimagepicker.state.rememberJetVideoPickerState
import com.nerojust.jetimagepicker.ui.ImagePreview

private const val DEMO_DURATION_LIMIT_SECONDS = 30
private const val BYTES_PER_KB = 1024.0

private fun formatFileSize(bytes: Long): String {
    if (bytes < 0) return "unknown size"
    val kb = bytes / BYTES_PER_KB
    return if (kb < BYTES_PER_KB) "%.0f KB".format(kb) else "%.1f MB".format(kb / BYTES_PER_KB)
}

@Composable
private fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun VideoPickerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var thumbnailUri by remember { mutableStateOf<android.net.Uri?>(null) }

    var enableCompression by remember { mutableStateOf(true) }
    var enableThumbnail by remember { mutableStateOf(true) }
    var enforceDurationLimit by remember { mutableStateOf(false) }
    var enableTrim by remember { mutableStateOf(false) }

    val pickerState =
        rememberJetVideoPickerState(
            context = context,
            config =
                JetVideoPickerConfig(
                    enableCompression = enableCompression,
                    enableThumbnail = enableThumbnail,
                    durationLimitSeconds = if (enforceDurationLimit) DEMO_DURATION_LIMIT_SECONDS else null,
                    enableTrim = enableTrim,
                ),
        ) { result ->
            when (result) {
                is VideoPickerResult.Success -> {
                    message = null
                    thumbnailUri = result.thumbnailUri
                    Log.d("VideoPicker", "Video ready: ${result.uri}, thumbnail: ${result.thumbnailUri}")
                }

                is VideoPickerResult.DurationExceeded -> {
                    message = "Video is longer than ${result.limitSeconds}s limit."
                    thumbnailUri = null
                }

                is VideoPickerResult.PermissionsRequired -> {
                    message =
                        buildString {
                            if (result.denied.isNotEmpty()) {
                                append("Denied: ${result.denied.joinToString()}. ")
                            }
                            if (result.permanentlyDenied.isNotEmpty()) {
                                append("Permanently denied (enable in settings): ${result.permanentlyDenied.joinToString()}. ")
                            }
                            if (result.shouldShowRationaleFor.isNotEmpty()) {
                                append("Please grant: ${result.shouldShowRationaleFor.joinToString()}.")
                            }
                        }.trim()
                }
            }
        }

    Column(
        modifier =
            modifier
                .safeDrawingPadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text("JetVideoPicker Demo", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Toggle the modes below, then pick or capture a video.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        LabeledSwitch(
            label = if (enableCompression) "Compression on" else "Compression off",
            checked = enableCompression,
            onCheckedChange = {
                enableCompression = it
                pickerState.clearSelection()
                thumbnailUri = null
                message = null
            },
        )
        LabeledSwitch(
            label = if (enableThumbnail) "Thumbnail on" else "Thumbnail off",
            checked = enableThumbnail,
            onCheckedChange = { enableThumbnail = it },
        )
        LabeledSwitch(
            label = if (enforceDurationLimit) "Duration limit: ${DEMO_DURATION_LIMIT_SECONDS}s" else "No duration limit",
            checked = enforceDurationLimit,
            onCheckedChange = { enforceDurationLimit = it },
        )
        LabeledSwitch(
            label = if (enableTrim) "Trim on" else "Trim off",
            checked = enableTrim,
            onCheckedChange = { enableTrim = it },
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = pickerState.pickFromGallery,
            enabled = !pickerState.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Pick Video from Gallery")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = pickerState.captureWithCamera,
            enabled = !pickerState.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Capture Video with Camera")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                pickerState.clearSelection()
                thumbnailUri = null
                message = null
            },
            enabled = pickerState.selectedVideoUri != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear Selection")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (pickerState.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        thumbnailUri?.let { uri ->
            ImagePreview(uri = uri, contentDescription = "Video thumbnail")
        }

        pickerState.selectedVideoUri?.let { uri ->
            Text(
                text = "Video: $uri",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val videoSize =
                remember(uri) {
                    val bytes = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
                    formatFileSize(bytes)
                }
            val sizeCaption = if (enableCompression) "compressed" else "original"
            Text(
                text = "Size: $videoSize ($sizeCaption)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        message?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
