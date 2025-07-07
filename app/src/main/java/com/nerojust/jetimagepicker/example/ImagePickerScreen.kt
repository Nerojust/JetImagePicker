package com.nerojust.jetimagepicker.example

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import com.nerojust.jetimagepicker.result.ImagePickerResult
import com.nerojust.jetimagepicker.state.rememberJetImagePickerState
import com.nerojust.jetimagepicker.ui.ImagePreview
import com.nerojust.jetimagepicker.ui.MultiImagePreview

@Composable
fun ImagePickerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    val pickerState = rememberJetImagePickerState(
        context = context,
        config = JetImagePickerConfig(
            enableCompression = true,
            compressionQuality = 70,
            allowMultiple = false,
            targetWidth = 1024,
            targetHeight = 1024
        )
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


    Column(modifier = modifier.padding(16.dp)) {
        Button(
            onClick = pickerState.pickFromGallery,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pick from Gallery")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = pickerState.captureWithCamera,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Capture with Camera")
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (pickerState.selectedImageUris.size) {
            1 -> ImagePreview(uri = pickerState.selectedImageUris.first())
            in 2..Int.MAX_VALUE -> MultiImagePreview(imageUris = pickerState.selectedImageUris)
        }

        message?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
