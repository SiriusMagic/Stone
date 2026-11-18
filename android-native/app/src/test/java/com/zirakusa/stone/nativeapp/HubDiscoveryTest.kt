package com.zirakusa.stone.nativeapp

import android.content.Intent
import android.content.pm.PackageManager
import com.zirakusa.stone.nativeapp.automation.HubDiscovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Gateway Hub app's scanner finds Stone through its manifest, and opening it only shows Modo API. */
@RunWith(RobolectricTestRunner::class)
class HubDiscoveryTest {
    @Test fun stoneDeclaresItselfAsTheMusicModule() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val found = context.packageManager.queryIntentActivities(
            Intent(HubDiscovery.ACTION).addCategory(Intent.CATEGORY_DEFAULT).setPackage(context.packageName), PackageManager.GET_META_DATA)
        val activity = found.single().activityInfo
        assertEquals(MainActivity::class.java.name, activity.name)
        assertEquals("music_", activity.metaData.getString(HubDiscovery.META_PREFIX))
    }

    @Test fun onlyTheHubActionOpensApiSettings() {
        HubDiscovery.consumed()
        HubDiscovery.handle(Intent(Intent.ACTION_MAIN))
        assertFalse(HubDiscovery.openApiSettings)
        HubDiscovery.handle(Intent(HubDiscovery.ACTION).putExtra("anything", "ignored"))
        assertTrue(HubDiscovery.openApiSettings)
        HubDiscovery.consumed()
        assertFalse(HubDiscovery.openApiSettings)
    }
}
