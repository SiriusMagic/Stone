package com.zirakusa.stone.nativeapp.data.persistence

import com.zirakusa.stone.nativeapp.ui.theme.StoneThemeSpec
import android.content.Context
import org.json.JSONArray
import com.zirakusa.stone.core.LandscapeLayoutMode

data class StoneSettings(
    val theme: String = "dark",
    val language: String = "es",
    val playWithOthers: Boolean = false,
    val gapless: Boolean = true,
    val crossfade: Boolean = false,
    val crossfadeSeconds: Int = 5,
    val notifyNewFiles: Boolean = false,
    val pushNotifications: Boolean = false,
    val sleepMinutes: Int = 0,
    val audioMode: String = "balanced",
    val autoRescan: Boolean = true,
    /** Index every song/video on the device through MediaStore (needs media permission). */
    val deviceScan: Boolean = true,
    val hardwareAcceleration: String = "automatic",
    val musicReminders: Boolean = false,
    val landscapeLayoutMode: LandscapeLayoutMode = LandscapeLayoutMode.ALWAYS,
    val videoScale: String = "auto",
    val videoLibraryLayout: String = "grid",
    /** One global policy for every Stone screen and player window. */
    val immersiveEnabled: Boolean = true,
    val shakeToReport: Boolean = true,
)

