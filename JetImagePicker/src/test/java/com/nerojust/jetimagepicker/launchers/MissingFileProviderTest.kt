package com.nerojust.jetimagepicker.launchers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nerojust.jetimagepicker.utils.Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MissingFileProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun imageCaptureUri_missingProvider_throwsActionableSetupError() {
        val error = runCatching { Utils.createImageUri(context) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("${context.packageName}.provider"))
    }

    @Test
    fun missingProviderMessage_pointsToConsumerAuthority() {
        assertEquals(
            "No FileProvider for \"${context.packageName}.provider\" - see the README setup",
            missingFileProviderMessage(context),
        )
    }
}
