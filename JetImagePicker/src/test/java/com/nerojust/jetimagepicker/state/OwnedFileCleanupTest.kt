package com.nerojust.jetimagepicker.state

import android.app.Activity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OwnedFileCleanupTest {
    @Test
    fun `files are kept when the system destroys a backgrounded activity`() {
        // Not finishing, not a config change: the restored selection still needs its files.
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        assertFalse(shouldDeleteOwnedFilesOnDestroy(activity))
    }

    @Test
    fun `files are deleted when the user finishes the activity`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.finish()

        assertTrue(shouldDeleteOwnedFilesOnDestroy(activity))
    }

    @Test
    fun `no activity means nothing is deleted`() {
        assertFalse(shouldDeleteOwnedFilesOnDestroy(null))
    }
}
