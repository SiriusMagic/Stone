package com.zirakusa.stone.nativeapp.automation

import com.zirakusa.stone.core.Song
import org.json.JSONArray
import org.json.JSONObject

/** Catalogue of Stone actions, grouped by domain: player, library, playlist, metadata, appearance, settings. */
internal object StoneActions {
    private fun host() = StoneAutomation.requireHost()
    private fun ok(message: String) = JSONObject().put("ok", true).put("message", message)

    private fun Song.json() = JSONObject().put("id", id).put("title", title).put("artist", artist)
        .put("album", album).put("genre", genre).put("folder", folder).put("durationMs", durationMs).put("isVideo", isVideo)

    private fun song(id: String): Song = host().songs.firstOrNull { it.id == id }
        ?: throw ActionException("No song with id '$id'. Use library_search to get valid ids.")

    private fun state(): JSONObject {
        val host = host(); val playback = host.playback; val (position, duration) = host.progress()
        return JSONObject().put("playing", playback.playing).put("title", playback.title).put("artist", playback.artist)
            .put("songId", playback.currentId).put("positionMs", position).put("durationMs", duration)
            .put("speed", playback.speed.toDouble()).put("volumePercent", playback.volumePercent)
            .put("maxVolumePercent", playback.audioMode.maxVolumePercent).put("audioMode", playback.audioMode.key)
            .put("queueSize", playback.queue.size).put("shuffle", playback.shuffle)
            .put("sleepMinutes", playback.sleepMinutes).put("sleepEndAtMs", playback.sleepEndAtMs)
    }

