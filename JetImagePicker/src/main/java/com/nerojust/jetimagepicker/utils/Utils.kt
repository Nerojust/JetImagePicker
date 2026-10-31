package com.nerojust.jetimagepicker.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.nerojust.jetimagepicker.config.ImageOutputFormat
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Internal file/image helpers backing [rememberImagePickerLauncher]. Not intended for direct use by consumers.
 */
object Utils {
    private const val MAX_QUALITY = 100
    internal const val STALE_PICKER_CACHE_AGE_MILLIS = 24L * 60 * 60 * 1000
    private const val PICKER_CACHE_DIR = "jetimagepicker"

    /**
     * The folder every library-created file lives in. Keeping them out of the cache root means
     * cleanup can never touch the host app's own cache files.
     */
    internal fun pickerCacheDir(context: Context): File = File(context.cacheDir, PICKER_CACHE_DIR).apply { mkdirs() }

    /** Removes abandoned picker outputs left behind when Android kills the process without lifecycle callbacks. */
    internal fun pruneStalePickerCache(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeMillis: Long = STALE_PICKER_CACHE_AGE_MILLIS,
    ): Int {
        require(maxAgeMillis >= 0) { "maxAgeMillis must not be negative" }
        val cutoff = nowMillis - maxAgeMillis
        return pickerCacheDir(context).listFiles()
            ?.asSequence()
            ?.filter { file -> file.isFile && file.lastModified() <= cutoff }
            ?.count { file -> file.delete() }
            ?: 0
    }

