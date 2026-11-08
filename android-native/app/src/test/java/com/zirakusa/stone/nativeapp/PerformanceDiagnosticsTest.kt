package com.zirakusa.stone.nativeapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zirakusa.stone.nativeapp.playback.PerformanceDiagnostics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerformanceDiagnosticsTest {
    @Test fun sessionReturnsSamplesAndNotifiesOnTheCallingThread() = runBlocking {
        val caller = Thread.currentThread()
        val delivered = mutableListOf<Long>()
        val summary = PerformanceDiagnostics.session(
            ApplicationProvider.getApplicationContext<Context>(), durationMs = 25, intervalMs = 5
        ) { sample ->
            assertEquals(caller, Thread.currentThread())
            delivered.add(sample.timestampMs)
        }
        assertTrue(delivered.isNotEmpty())
        assertEquals(delivered, summary.samples.map { it.timestampMs })
    }
}
