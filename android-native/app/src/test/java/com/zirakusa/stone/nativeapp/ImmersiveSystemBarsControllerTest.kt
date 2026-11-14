package com.zirakusa.stone.nativeapp

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.zirakusa.stone.nativeapp.ui.ImmersiveSystemBarsController
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImmersiveSystemBarsControllerTest {
    private lateinit var activity: ComponentActivity
    private lateinit var controller: ImmersiveSystemBarsController

    @Before fun setUp() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        controller = ImmersiveSystemBarsController(activity)
        controller.attach()
        controller.onResume()
        controller.onWindowFocusChanged(true)
        controller.setImmersiveEnabled(true)
    }

    @After fun tearDown() {
        controller.dispose()
        activity.finish()
    }

    @Test fun transientBarsShownBySystemGestureAreNotImmediatelyHiddenAgain() {
        dispatchInsets(systemBarsVisible = false)
        assertFalse(controller.systemBarsVisible)

        dispatchInsets(systemBarsVisible = true)
        controller.onWindowFocusChanged(true)

        assertTrue(controller.systemBarsVisible)
    }

    @Test fun resumeAndConfigurationReapplyTheRequestedModeWithoutAConstantLoop() {
        dispatchInsets(systemBarsVisible = false)
        controller.onStop()
        assertTrue(controller.systemBarsVisible)

        controller.onResume()
        controller.onWindowFocusChanged(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(controller.systemBarsVisible)

        dispatchInsets(systemBarsVisible = true)
        controller.onConfigurationChanged()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(controller.systemBarsVisible)
    }

    @Test fun disablingImmersiveRestoresBarsAndKeepsImeInsetsUnconsumed() {
        dispatchInsets(systemBarsVisible = false)
        controller.setImmersiveEnabled(false)
        assertTrue(controller.systemBarsVisible)

        val insets = WindowInsetsCompat.Builder()
            .setVisible(WindowInsetsCompat.Type.systemBars(), true)
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        val returned = ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, insets)
        assertSame(insets, returned)
        assertTrue(controller.imeVisible)
        assertTrue(controller.systemBarsVisible)
    }

    private fun dispatchInsets(systemBarsVisible: Boolean) {
        val insets = WindowInsetsCompat.Builder()
            .setVisible(WindowInsetsCompat.Type.systemBars(), systemBarsVisible)
            .setVisible(WindowInsetsCompat.Type.ime(), false)
            .build()
        ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, insets)
    }
}
