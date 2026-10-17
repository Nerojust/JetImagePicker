package com.nerojust.jetimagepicker.config

/**
 * Configuration for [rememberJetVideoPickerState] and [rememberVideoPickerLauncher].
 *
 * @property enableCompression If true, picked/captured video is re-encoded (via Media3
 * Transformer) before the result callback fires.
 * @property enableThumbnail If true, a thumbnail frame is extracted and its [android.net.Uri]
 * included in [com.nerojust.jetimagepicker.result.VideoPickerResult.Success.thumbnailUri].
 * @property durationLimitSeconds Optional max video duration. Enforced live during camera
 * capture and re-checked defensively afterward for both capture and gallery pick.
 * @property enableTrim If true, a trim screen is shown automatically after pick/capture (for
 * both sources), before the duration check — letting the user cut the video down, including to
 * rescue one that's over [durationLimitSeconds]. Cancelling the trim screen cancels the whole
 * pick/capture, mirroring how cancelling the image picker's crop step works.
 */
data class JetVideoPickerConfig(
    val enableCompression: Boolean = true,
    val enableThumbnail: Boolean = true,
    val durationLimitSeconds: Int? = null,
    val enableTrim: Boolean = false,
)
