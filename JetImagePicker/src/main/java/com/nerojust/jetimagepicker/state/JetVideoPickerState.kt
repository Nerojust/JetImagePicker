package com.nerojust.jetimagepicker.state

import android.net.Uri
import androidx.compose.runtime.State

/**
 * Observable state for a single video picker instance, returned by [rememberJetVideoPickerState].
 *
 * @property selectedVideoUri The currently selected/captured video, or null if nothing is selected.
 * @property isLoading True while picked/captured video is being compressed.
 * @property pickFromGallery Launches the gallery picker (Photo Picker, video-only) on supported devices).
 * @property captureWithCamera Launches the system camera to record a new video.
 * @property clearSelection Resets [selectedVideoUri] to null.
 */
class JetVideoPickerState internal constructor(
    private val _selectedVideoUri: State<Uri?>,
    private val _isLoading: State<Boolean>,
    val pickFromGallery: () -> Unit,
    val captureWithCamera: () -> Unit,
    val clearSelection: () -> Unit,
) {
    val selectedVideoUri: Uri? get() = _selectedVideoUri.value
    val isLoading: Boolean get() = _isLoading.value
}
