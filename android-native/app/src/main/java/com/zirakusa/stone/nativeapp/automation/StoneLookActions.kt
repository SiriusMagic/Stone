package com.zirakusa.stone.nativeapp.automation

import com.zirakusa.stone.nativeapp.playback.AudioMode
import com.zirakusa.stone.nativeapp.ui.theme.LauncherIcon
import com.zirakusa.stone.nativeapp.ui.theme.StoneAccent
import com.zirakusa.stone.nativeapp.ui.theme.StoneInk
import com.zirakusa.stone.nativeapp.ui.theme.StoneLook
import com.zirakusa.stone.nativeapp.ui.theme.StoneThemeSpec
import org.json.JSONArray
import org.json.JSONObject

/** Appearance and settings actions: theme, Personalizado values, home icon, language, audio mode. */
internal object StoneLookActions {
    private fun host() = StoneAutomation.requireHost()
    private fun ok(message: String) = JSONObject().put("ok", true).put("message", message)
    private val inks = StoneInk.entries.map { it.key }
    private val layouts = listOf("grid", "list", "compact")

    fun all(): List<StoneAction> = listOf(
        StoneAction("appearance.get", "Current theme, accent, text/icon colours, sizes, app name, song layout, home icon, language and the options available for each.",
            ActionRisk.READ) {
            JSONObject().put("theme", host().theme).put("themes", JSONArray(StoneThemeSpec.entries.map { it.key }))
                .put("accent", StoneLook.accent.key).put("accentHex", StoneLook.hex(StoneLook.accentColor))
                .put("accents", JSONArray(StoneAccent.entries.map { it.key }))
                .put("textColor", StoneLook.textInk.key).put("iconColor", StoneLook.iconInk.key).put("colors", JSONArray(inks))
                .put("fontScale", StoneLook.fontScale.toDouble()).put("iconScale", StoneLook.iconScale.toDouble())
                .put("appTitle", StoneLook.appTitle).put("songLayout", StoneLook.songLayout).put("songLayouts", JSONArray(layouts))
                .put("launcherIcon", LauncherIcon.current.key).put("launcherIcons", JSONArray(LauncherIcon.entries.map { it.key }))
                .put("language", host().language).put("languages", JSONArray(SUPPORTED_LANGUAGES))
        },
        StoneAction("appearance.set_theme", "Change the background theme.", ActionRisk.REVERSIBLE,
            listOf(ActionParam("theme", "string", "Theme key.", true, StoneThemeSpec.entries.map { it.key }))) { args ->
            host().setTheme(args.getString("theme")); ok("Theme changed")
        },
        StoneAction("appearance.set_accent", "Change the accent colour used for highlights (a preset key).", ActionRisk.REVERSIBLE,
            listOf(ActionParam("accent", "string", "Accent key.", true, StoneAccent.entries.map { it.key }))) { args ->
            StoneLook.accent = StoneAccent.from(args.getString("accent")); StoneLook.persist(); ok("Accent changed")
        },
        StoneAction("appearance.set_look", "Change any Personalizado value. Omitted fields keep their value. " +
            "accentHex selects the free colour (#RRGGBB); textColor/iconColor take a colour key; scales are multipliers.",
            ActionRisk.REVERSIBLE, listOf(
                ActionParam("accent", "string", "Preset accent key.", options = StoneAccent.entries.map { it.key }),
                ActionParam("accentHex", "string", "Free accent colour, e.g. #3FA7FF."),
                ActionParam("textColor", "string", "Text colour key.", options = inks),
                ActionParam("iconColor", "string", "Icon colour key.", options = inks),
                ActionParam("fontScale", "number", "Text size 0.8 to 1.4."),
                ActionParam("iconScale", "number", "Icon size 0.8 to 1.5."),
                ActionParam("appTitle", "string", "Name shown inside Stone (max 30 chars, empty = default)."),
                ActionParam("songLayout", "string", "Songs list layout.", options = layouts),
                ActionParam("launcherIcon", "string", "Home-screen icon colour.", options = LauncherIcon.entries.map { it.key }))) { args ->
            applyLook(args); StoneLook.persist(); ok("Look updated")
        },
        StoneAction("appearance.reset_look", "Reset every Personalizado value to Stone's defaults (theme and home icon are kept).",
            ActionRisk.REVERSIBLE) { StoneLook.resetDefaults(); ok("Look reset") },
        StoneAction("settings.set_language", "Change the app language.", ActionRisk.REVERSIBLE,
            listOf(ActionParam("language", "string", "Language code.", true, SUPPORTED_LANGUAGES))) { args ->
            host().setLanguage(args.getString("language")); ok("Language changed")
        },
        StoneAction("settings.set_audio_mode", "Change the audio mode (battery, balanced, hifi = Cine, sleepy = Dormilón, custom).",
            ActionRisk.REVERSIBLE, listOf(ActionParam("mode", "string", "Mode key.", true, AudioMode.entries.map { it.key }))) { args ->
            host().setAudioMode(AudioMode.entries.first { it.key == args.getString("mode") }); ok("Audio mode changed")
        },
    )

    private fun applyLook(args: JSONObject) {
        if (args.has("accentHex")) {
            StoneLook.customAccent = StoneLook.parseHex(args.getString("accentHex"))
                ?: throw ActionException("accentHex must look like #RRGGBB")
            StoneLook.accent = StoneAccent.CUSTOM
        }
        if (args.has("accent")) StoneLook.accent = StoneAccent.from(args.getString("accent"))
        if (args.has("textColor")) StoneLook.textInk = StoneInk.from(args.getString("textColor"), StoneLook.textInk)
        if (args.has("iconColor")) StoneLook.iconInk = StoneInk.from(args.getString("iconColor"), StoneLook.iconInk)
        if (args.has("fontScale")) StoneLook.fontScale = args.getDouble("fontScale").toFloat().checked("fontScale", .8f..1.4f)
        if (args.has("iconScale")) StoneLook.iconScale = args.getDouble("iconScale").toFloat().checked("iconScale", .8f..1.5f)
        if (args.has("appTitle")) StoneLook.appTitle = args.getString("appTitle").trim().take(30)
        if (args.has("songLayout")) StoneLook.chooseSongLayout(args.getString("songLayout"))
        if (args.has("launcherIcon")) host().setLauncherIcon(args.getString("launcherIcon"))
    }

    private fun Float.checked(name: String, range: ClosedFloatingPointRange<Float>): Float {
        if (this !in range) throw ActionException("$name must be between ${range.start} and ${range.endInclusive}")
        return this
    }

    /** Languages Stone ships translations for. */
    val SUPPORTED_LANGUAGES = com.zirakusa.stone.nativeapp.data.persistence.StoneLanguages.all.map { it.first }
}
