package com.nerojust.jetimagepicker.result

import android.net.Uri

/**
 * Result of a video pick/capture operation, delivered via [rememberJetVideoPickerState]'s
 * `onResult` callback.
 */
sealed class VideoPickerResult {
    /**
     * A video was picked/captured, or the operation was cancelled.
     *
     * @property uri The resulting video, or `null` if the pick/capture was cancelled.
     * @property thumbnailUri The extracted thumbnail frame, or `null` if
     * [com.nerojust.jetimagepicker.config.JetVideoPickerConfig.enableThumbnail] is false, or [uri]
     * is null.
     */
    data class Success(val uri: Uri?, val thumbnailUri: Uri?) : VideoPickerResult()

    /**
     * [uri]'s duration exceeds the configured [limitSeconds]; no [Success] was returned for it.
     *
     * @property uri The over-long video. **For a camera capture this file is deleted immediately
     * after this result is delivered** — it is the library's own temporary capture, so read any
     * metadata you need (e.g. via `MediaMetadataRetriever`) synchronously inside the callback and
     * do not persist the uri for later use. For a gallery-picked video the uri stays valid: it
     * isn't owned by this library and is never deleted. When
     * [com.nerojust.jetimagepicker.config.JetVideoPickerConfig.enableTrim] is on and the trimmed
     * result is still over the limit, [uri] is a library-owned trimmed copy that is likewise
     * deleted immediately after this callback returns — do not hold onto it either.
     * @property limitSeconds The configured
     * [com.nerojust.jetimagepicker.config.JetVideoPickerConfig.durationLimitSeconds] that was exceeded.
     */
    data class DurationExceeded(val uri: Uri, val limitSeconds: Int) : VideoPickerResult()

    /**
     * One or more permissions required for camera capture (`CAMERA`, `RECORD_AUDIO`) were not
     * granted. The three lists are independent — a given permission appears in exactly one of
     * them, since [android.permission] states are mutually exclusive per permission.
     *
     * @property denied Permissions denied this round; still re-requestable.
     * @property permanentlyDenied Permissions denied with "Don't ask again"; direct the user to
     * app settings.
     * @property shouldShowRationaleFor Permissions the OS recommends showing a rationale for
     * before requesting again.
     */
    data class PermissionsRequired(
        val denied: List<String>,
        val permanentlyDenied: List<String>,
        val shouldShowRationaleFor: List<String>,
    ) : VideoPickerResult()
}
