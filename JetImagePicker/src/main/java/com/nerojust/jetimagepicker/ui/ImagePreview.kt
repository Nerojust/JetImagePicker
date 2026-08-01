// File: ui/ImagePreview.kt
package com.nerojust.jetimagepicker.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

/**
 * Displays a single selected image at a fixed 250dp height, cropped to fill the width.
 */
@Composable
fun ImagePreview(
    uri: Uri,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Selected image",
) {
    val painter = rememberAsyncImagePainter(model = uri)

    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(4.dp)
                .clip(MaterialTheme.shapes.medium)
                .border(1.dp, Color.LightGray, MaterialTheme.shapes.medium),
    )
}
