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
 * @property enableCrop If true, a crop step runs before compression whenever
 * exactly one image was picked/captured (camera capture always; gallery pick
 * only when a single image was selected).
 * @property cropAspectRatio The aspect ratio the crop region is constrained to
 * when [enableCrop] is true.
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
    val enableCrop: Boolean = false,
    val cropAspectRatio: CropAspectRatio = CropAspectRatio.Free,
)
