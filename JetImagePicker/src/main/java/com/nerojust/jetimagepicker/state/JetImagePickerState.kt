package com.nerojust.jetimagepicker.state

import android.net.Uri
import androidx.compose.runtime.State

class JetImagePickerState internal constructor(
    private val _selectedImageUris: State<List<Uri>>,
    private val _selectedImageUri: State<Uri?>,
    val pickFromGallery: () -> Unit,
    val captureWithCamera: () -> Unit
) {
    val selectedImageUris: List<Uri> get() = _selectedImageUris.value
    val selectedImageUri: Uri? get() = _selectedImageUri.value
}
