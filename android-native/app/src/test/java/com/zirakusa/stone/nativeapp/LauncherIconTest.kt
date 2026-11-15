package com.zirakusa.stone.nativeapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.zirakusa.stone.nativeapp.ui.theme.LauncherIcon
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stone must always have exactly one home-screen entry, whatever state Android kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherIconTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun launcherEntries(): List<String> = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
        .map { it.activityInfo.name.substringAfterLast('.') }

    @Test fun freshInstallShowsOnlyThePenguin() {
        assertEquals(listOf("Home"), launcherEntries())
    }

    @Test fun choosingAMascotKeepsOneEntry() {
        LauncherIcon.apply(context, LauncherIcon.FOX)
        assertEquals(listOf("HomeFox"), launcherEntries())
        LauncherIcon.apply(context, LauncherIcon.PENGUIN)
        assertEquals(listOf("Home"), launcherEntries())
    }

    @Test fun startUpRestoresAnEntryWhenAllWereDisabled() {
        LauncherIcon.entries.forEach {
            context.packageManager.setComponentEnabledSetting(ComponentName(context, "com.zirakusa.stone.nativeapp.${it.alias}"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        LauncherIcon.load(context)
        assertEquals(listOf("Home"), launcherEntries())
    }
}