    fun all(): List<StoneAction> = listOf(
        // ---- player ----
        StoneAction("player.state", "Current playback state: song, position, speed, volume and audio mode.", ActionRisk.READ) { state() },
        StoneAction("player.play", "Resume playback of the current song.", ActionRisk.REVERSIBLE) {
            if (!host().playback.playing) host().toggle(); ok("Playing")
        },
        StoneAction("player.pause", "Pause playback.", ActionRisk.REVERSIBLE) {
            if (host().playback.playing) host().toggle(); ok("Paused")
        },
        StoneAction("player.next", "Skip to the next song in the queue.", ActionRisk.REVERSIBLE) { host().next(); ok("Next song") },
        StoneAction("player.previous", "Go back to the previous song.", ActionRisk.REVERSIBLE) { host().previous(); ok("Previous song") },
        StoneAction("player.seek", "Jump to a position of the current song.", ActionRisk.REVERSIBLE,
            listOf(ActionParam("positionSeconds", "number", "Target position in seconds from the start.", true))) { args ->
            host().seek((args.getDouble("positionSeconds") * 1000).toLong()); ok("Moved")
        },
        StoneAction("player.set_speed", "Set playback speed (0.5 to 2.0).", ActionRisk.REVERSIBLE,
            listOf(ActionParam("speed", "number", "Speed multiplier between 0.5 and 2.0.", true))) { args ->
            val speed = args.getDouble("speed").toFloat()
            if (speed !in 0.5f..2f) throw ActionException("speed must be between 0.5 and 2.0")
            host().setSpeed(speed); ok("Speed ${speed}x")
        },
        StoneAction("player.set_volume", "Set the volume in percent. 0-100 is Android's volume; above 100 only up to the audio mode's maximum.",
            ActionRisk.REVERSIBLE, listOf(ActionParam("percent", "integer", "Volume percent.", true))) { args ->
            val max = host().playback.audioMode.maxVolumePercent
            val percent = args.getInt("percent")
            if (percent !in 0..max) throw ActionException("percent must be between 0 and $max in the current audio mode")
            host().setVolume(percent); ok("Volume $percent%")
        },
        StoneAction("player.play_song", "Play a song or video by id. The rest of the library becomes the queue.", ActionRisk.REVERSIBLE,
            listOf(ActionParam("songId", "string", "Id returned by library_search.", true))) { args ->
            val target = song(args.getString("songId"))
            host().play(target, host().songs.filter { it.isVideo == target.isVideo }); ok("Playing ${target.title}")
        },
        StoneAction("player.queue_next", "Insert a song right after the current one.", ActionRisk.REVERSIBLE,
            listOf(ActionParam("songId", "string", "Id returned by library_search.", true))) { args ->
            val target = song(args.getString("songId")); host().playNext(listOf(target)); ok("${target.title} plays next")
        },
        StoneAction("player.sleep_timer", "Set the sleep timer: pause after N minutes (0 turns it off).", ActionRisk.REVERSIBLE, listOf(
            ActionParam("minutes", "integer", "Minutes until pause; 0 cancels.", true),
            ActionParam("finishTrack", "boolean", "Let the playing song end before pausing (default false)."),
            ActionParam("fadeOut", "boolean", "Lower the volume gradually during the last 30 seconds (default true)."))) { args ->
            val minutes = args.getInt("minutes")
            if (minutes !in 0..1439) throw ActionException("minutes must be between 0 and 1439")
            host().setSleepTimer(minutes, args.optBoolean("finishTrack", false), args.optBoolean("fadeOut", true))
            ok(if (minutes == 0) "Sleep timer off" else "Pausing in $minutes min")
        },
        StoneAction("player.sleep_timer_extend", "Add minutes to the running sleep timer.", ActionRisk.REVERSIBLE,
            listOf(ActionParam("minutes", "integer", "Minutes to add (1-180).", true))) { args ->
            val minutes = args.getInt("minutes")
            if (minutes !in 1..180) throw ActionException("minutes must be between 1 and 180")
            host().extendSleepTimer(minutes); ok("Added $minutes min")
        },
        // ---- library ----
        StoneAction("library.search", "Search songs and videos by title, artist, album or folder. Returns ids for other tools.",
            ActionRisk.READ, listOf(
                ActionParam("query", "string", "Text to look for. Empty lists everything."),
                ActionParam("kind", "string", "Restrict to audio or video.", options = listOf("audio", "video", "any")),
                ActionParam("limit", "integer", "Maximum results (default 25, max 200)."))) { args ->
            val query = args.optString("query")
            val kind = args.optString("kind", "any")
            val limit = args.optInt("limit", 25).coerceIn(1, 200)
            val matches = host().songs.asSequence()
                .filter { kind == "any" || (kind == "video") == it.isVideo }
                .filter { query.isBlank() || it.matches(query) }.toList()
            JSONObject().put("total", matches.size)
                .put("results", JSONArray(matches.take(limit).map { it.json() }))
        },
        StoneAction("library.stats", "Counts of songs, videos, artists, albums and playlists.", ActionRisk.READ) {
            val songs = host().songs
            JSONObject().put("songs", songs.count { !it.isVideo }).put("videos", songs.count { it.isVideo })
                .put("artists", songs.map { it.artist }.filter { it.isNotBlank() }.distinct().size)
                .put("albums", songs.map { it.album }.filter { it.isNotBlank() }.distinct().size)
                .put("playlists", host().playlists.size)
        },
        StoneAction("library.rescan", "Scan the device and folders again for new or removed files.", ActionRisk.REVERSIBLE) {
            host().refreshLibrary(); ok("Scan started")
        },
        // ---- playlists ----
        StoneAction("playlist.list", "List playlists with their ids and song counts.", ActionRisk.READ) {
            JSONObject().put("playlists", JSONArray(host().playlists.map {
                JSONObject().put("id", it.id).put("name", it.name).put("songs", it.songIds.size)
            }))
        },
        StoneAction("playlist.create", "Create an empty playlist.", ActionRisk.REVERSIBLE,
            listOf(ActionParam("name", "string", "Playlist name.", true))) { args ->
            val name = args.getString("name").trim()
            if (name.isEmpty()) throw ActionException("name must not be empty")
            host().createPlaylist(name); ok("Playlist '$name' created. Use playlist_list to get its id.")
        },
        StoneAction("playlist.add_songs", "Add songs to a playlist.", ActionRisk.REVERSIBLE, listOf(
            ActionParam("playlistId", "string", "Id from playlist_list.", true),
            ActionParam("songIds", "string", "Comma separated song ids from library_search.", true))) { args ->
            val playlist = host().playlists.firstOrNull { it.id == args.getString("playlistId") }
                ?: throw ActionException("No playlist with that id. Use playlist_list.")
            val targets = args.getString("songIds").split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(::song)
            targets.forEach { host().addToPlaylist(playlist.id, it) }
            ok("${targets.size} song(s) added to '${playlist.name}'")
        },
    ) + StoneMediaActions.all() + StoneMetadataActions.all() + StoneSettingsActions.all() + StoneLookActions.all() + StoneGuide.action
}
