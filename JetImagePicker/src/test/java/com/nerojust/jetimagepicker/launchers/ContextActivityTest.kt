package com.nerojust.jetimagepicker.launchers

import android.app.Activity
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContextActivityTest {
    @Test
    fun `direct activity is found`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        assertSame(activity, activity.findActivity())
    }

    @Test
    fun `activity behind nested wrappers is found`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val wrapped = ContextThemeWrapper(ContextThemeWrapper(activity, 0), 0)

        assertSame(activity, wrapped.findActivity())
    }

    @Test
    fun `non-activity context returns null`() {
        assertNull(ApplicationProvider.getApplicationContext<android.content.Context>().findActivity())
    }
}
