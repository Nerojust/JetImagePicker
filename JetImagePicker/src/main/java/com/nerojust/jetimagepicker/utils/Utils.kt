// File: utils/Utils.kt
package com.nerojust.jetimagepicker.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import com.nerojust.jetimagepicker.config.JetImagePickerConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Utils {

    /**
     * Compress an image from its [Uri] and return the file pointing to the compressed image.
     */
    fun createImageUri(context: Context): Uri {
        val file = File(
            context.cacheDir,
            "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
        )
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    fun compressImage(
        context: Context,
        uri: Uri,
        config: JetImagePickerConfig
    ): Uri? = try {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }

        val resized = config.targetWidth?.let { width ->
            config.targetHeight?.let { height ->
                bitmap.scale(width, height)
            }
        } ?: bitmap

        val file = File(context.cacheDir, "COMP_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use {
            resized.compress(Bitmap.CompressFormat.JPEG, config.compressionQuality, it)
        }

        Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
