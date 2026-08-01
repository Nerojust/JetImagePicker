// File: config/JetImagePickerConfig.kt
package com.nerojust.jetimagepicker.config

/**
 * Configuration for [rememberJetImagePickerState] and [rememberImagePickerLauncher].
 *
 * @property enableCompression If true, picked/captured images are compressed
 * (and optionally resized) before the result callback fires.
 * @property compressionQuality JPEG quality (0-100) used when [enableCompression]
 * is true.
 * @property targetWidth Optional max width (px) to fit picked images into,
 * preserving aspect ratio. Requires [targetHeight] to also be set.
 * @property targetHeight Optional max height (px) to fit picked images into,
 * preserving aspect ratio. Requires [targetWidth] to also be set.
 * @property allowMultiple If true, the gallery picker allows selecting more than
 * one image.
 */
data class JetImagePickerConfig(
    val enableCompression: Boolean = true,
    // 0–100
    val compressionQuality: Int = 75,
    // Optional resizing
    val targetWidth: Int? = null,
    val targetHeight: Int? = null,
    // For future extensibility
    val allowMultiple: Boolean = true,
)
