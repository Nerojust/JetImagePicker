package com.nerojust.jetimagepicker.config

/**
 * Configuration for [rememberJetVideoPickerState] and [rememberVideoPickerLauncher].
 *
 * @property enableCompression If true, picked/captured video is re-encoded (via Media3
 * Transformer) before the result callback fires.
 * @property enableThumbnail If true, a thumbnail frame is extracted and its [android.net.Uri]
 * included in [com.nerojust.jetimagepicker.result.VideoPickerResult.Success.thumbnailUri].
 * @property durationLimitSeconds Optional max video duration. Enforced live during camera
 * capture (via the system camera app's own UI) and re-checked defensively afterward for both
 * capture and gallery pick, since some camera apps ignore the live limit.
 */
data class JetVideoPickerConfig(
    val enableCompression: Boolean = true,
    val enableThumbnail: Boolean = true,
    val durationLimitSeconds: Int? = null,
)
