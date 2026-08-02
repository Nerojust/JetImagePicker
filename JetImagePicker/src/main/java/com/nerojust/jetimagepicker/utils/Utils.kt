package com.nerojust.jetimagepicker.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Internal file/image helpers backing [rememberImagePickerLauncher]. Not intended for direct use by consumers.
 */
object Utils {
    private const val MAX_QUALITY = 100

    /** Creates a new empty cache file for a camera capture and returns its [FileProvider] URI. */
    fun createImageUri(context: Context): Uri {
        val file =
            File(
                context.cacheDir,
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
                val bitmap =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }

                val targetWidth = config.targetWidth
                val targetHeight = config.targetHeight
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
                val file = File(context.cacheDir, "COMP_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
                FileOutputStream(file).use {
                    resized.compress(Bitmap.CompressFormat.JPEG, config.compressionQuality, it)
                }

                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * Writes [bitmap] to a new cache file as JPEG and returns its [FileProvider] URI.
     * Used to turn an in-memory cropped bitmap back into a [Uri] the rest of the pipeline expects.
     */
    fun writeBitmapToCache(
        context: Context,
        bitmap: Bitmap,
    ): Uri? =
        try {
            val file = File(context.cacheDir, "CROP_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, MAX_QUALITY, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            Log.e("JetImagePicker", "Failed to write cropped bitmap to cache", e)
            null
        }
}
