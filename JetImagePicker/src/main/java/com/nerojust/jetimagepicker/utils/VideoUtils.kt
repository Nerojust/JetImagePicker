package com.nerojust.jetimagepicker.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
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
    /** Creates a new empty cache file for a video capture and returns its [FileProvider] URI. */
    fun createVideoUri(context: Context): Uri {
        val file =
            File(
                context.cacheDir,
                "VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp4",
            )
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    /** Reads the duration of the video at [uri] in whole seconds, or `null` if it can't be read. */
    fun getVideoDurationSeconds(
        context: Context,
        uri: Uri,
    ): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
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

    /** Extracts a representative frame from the video at [uri] and caches it as a JPEG. */
    fun extractVideoThumbnail(
        context: Context,
        uri: Uri,
    ): Uri? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val bitmap = retriever.frameAtTime ?: return null
            Utils.writeBitmapToCache(context, bitmap, filenamePrefix = "THUMB_VIDEO")
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
     */
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
                transformer.start(MediaItem.fromUri(uri), outputFile.absolutePath)
                continuation.invokeOnCancellation { transformer.cancel() }
            }
        }
}
