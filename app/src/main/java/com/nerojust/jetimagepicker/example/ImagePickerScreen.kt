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
import com.nerojust.jetimagepicker.config.CropAspectRatio
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import com.nerojust.jetimagepicker.result.ImagePickerResult
import com.nerojust.jetimagepicker.state.rememberJetImagePickerState
import com.nerojust.jetimagepicker.ui.ImagePreview
import com.nerojust.jetimagepicker.ui.MultiImagePreview

private const val BYTES_PER_KB = 1024.0

private fun formatFileSize(bytes: Long): String {
    if (bytes < 0) return "unknown size"
    val kb = bytes / BYTES_PER_KB
    return if (kb < BYTES_PER_KB) "%.0f KB".format(kb) else "%.1f MB".format(kb / BYTES_PER_KB)
}

private fun modeLabel(
    enabled: Boolean,
    onLabel: String,
    offLabel: String,
) = if (enabled) onLabel else offLabel

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
fun ImagePickerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    // Toggle these to see the library switch modes live - single vs. multiple
    // selection, and compressed vs. original output.
    var allowMultiple by remember { mutableStateOf(false) }
    var enableCompression by remember { mutableStateOf(true) }
    var enableCrop by remember { mutableStateOf(false) }

    val pickerState =
        rememberJetImagePickerState(
            context = context,
            config =
                JetImagePickerConfig(
                    enableCompression = enableCompression,
                    compressionQuality = 70,
                    allowMultiple = allowMultiple,
                    targetWidth = 1024,
                    targetHeight = 1024,
                    enableCrop = enableCrop,
                    cropAspectRatio = CropAspectRatio.Square,
                ),
        ) { result ->
            when (result) {
                is ImagePickerResult.Success -> {
                    message = null
                    Log.d("ImagePicker", "Images selected: ${result.uris.size} URIs: ${result.uris}")
                }

                is ImagePickerResult.PermissionDenied -> {
                    message = "Permission denied: ${result.permission}"
                    Log.d("ImagePicker", "Permission denied: ${result.permission}")
                }

                is ImagePickerResult.PermissionPermanentlyDenied -> {
                    message = "Permission permanently denied: ${result.permission}. Enable in settings."
                    Log.d("ImagePicker", "Permission permanently denied: ${result.permission}")
                }

                is ImagePickerResult.ShowRationale -> {
                    message = "Please grant ${result.permission} permission to continue."
                    Log.d("ImagePicker", "Show rationale for permission: ${result.permission}")
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
        Text("JetImagePicker Demo", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Toggle the modes below, then pick or capture an image.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        LabeledSwitch(
            label = modeLabel(allowMultiple, "Multiple selection", "Single selection"),
            checked = allowMultiple,
            onCheckedChange = { allowMultiple = it },
        )

        LabeledSwitch(
            label = modeLabel(enableCompression, "Compression on", "Compression off"),
            checked = enableCompression,
            onCheckedChange = { enableCompression = it },
        )

        LabeledSwitch(
            label = modeLabel(enableCrop, "Crop to square on", "Crop to square off"),
            checked = enableCrop,
            onCheckedChange = { enableCrop = it },
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = pickerState.pickFromGallery,
            enabled = !pickerState.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Pick from Gallery")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = pickerState.captureWithCamera,
            enabled = !pickerState.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Capture with Camera")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = pickerState.clearSelection,
            enabled = pickerState.selectedImageUris.isNotEmpty(),
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

        val imageSizes =
            remember(pickerState.selectedImageUris) {
                pickerState.selectedImageUris.map { uri ->
                    val bytes =
                        context.contentResolver.openFileDescriptor(uri, "r")
                            ?.use { it.statSize } ?: -1L
                    formatFileSize(bytes)
                }
            }
        val sizeCaption = if (enableCompression) "compressed" else "original"

        when (pickerState.selectedImageUris.size) {
            1 -> {
                ImagePreview(uri = pickerState.selectedImageUris.first())
                Text(
                    text = "Size: ${imageSizes.first()} ($sizeCaption)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            in 2..Int.MAX_VALUE -> {
                MultiImagePreview(imageUris = pickerState.selectedImageUris)
                Text(
                    text = "Sizes: ${imageSizes.joinToString(", ")} ($sizeCaption)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
