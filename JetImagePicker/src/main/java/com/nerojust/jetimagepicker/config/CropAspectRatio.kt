package com.nerojust.jetimagepicker.config

/**
 * Constrains the crop region shown when [JetImagePickerConfig.enableCrop] is true.
 */
sealed class CropAspectRatio {
    /** No constraint — the user can freely resize the crop region. */
    data object Free : CropAspectRatio()

    /** 1:1 — the classic avatar/profile-picture crop. */
    data object Square : CropAspectRatio()

    /** An arbitrary width:height ratio, e.g. `Custom(16f, 9f)` for 16:9. */
    data class Custom(val ratioX: Float, val ratioY: Float) : CropAspectRatio()
}

/** The numeric width/height ratio for this [CropAspectRatio], or `null` if unconstrained. */
fun CropAspectRatio.toRatioOrNull(): Float? =
    when (this) {
        is CropAspectRatio.Free -> null
        is CropAspectRatio.Square -> 1f
        is CropAspectRatio.Custom -> ratioX / ratioY
    }