class StoneSettingsStore(context: Context) {
    private val db = StoneDatabase.get(context)
    fun read() = StoneSettings(
        theme = db.setting("theme", "dark").takeIf { it in StoneThemeSpec.keys } ?: "dark",
        language = db.setting("language", "es").takeIf { it in setOf("es", "en") } ?: "es",
        playWithOthers = db.setting("playWithOthers", "false").toBoolean(),
        gapless = db.setting("gapless", "true").toBoolean(),
        crossfade = db.setting("crossfade", "false").toBoolean(),
        crossfadeSeconds = db.setting("crossfadeSeconds", "5").toIntOrNull()?.coerceIn(0, 12) ?: 5,
        notifyNewFiles = db.setting("notifyNewFiles", "false").toBoolean(),
        pushNotifications = db.setting("pushNotifications", "false").toBoolean(),
        sleepMinutes = db.setting("sleepMinutes", "0").toIntOrNull()?.coerceAtLeast(0) ?: 0,
        audioMode = db.setting("audioMode", "balanced")
            .takeIf { it in setOf("battery", "balanced", "hifi") } ?: "balanced",
        autoRescan = db.setting("autoRescan", "true").toBoolean(),
        deviceScan = db.setting("deviceScan", "true").toBoolean(),
        hardwareAcceleration = db.setting("hardwareAcceleration", "automatic")
            .takeIf { it in setOf("automatic", "disabled", "decoding", "full", "adaptive") } ?: "automatic",
        musicReminders = db.setting("musicReminders", "false").toBoolean(),
        landscapeLayoutMode = LandscapeLayoutMode.fromKey(db.setting("landscapeLayoutMode", "always")),
        videoScale = db.setting("videoScale", "auto")
            .takeIf { it in setOf("auto", "fit", "zoom", "fill") } ?: "auto",
        videoLibraryLayout = db.setting("videoLibraryLayout", "grid")
            .takeIf { it in setOf("grid", "list") } ?: "grid",
        immersiveEnabled = db.setting("immersiveMode", legacyImmersiveDefault(db)).toBoolean(),
        shakeToReport = db.setting("shakeToReport", "true").toBoolean(),
    )
    fun setTheme(value: String) = db.setSetting("theme", value.takeIf { it in StoneThemeSpec.keys } ?: "dark")
    fun setLandscapeLayoutMode(value: LandscapeLayoutMode) = db.setSetting("landscapeLayoutMode", value.key)
    fun setLanguage(value: String) = db.setSetting("language", value.takeIf { it in setOf("es", "en") } ?: "es")
    fun setBoolean(key: String, value: Boolean) = db.setSetting(key, value.toString())
    fun shortcutVisible(key: String, fallback: Boolean = true) =
        db.setting("home.shortcut.$key", fallback.toString()).toBoolean()
    fun setShortcutVisible(key: String, value: Boolean) =
        db.setSetting("home.shortcut.$key", value.toString())
    fun customShortcutVisible(key: String, fallback: Boolean = true) =
        db.setting("home.shortcut.custom.$key", fallback.toString()).toBoolean()
    fun setCustomShortcutVisible(key: String, value: Boolean) =
        db.setSetting("home.shortcut.custom.$key", value.toString())
    fun shortcutOrder(defaults: List<String>): List<String> {
        val saved = runCatching { JSONArray(db.setting("home.shortcut.order", "[]")).let { array ->
            List(array.length()) { array.getString(it) }
        } }.getOrDefault(emptyList())
        return (saved + defaults).distinct().filter { it in defaults }
    }
    fun setShortcutOrder(order: List<String>) = db.setSetting("home.shortcut.order", JSONArray(order.distinct()).toString())
    fun shortcutProfile() = db.setting("home.shortcut.profile", "custom")
        .takeIf { it in setOf("minimal", "basic", "audiophile", "custom") } ?: "custom"
    fun setShortcutProfile(profile: String) = db.setSetting("home.shortcut.profile",
        profile.takeIf { it in setOf("minimal", "basic", "audiophile", "custom") } ?: "custom")
    fun setCrossfadeSeconds(value: Int) = db.setSetting("crossfadeSeconds", value.coerceIn(0, 12).toString())
    fun setSleepMinutes(value: Int) = db.setSetting("sleepMinutes", value.coerceAtLeast(0).toString())
    fun setAudioMode(value: String) = db.setSetting("audioMode",
        value.takeIf { it in setOf("battery", "balanced", "hifi") } ?: "balanced")
    fun setHardwareAcceleration(value: String) = db.setSetting("hardwareAcceleration",
        value.takeIf { it in setOf("automatic", "disabled", "decoding", "full", "adaptive") } ?: "automatic")
    fun setVideoScale(value: String) = db.setSetting("videoScale",
        value.takeIf { it in setOf("auto", "fit", "zoom", "fill") } ?: "auto")
    fun setVideoLibraryLayout(value: String) = db.setSetting("videoLibraryLayout",
        value.takeIf { it in setOf("grid", "list") } ?: "grid")
    fun setDeviceScan(value: Boolean) = db.setSetting("deviceScan", value.toString())
    fun setAutoRescan(value: Boolean) = db.setSetting("autoRescan", value.toString())
    /** True once Stone asked for the media permission on first launch. */
    fun mediaPermissionAsked() = db.setting("mediaPermissionAsked", "false").toBoolean()
    fun setMediaPermissionAsked() = db.setSetting("mediaPermissionAsked", "true")
    fun setImmersiveEnabled(value: Boolean) = db.setSetting("immersiveMode", value.toString())
    fun setShakeToReport(value: Boolean) = db.setSetting("shakeToReport", value.toString())
    fun videoControlVisible(key: String, fallback: Boolean = true) =
        db.setting("video.control.$key", fallback.toString()).toBoolean()
    fun setVideoControlVisible(key: String, value: Boolean) =
        db.setSetting("video.control.$key", value.toString())
    fun videoControlOrder(defaults: List<String>): List<String> {
        val saved = runCatching { JSONArray(db.setting("video.control.order", "[]")).let { array ->
            List(array.length()) { array.getString(it) }
        } }.getOrDefault(emptyList())
        return (saved + defaults).distinct().filter { it in defaults }
    }
    fun setVideoControlOrder(order: List<String>) =
        db.setSetting("video.control.order", JSONArray(order.distinct()).toString())
    fun lastVideoPreviewId() = db.setting("video.lastPreviewId", "")
    fun setLastVideoPreviewId(id: String) = db.setSetting("video.lastPreviewId", id)

    private fun legacyImmersiveDefault(database: StoneDatabase): String {
        // Migrate the two retired switches without changing a user's previous
        // choice. New installs keep the former player default (enabled).
        val players = database.setting("immersivePlayers", "true").toBoolean()
        val library = database.setting("immersiveLibrary", "false").toBoolean()
        return (players || library).toString()
    }
}
