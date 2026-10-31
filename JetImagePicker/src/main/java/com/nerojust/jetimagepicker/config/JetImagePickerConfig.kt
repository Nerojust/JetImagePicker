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
 * @property maxSelection Optional cap on how many images the gallery picker
 * returns when [allowMultiple] is true. Passed to the system picker; on devices
 * whose picker ignores it, only the first [maxSelection] images are kept.
 * @property enableCrop If true, a crop step runs before compression whenever
 * exactly one image was picked/captured (camera capture always; gallery pick
 * only when a single image was selected, unless [cropMultiple] is true).
 * @property cropAspectRatio The aspect ratio the crop region is constrained to
 * when [enableCrop] is true.
 * @property cropMultiple If true (and [enableCrop] is true), every image in a
 * multi-select is cropped one after another. Cancelling any crop cancels the
 * whole pick.
 * @property outputFormat The file format compressed images are written in.
 */
data class JetImagePickerConfig(
    val enableCompression: Boolean = true,
    // 0–100
    val compressionQuality: Int = 75,
    // Optional resizing
    val targetWidth: Int? = null,
    val targetHeight: Int? = null,
    val allowMultiple: Boolean = true,
    val enableCrop: Boolean = false,
    val cropAspectRatio: CropAspectRatio = CropAspectRatio.Free,
    val maxSelection: Int? = null,
    val cropMultiple: Boolean = false,
    val outputFormat: ImageOutputFormat = ImageOutputFormat.JPEG,
) {
    init {
        require(targetWidth == null || targetWidth > 0) { "targetWidth must be greater than 0, was $targetWidth" }
        require(targetHeight == null || targetHeight > 0) { "targetHeight must be greater than 0, was $targetHeight" }
        require(maxSelection == null || maxSelection >= 1) { "maxSelection must be at least 1, was $maxSelection" }
    }
}
