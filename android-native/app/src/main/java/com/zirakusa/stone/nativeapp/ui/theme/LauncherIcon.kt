package com.zirakusa.stone.nativeapp.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.zirakusa.stone.nativeapp.R

/**
 * Home-screen icon variants. Each one is an `activity-alias` in the manifest with its own
 * adaptive icon (penguin over a coloured background); Stone enables exactly one of them.
 */
enum class LauncherIcon(val key: String, val alias: String, val color: Color, val label: Int) {
    DEFAULT("default", "Launcher", Color(0xFF0E0E12), R.string.launcher_original),
    GOLD("gold", "LauncherGold", Color(0xFFF5C518), R.string.color_gold),
    ORANGE("orange", "LauncherOrange", Color(0xFFFF8A3D), R.string.color_orange),
    BLUE("blue", "LauncherBlue", Color(0xFF2F7FD6), R.string.color_blue),
    TEAL("teal", "LauncherTeal", Color(0xFF1FA9AD), R.string.color_teal),
    GREEN("green", "LauncherGreen", Color(0xFF2E9E5B), R.string.color_green),
    RED("red", "LauncherRed", Color(0xFFD63A4A), R.string.color_red),
    PURPLE("purple", "LauncherPurple", Color(0xFF7E57C2), R.string.color_purple),
    PINK("pink", "LauncherPink", Color(0xFFE05A9A), R.string.color_pink),
    WHITE("white", "LauncherWhite", Color(0xFFF2F2F5), R.string.color_white),
    COFFEE("coffee", "LauncherCoffee", Color(0xFF8B5E3C), R.string.color_coffee);

    companion object {
        /** The icon currently enabled, read from the package manager the first time. */
        var current by mutableStateOf(DEFAULT)
            private set

        fun from(key: String) = entries.firstOrNull { it.key == key } ?: DEFAULT

        fun load(context: Context) {
            val manager = context.packageManager
            current = entries.firstOrNull { icon ->
                manager.getComponentEnabledSetting(icon.component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } ?: DEFAULT
        }

        /**
         * Enables [icon] and disables the others without killing the app. Launchers refresh the
         * icon on their own; some take a few seconds or need the home screen to be reopened.
         */
        fun apply(context: Context, icon: LauncherIcon) {
            val manager = context.packageManager
            // Enable the new one first so the app always has a launcher entry.
            manager.setComponentEnabledSetting(icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            entries.filter { it != icon }.forEach {
                manager.setComponentEnabledSetting(it.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
            current = icon
        }

        private fun LauncherIcon.component(context: Context) =
            ComponentName(context, "com.zirakusa.stone.nativeapp.$alias")
    }
}
