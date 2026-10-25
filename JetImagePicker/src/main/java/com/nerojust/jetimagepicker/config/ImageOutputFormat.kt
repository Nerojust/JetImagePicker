package com.nerojust.jetimagepicker.config

/**
 * The file format compressed images are written in when [JetImagePickerConfig.enableCompression] is true.
 */
enum class ImageOutputFormat(val extension: String) {
    /** Universally supported. The default. */
    JPEG("jpg"),

    /** Lossy WebP — usually much smaller than JPEG for photos at the same quality setting. */
    WEBP("webp"),
}
