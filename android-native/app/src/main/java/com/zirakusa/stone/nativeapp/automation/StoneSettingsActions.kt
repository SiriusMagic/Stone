package com.zirakusa.stone.nativeapp.automation

import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.playback.AudioLevels
import com.zirakusa.stone.nativeapp.playback.AudioMode
import com.zirakusa.stone.nativeapp.playback.HardwareAcceleration
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Favourites by id, playlist management, audio levels and the remaining Ajustes switches. */
internal object StoneSettingsActions {
    private fun host() = StoneAutomation.requireHost()
    private fun ok(message: String) = JSONObject().put("ok", true).put("message", message)
    private fun store() = StoneSettingsStore(StonePlaybackService.instance?.applicationContext
        ?: throw ActionException("Playback service not running; open Stone."))
    private fun playlist(id: String) = host().playlists.firstOrNull { it.id == id }
        ?: throw ActionException("No playlist with that id. Use playlist_list.")
    private val adjustableKeys = AudioLevels.adjustable.map { it.key }
    private val accelerationKeys = HardwareAcceleration.entries.map { it.key }
    /** Simple on/off settings that are read from storage when used (some apply after reopening Stone). */
    private val options = listOf("autoRescan", "notifyNewFiles", "musicReminders", "shakeToReport")

    fun all(): List<StoneAction> = listOf(
        StoneAction("library.set_favorite", "Like / unlike any songs or videos by id (not only the one playing).",
            ActionRisk.REVERSIBLE, listOf(
                ActionParam("songIds", "string", "Comma separated ids from library_search.", true),
                ActionParam("favorite", "boolean", "true = like, false = remove from favourites.", true))) { args ->
            val known = host().songs.associateBy { it.id }
            val ids = args.getString("songIds").split(',').map { it.trim() }.filter { it.isNotEmpty() }
            ids.forEach { if (it !in known) throw ActionException("No song with id '$it'. Use library_search.") }
            ids.forEach { host().setFavorite(it, args.getBoolean("favorite")) }
            ok("${ids.size} item(s) ${if (args.getBoolean("favorite")) "added to" else "removed from"} favourites")
        },
        StoneAction("playlist.rename", "Rename a playlist.", ActionRisk.REVERSIBLE, listOf(
            ActionParam("playlistId", "string", "Id from playlist_list.", true),
            ActionParam("name", "string", "New name.", true))) { args ->
            val target = playlist(args.getString("playlistId"))
            val name = args.getString("name").trim().ifEmpty { throw ActionException("The name cannot be empty.") }
            host().renamePlaylist(target.id, name); ok("'${target.name}' renamed to '$name'")
        },
        StoneAction("playlist.delete", "Delete a playlist (songs and files stay). Needs approval on the phone.",
            ActionRisk.PERSISTENT, listOf(ActionParam("playlistId", "string", "Id from playlist_list.", true)),
            summary = { args -> "Borrar la lista «${host().playlists.firstOrNull { it.id == args.optString("playlistId") }?.name ?: "?"}»" }) { args ->
            val target = playlist(args.getString("playlistId"))
            host().deletePlaylist(target.id); ok("Playlist '${target.name}' deleted")
        },
        // ---- audio levels (Ajustes › Niveles de audio) ----
        StoneAction("audio.get_levels", "Volume ceiling (maxPercent) and software gain of each audio mode, and the active mode.",
            ActionRisk.READ) {
            JSONObject().put("activeMode", host().playback.audioMode.key).put("modes", JSONArray(AudioMode.entries.map { mode ->
                JSONObject().put("mode", mode.key).put("maxPercent", mode.maxVolumePercent)
                    .put("gainPercent", (mode.gain * 100).roundToInt()).put("adjustable", mode in AudioLevels.adjustable)
            }))
        },
        StoneAction("audio.set_level", "Change the volume ceiling and/or gain of an audio mode (Ajustes › Niveles de audio). " +
            "Omitted values keep their current setting.", ActionRisk.REVERSIBLE, listOf(
            ActionParam("mode", "string", "Audio mode.", true, adjustableKeys),
            ActionParam("maxPercent", "integer", "Maximum volume 10-300 %."),
            ActionParam("gainPercent", "integer", "Software gain 1-100 % (lower = quieter)."))) { args ->
            val mode = AudioLevels.adjustable.first { it.key == args.getString("mode") }
            val max = args.optInt("maxPercent", mode.maxVolumePercent)
            val gain = args.optInt("gainPercent", (mode.gain * 100).roundToInt())
            if (max !in 10..300) throw ActionException("maxPercent must be between 10 and 300.")
            if (gain !in 1..100) throw ActionException("gainPercent must be between 1 and 100.")
            AudioLevels.set(store(), mode, max, gain / 100f)
            if (host().playback.audioMode == mode) host().setAudioMode(mode)
            ok("${mode.key}: max $max %, gain $gain %")
        },
        StoneAction("audio.reset_levels", "Restore the default level of every audio mode.", ActionRisk.REVERSIBLE) {
            AudioLevels.reset(store()); host().setAudioMode(host().playback.audioMode); ok("Audio levels restored")
        },
        // ---- remaining settings ----
        StoneAction("settings.get", "All of Stone's settings: transitions, crossfade, hardware acceleration, audio mode, " +
            "play with other apps, library scanning, notifications…", ActionRisk.READ) {
            val s = store().read()
            JSONObject().put("theme", s.theme).put("language", s.language).put("audioMode", s.audioMode)
                .put("gapless", s.gapless).put("crossfade", s.crossfade).put("crossfadeSeconds", s.crossfadeSeconds)
                .put("hardwareAcceleration", s.hardwareAcceleration).put("playWithOthers", s.playWithOthers)
                .put("autoRescan", s.autoRescan).put("deviceScan", s.deviceScan).put("notifyNewFiles", s.notifyNewFiles)
                .put("musicReminders", s.musicReminders).put("shakeToReport", s.shakeToReport)
                .put("sleepFinishTrack", s.sleepFinishTrack).put("sleepFadeOut", s.sleepFadeOut)
                .put("videoScale", s.videoScale).put("videoLibraryLayout", s.videoLibraryLayout)
                .put("immersive", s.immersiveEnabled).put("landscapeLayout", s.landscapeLayoutMode.key)
        },
        StoneAction("settings.set_transitions", "Gapless playback and crossfade between songs. Omitted values stay.",
            ActionRisk.REVERSIBLE, listOf(
                ActionParam("gapless", "boolean", "Play without pauses between tracks."),
                ActionParam("crossfade", "boolean", "Blend the end of a song into the next."),
                ActionParam("crossfadeSeconds", "integer", "Crossfade length 1-12 s."))) { args ->
            val store = store(); val current = store.read()
            val gapless = args.optBoolean("gapless", current.gapless)
            val crossfade = args.optBoolean("crossfade", current.crossfade)
            val seconds = args.optInt("crossfadeSeconds", current.crossfadeSeconds)
            if (seconds !in 1..12) throw ActionException("crossfadeSeconds must be between 1 and 12.")
            store.setBoolean("gapless", gapless); store.setBoolean("crossfade", crossfade); store.setCrossfadeSeconds(seconds)
            StonePlaybackService.configureTransitions(gapless, crossfade, seconds)
            ok("gapless=$gapless, crossfade=$crossfade ($seconds s)")
        },
        StoneAction("settings.set_hardware_acceleration", "Hardware decoding policy for audio and video.", ActionRisk.REVERSIBLE,
            listOf(ActionParam("mode", "string", "Policy.", true, accelerationKeys))) { args ->
            host().setHardwareAcceleration(args.getString("mode")); ok("Hardware acceleration: ${args.getString("mode")}")
        },
        StoneAction("settings.set_play_with_others", "Keep playing when another app plays sound (true) or pause (false).",
            ActionRisk.REVERSIBLE, listOf(ActionParam("enabled", "boolean", "Mix with other apps.", true))) { args ->
            host().setPlayWithOthers(args.getBoolean("enabled")); ok("Play with other apps: ${args.getBoolean("enabled")}")
        },
        StoneAction("settings.set_option", "Turn a simple setting on or off: autoRescan (rescan on open), notifyNewFiles, " +
            "musicReminders, shakeToReport. Some apply the next time Stone opens.", ActionRisk.REVERSIBLE, listOf(
            ActionParam("option", "string", "Setting.", true, options),
            ActionParam("enabled", "boolean", "On or off.", true))) { args ->
            store().setBoolean(args.getString("option"), args.getBoolean("enabled"))
            ok("${args.getString("option")} = ${args.getBoolean("enabled")}")
        },
    )
}