    /** Creates a new empty cache file for a camera capture and returns its [FileProvider] URI. */
    fun createImageUri(context: Context): Uri {
        val file =
            File(
                pickerCacheDir(context),
                "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg",
            )
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    fun calculateScaledDimensions(
        srcWidth: Int,
        srcHeight: Int,
        maxWidth: Int,
        maxHeight: Int,
    ): Pair<Int, Int> {
        if (srcWidth <= 0 || srcHeight <= 0) return srcWidth to srcHeight

        val widthRatio = maxWidth.toFloat() / srcWidth
        val heightRatio = maxHeight.toFloat() / srcHeight
        val scale = minOf(widthRatio, heightRatio, 1f)

        val newWidth = (srcWidth * scale).toInt().coerceAtLeast(1)
        val newHeight = (srcHeight * scale).toInt().coerceAtLeast(1)
        return newWidth to newHeight
    }

    /**
     * The largest power-of-two decode sample size that still leaves the image at least as big as
     * the size it will be scaled to fit into [maxWidth] x [maxHeight] - so huge photos are shrunk
     * while decoding instead of after. Checks the box in both orientations, since EXIF rotation
     * may swap the image's width and height after decoding.
     */
    fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        maxWidth: Int,
        maxHeight: Int,
    ): Int {
        // Unknown dimensions (e.g. an undecodable file reports -1) - let the decoder fail normally.
        val hasValidSource = srcWidth > 0 && srcHeight > 0
        if (!hasValidSource || maxWidth <= 0 || maxHeight <= 0) return 1
        val (width1, height1) = calculateScaledDimensions(srcWidth, srcHeight, maxWidth, maxHeight)
        val (width2, height2) = calculateScaledDimensions(srcWidth, srcHeight, maxHeight, maxWidth)
        val requiredWidth = maxOf(width1, width2)
        val requiredHeight = maxOf(height1, height2)
        var sampleSize = 1
        while (srcWidth / (sampleSize * 2) >= requiredWidth && srcHeight / (sampleSize * 2) >= requiredHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * Decodes [uri], downsampled while decoding when a target box is given, and rotated upright
     * per its EXIF orientation (ImageDecoder does this itself on API 28+; done by hand below that).
     */
    private fun decodeBitmap(
        context: Context,
        uri: Uri,
        maxWidth: Int?,
        maxHeight: Int?,
    ): Bitmap {
        fun sampleSizeFor(
            width: Int,
            height: Int,
        ) = if (maxWidth != null && maxHeight != null) calculateInSampleSize(width, height, maxWidth, maxHeight) else 1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.setTargetSampleSize(sampleSizeFor(info.size.width, info.size.height))
                }
            } catch (e: IOException) {
                // E.g. Android 9 rejects some sample sizes on EXIF-rotated JPEGs ("invalid scale").
                Log.w("JetImagePicker", "ImageDecoder failed for $uri, retrying with BitmapFactory", e)
            }
        }
        return decodeWithBitmapFactory(context, uri, ::sampleSizeFor)
    }

    private fun decodeWithBitmapFactory(
        context: Context,
        uri: Uri,
        sampleSizeFor: (width: Int, height: Int) -> Int,
    ): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded =
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("Could not decode $uri")
        // A broken EXIF block only costs the rotation, not the whole image.
        val exif = runCatching { resolver.openInputStream(uri)?.use { ExifInterface(it) } }.getOrNull()
        return if (exif == null) decoded else applyExifOrientation(decoded, exif)
    }

    private fun applyExifOrientation(
        bitmap: Bitmap,
        exif: ExifInterface,
    ): Bitmap {
        if (exif.rotationDegrees == 0 && !exif.isFlipped) return bitmap
        val matrix =
            Matrix().apply {
                // Mirror first: ExifInterface reports TRANSPOSE as 270 and TRANSVERSE as 90 degrees,
                // which only land correctly when applied after the flip.
                if (exif.isFlipped) postScale(-1f, 1f)
                postRotate(exif.rotationDegrees.toFloat())
            }
        val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (upright !== bitmap) bitmap.recycle()
        return upright
    }

    /**
     * Compresses (and optionally resizes) the image at [uri], writing the result
     * to a cache file exposed via [FileProvider]. Runs on [Dispatchers.IO].
     */
    suspend fun compressImage(
        context: Context,
        uri: Uri,
        config: JetImagePickerConfig,
    ): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val targetWidth = config.targetWidth
                val targetHeight = config.targetHeight
                val bitmap = decodeBitmap(context, uri, targetWidth, targetHeight)
                val resized =
                    if (targetWidth != null && targetHeight != null) {
                        val (scaledWidth, scaledHeight) =
                            calculateScaledDimensions(
                                bitmap.width,
                                bitmap.height,
                                targetWidth,
                                targetHeight,
                            )
                        bitmap.scale(scaledWidth, scaledHeight)
                    } else {
                        bitmap
                    }

                // A timestamp alone can collide when compressing several images in the same
                // millisecond (e.g. multi-select), silently overwriting one with another.
                val format = config.outputFormat
                val filename = "COMP_${System.currentTimeMillis()}_${UUID.randomUUID()}.${format.extension}"
                val file = File(pickerCacheDir(context), filename)
                FileOutputStream(file).use {
                    resized.compress(format.toCompressFormat(), config.compressionQuality.coerceIn(0, MAX_QUALITY), it)
                }

                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            } catch (e: Exception) {
                Log.e("JetImagePicker", "Failed to compress $uri", e)
                null
            } catch (e: OutOfMemoryError) {
                // Most likely a huge photo with no targetWidth/targetHeight to shrink it while decoding.
                Log.e("JetImagePicker", "Out of memory compressing $uri", e)
                null
            }
        }

    /**
     * Writes [bitmap] to a new cache file as JPEG and returns its [FileProvider] URI.
     * [filenamePrefix] distinguishes callers writing different kinds of images to cache
     * (e.g. cropped images vs. video thumbnails).
     */
    fun writeBitmapToCache(
        context: Context,
        bitmap: Bitmap,
        filenamePrefix: String = "CROP",
    ): Uri? =
        try {
            val filename = "${filenamePrefix}_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"
            val file = File(pickerCacheDir(context), filename)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, MAX_QUALITY, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            Log.e("JetImagePicker", "Failed to write bitmap to cache", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e("JetImagePicker", "Out of memory writing bitmap to cache", e)
            null
        }

    private fun ImageOutputFormat.toCompressFormat(): Bitmap.CompressFormat =
        when (this) {
            ImageOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ImageOutputFormat.WEBP ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    // Lossy below quality 100 on these versions.
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
        }
}
