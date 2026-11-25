package com.zirakusa.stone.nativeapp.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.stone.nativeapp.R

/**
 * Stone's mascots. The one chosen in Personalizado is shown inside the app (header, rail,
 * about) and as the home-screen icon. Each mascot is an `activity-alias` in the manifest with
 * its own adaptive icon; Stone keeps exactly one of them enabled.
 *
 * Alias names must never change: Android keeps the enabled state per name, so renaming or
 * removing one can leave the app with no launcher entry after an update.
 */
enum class LauncherIcon(val key: String, val alias: String, val drawable: Int, val label: Int) {
    PENGUIN("penguin", "Home", R.drawable.mascot_penguin, R.string.mascot_penguin),
    PANDA("panda", "HomePanda", R.drawable.mascot_panda, R.string.mascot_panda),
    KOALA("koala", "HomeKoala", R.drawable.mascot_koala, R.string.mascot_koala),
    // «HomeFox» keeps its name (Android remembers aliases by name) but now shows the otter.
    OTTER("otter", "HomeFox", R.drawable.mascot_otter, R.string.mascot_otter);

    companion object {
        /** The mascot currently in use, read from the package manager at start-up. */
        var current by mutableStateOf(PENGUIN)
            private set

        fun from(key: String) = entries.firstOrNull { it.key == key } ?: PENGUIN

        /**
         * Reads which alias is enabled. If none is (for example after updating from a build that
         * had other icons), the penguin is enabled again so Stone never loses its launcher entry.
         */
        fun load(context: Context) {
            val enabled = entries.firstOrNull { it.isEnabled(context) }
            if (enabled == null) apply(context, PENGUIN) else current = enabled
        }

        /**
         * Enables [icon] and disables the others without killing the app. Launchers refresh the
         * icon on their own; some take a few seconds or need the home screen to be reopened.
         */
        fun apply(context: Context, icon: LauncherIcon) {
            val manager = context.packageManager
            // Enable the new one first so the app always has a launcher entry.
            runCatching { manager.setComponentEnabledSetting(icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP) }
            entries.filter { it != icon }.forEach {
                runCatching { manager.setComponentEnabledSetting(it.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP) }
            }
            current = icon
        }

        private fun LauncherIcon.isEnabled(context: Context): Boolean {
            val state = runCatching { context.packageManager.getComponentEnabledSetting(component(context)) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
            // DEFAULT means "as declared in the manifest": only the penguin alias starts enabled.
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && this == PENGUIN)
        }

        private fun LauncherIcon.component(context: Context) =
            ComponentName(context, "com.zirakusa.stone.nativeapp.$alias")
    }
}
