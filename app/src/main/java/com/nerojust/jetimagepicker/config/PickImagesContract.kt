package com.nerojust.jetimagepicker.config

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

class PickImagesContract(private val allowMultiple: Boolean) :
    ActivityResultContract<String, List<Uri>>() {

    override fun createIntent(context: Context, input: String): Intent {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = input
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
        }

        return Intent.createChooser(intent, if (allowMultiple) "Select Images" else "Select Image")
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        val result = mutableListOf<Uri>()
        if (intent == null) return result

        // Multiple
        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                result.add(clipData.getItemAt(i).uri)
            }
        } else {
            // Single
            intent.data?.let { result.add(it) }
        }

        return result
    }
}
