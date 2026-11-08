package com.nerojust.jetimagepicker.launchers

import android.annotation.SuppressLint
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import com.nerojust.jetimagepicker.config.JetImagePickerConfig

/** Whether a gallery pick uses the multi-select picker for this [config]. */
internal fun usesMultiPicker(config: JetImagePickerConfig): Boolean = config.allowMultiple && config.maxSelection != 1

/**
 * The limit to hand the multi-select contract, which rejects anything below 2 or above the
 * system picker's own maximum.
 */
internal fun pickerMaxItems(
    maxSelection: Int,
    systemLimit: Int,
): Int = maxSelection.coerceIn(2, maxOf(2, systemLimit))

/** The first [max] items, or all of them when there is no limit. */
internal fun <T> List<T>.limitedTo(max: Int?): List<T> = if (max == null) this else take(max)

/** The most items Android's Photo Picker lets one request ask for, or no limit where it isn't available. */
internal fun systemPickLimit(): Int = runCatching { platformPickLimit() }.getOrDefault(Int.MAX_VALUE)

@SuppressLint("NewApi") // getPickImagesMaxLimit exists exactly where the check below says it does.
private fun platformPickLimit(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
        (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2
        )
    ) {
        MediaStore.getPickImagesMaxLimit()
    } else {
        Int.MAX_VALUE
    }
