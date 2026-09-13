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
     * isn't owned by this library and is never deleted.
     * @property limitSeconds The configured
     * [com.nerojust.jetimagepicker.config.JetVideoPickerConfig.durationLimitSeconds] that was exceeded.
     */
    data class DurationExceeded(val uri: Uri, val limitSeconds: Int) : VideoPickerResult()

    /** [permission] was denied for this request; it can still be requested again. */
    data class PermissionDenied(val permission: String) : VideoPickerResult()

    /** [permission] was permanently denied ("Don't ask again"); direct the user to app settings. */
    data class PermissionPermanentlyDenied(val permission: String) : VideoPickerResult()

    /** The OS recommends showing a rationale for [permission] before requesting it again. */
    data class ShowRationale(val permission: String) : VideoPickerResult()
}
