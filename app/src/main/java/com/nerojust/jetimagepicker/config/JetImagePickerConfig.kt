// File: config/JetImagePickerConfig.kt
package com.nerojust.jetimagepicker.config

data class JetImagePickerConfig(
    val enableCompression: Boolean = true,
    val compressionQuality: Int = 75, // 0–100
    val targetWidth: Int? = null,     // Optional resizing
    val targetHeight: Int? = null,
    val allowMultiple: Boolean = true // For future extensibility
)
