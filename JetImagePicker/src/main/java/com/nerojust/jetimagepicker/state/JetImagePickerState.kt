package com.nerojust.jetimagepicker.state

import android.net.Uri
import androidx.compose.runtime.State

/**
 * Observable state for a single image picker instance, returned by [rememberJetImagePickerState].
 *
 * @property selectedImageUris All currently selected/captured image URIs.
 * @property selectedImageUri The first URI in [selectedImageUris], or null if nothing is selected.
 * @property isLoading True while picked images are being compressed.
 * @property pickFromGallery Launches the gallery picker (Photo Picker on supported devices).
 * @property captureWithCamera Launches the system camera to capture a new photo.
 * @property clearSelection Resets [selectedImageUris]/[selectedImageUri] to empty.
 */
class JetImagePickerState internal constructor(
    private val _selectedImageUris: State<List<Uri>>,
    private val _selectedImageUri: State<Uri?>,
    private val _isLoading: State<Boolean>,
    val pickFromGallery: () -> Unit,
    val captureWithCamera: () -> Unit,
    val clearSelection: () -> Unit,
) {
    val selectedImageUris: List<Uri> get() = _selectedImageUris.value
    val selectedImageUri: Uri? get() = _selectedImageUri.value
    val isLoading: Boolean get() = _isLoading.value
}
