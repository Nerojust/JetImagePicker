package com.nerojust.jetimagepicker.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

private const val MILLIS_PER_SECOND = 1000L

/**
 * Internal video file/metadata helpers backing [rememberVideoPickerLauncher]. Not intended
 * for direct use by consumers.
 */
object VideoUtils {
    /** Creates a new empty cache file for a CameraX video recording. */
    fun createVideoFile(context: Context): File =
        File(
            context.cacheDir,
            "VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp4",
        )

    /**
     * Reads the duration of the video at [uri] in whole seconds, or `null` if it can't be read.
     * Runs on [Dispatchers.IO] — [MediaMetadataRetriever] reads the file and can block for a
     * noticeable time on a large video.
     */
    suspend fun getVideoDurationSeconds(
        context: Context,
        uri: Uri,
    ): Long? =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.let { it / MILLIS_PER_SECOND }
            } catch (e: Exception) {
                Log.e("JetImagePicker", "Failed to read video duration", e)
                null
            } finally {
                retriever.release()
            }
        }

    /** True if [durationSeconds] is known and exceeds [limitSeconds]; false if either is null. */
    fun isDurationExceeded(
        durationSeconds: Long?,
        limitSeconds: Int?,
    ): Boolean {
        if (durationSeconds == null || limitSeconds == null) return false
        return durationSeconds > limitSeconds
    }

    /**
     * True the instant [elapsedSeconds] reaches [limitSeconds] during a live recording; false if
     * no limit is configured. Distinct from [isDurationExceeded]: this uses `>=` to stop the
     * recording the moment the limit is hit, while [isDurationExceeded] deliberately uses `>` on
     * an already-finished video's duration so a video exactly at the limit isn't flagged.
     */
    fun shouldStopRecording(
        elapsedSeconds: Long,
        limitSeconds: Int?,
    ): Boolean {
        if (limitSeconds == null) return false
        return elapsedSeconds >= limitSeconds
    }

    /**
     * True when the raw [source] video is the library's own camera capture and has been superseded
     * by a different [output] (or by no output at all, `null`), making it safe to delete. Never
     * true for a gallery pick — that uri belongs to the caller — and never true when [output] IS
     * [source], since that is the uri handed back to the caller.
     *
     * Generic so it stays a pure, unit-testable function (`Uri` can't be constructed in a plain
     * JVM unit test); both call sites pass `Uri`.
     */
    fun <T> shouldDeleteSource(
        isCameraCapture: Boolean,
        source: T,
        output: T?,
    ): Boolean = isCameraCapture && output != source

    /**
     * Extracts a representative frame from the video at [uri] and caches it as a JPEG.
     * Runs on [Dispatchers.IO] — decoding a frame blocks for a noticeable time on a large video.
     */
    suspend fun extractVideoThumbnail(
        context: Context,
        uri: Uri,
    ): Uri? =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val bitmap = retriever.frameAtTime
                bitmap?.let { Utils.writeBitmapToCache(context, it, filenamePrefix = "THUMB_VIDEO") }
            } catch (e: Exception) {
                Log.e("JetImagePicker", "Failed to extract video thumbnail", e)
                null
            } finally {
                retriever.release()
            }
        }

    /**
     * Re-encodes the video at [uri] via Media3 Transformer, writing the result to a cache file
     * exposed via [FileProvider]. Suspends until the export completes or fails.
     *
     * Every Transformer symbol used below is `@UnstableApi`: opting in is a real commitment, since
     * Media3 can change these signatures in a minor version bump - the version is pinned, and this
     * function must be re-checked on every Media3 upgrade.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    suspend fun compressVideo(
        context: Context,
        uri: Uri,
    ): Uri? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val outputFile =
                    File(context.cacheDir, "COMP_VIDEO_${System.currentTimeMillis()}_${UUID.randomUUID()}.mp4")
                val transformer =
                    Transformer.Builder(context)
                        // Without an explicit target codec, Transformer takes a lossless
                        // remux/copy fast path whenever the input is already a supported
                        // format - producing a same-size "compressed" file. Forcing H.264
                        // guarantees an actual decode+encode pass.
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .addListener(
                            object : Transformer.Listener {
                                override fun onCompleted(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                ) {
                                    val resultUri =
                                        FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            outputFile,
                                        )
                                    if (continuation.isActive) continuation.resume(resultUri)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException,
                                ) {
                                    Log.e("JetImagePicker", "Video compression failed", exportException)
                                    if (continuation.isActive) continuation.resume(null)
                                }
                            },
                        )
                        .build()
                // start() can throw synchronously (bad uri, bad output path) - that would escape
                // the coroutine uncaught, so treat it like the async onError path.
                val startFailure =
                    runCatching {
                        transformer.start(MediaItem.fromUri(uri), outputFile.absolutePath)
                    }.exceptionOrNull()
                if (startFailure != null) {
                    Log.e("JetImagePicker", "Failed to start video compression", startFailure)
                    if (continuation.isActive) continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
                continuation.invokeOnCancellation { transformer.cancel() }
            }
        }
}
