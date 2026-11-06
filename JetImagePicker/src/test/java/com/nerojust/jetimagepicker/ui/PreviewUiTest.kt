package com.nerojust.jetimagepicker.ui

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PreviewUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `single preview exposes its description`() {
        composeRule.setContent {
            ImagePreview(uri = Uri.parse("content://test/image"), contentDescription = "Preview")
        }

        composeRule.onAllNodesWithContentDescription("Preview").assertCountEquals(1)
    }

    @Test
    fun `multi preview renders every selected image`() {
        composeRule.setContent {
            MultiImagePreview(
                imageUris = listOf(Uri.parse("content://test/one"), Uri.parse("content://test/two")),
                contentDescription = "Selected",
            )
        }

        composeRule.onAllNodesWithContentDescription("Selected").assertCountEquals(2)
    }
}
