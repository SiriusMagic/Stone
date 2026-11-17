package com.zirakusa.stone.nativeapp.automation.actions

import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import org.json.JSONArray
import org.json.JSONObject

/** Queue, equalizer, video and library-view actions (added in 1.6.7). */
internal object StoneMediaActions {
    private fun host() = StoneAutomation.requireHost()
    private fun ok(message: String) = JSONObject().put("ok", true).put("message", message)
    private fun Song.json() = JSONObject().put("id", id).put("title", title).put("artist", artist)
        .put("album", album).put("durationMs", durationMs).put("isVideo", isVideo)
    private fun song(id: String): Song = host().songs.firstOrNull { it.id == id }
        ?: throw ActionException("No song with id '$id'. Use library_search to get valid ids.")

    val bandLabels = listOf("31Hz", "63Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")
    /** Preset key -> name shown in Stone's equalizer and its ten band values (-10..10). */
    val presets = linkedMapOf(
        "normal" to ("Normal" to List(10) { 0 }),
        "bass" to ("Aumento de graves" to listOf(5, 5, 3, 1, 0, 0, 0, 0, 0, 0)),
        "voice" to ("Aumento de voz" to listOf(0, 0, 0, 0, 1, 4, 3, 4, 0, 0)),
        "treble" to ("Aumento de agudos" to listOf(0, 0, 0, 0, 0, 1, 2, 2, 3, 5)),
        "pop" to ("Pop" to listOf(-1, -1, 0, 1, 1, 2, 2, 1, 0, -1)),
        "hiphop" to ("HipHop" to listOf(5, 5, 3, 0, -2, -2, 2, 3, 4, 4)),
        "rock" to ("Rock" to listOf(5, 3, 2, 0, 0, 1, 0, 2, 2, 4)),
        "electronic" to ("Electrónica" to listOf(4, 7, 6, -1, -2, 0, 5, 8, 3, 5)),
        "metal" to ("Heavy metal" to listOf(2, 4, 3, 1, 2, 3, 3, 2, 2, 0)),
        "rnb" to ("R&B" to listOf(5, 5, 4, 0, -2, -1, 2, 2, 2, 3)),
        "folk" to ("Folk" to listOf(0, 2, 3, 0, 0, 0, 6, 3, 2, -1)),
        "dance" to ("Baile" to listOf(5, 5, 4, 2, 1, 3, 4, 3, 2, 0)),
        "jazz" to ("Jazz" to listOf(3, 3, 2, 0, -1, -1, 0, 2, 3, 4)),
        "classical" to ("Clásico" to listOf(0, 1, -2, 0, -1, 0, 2, -2, -2, 3)),
        "latin" to ("Latín" to listOf(1, 3, 2, 0, 0, -1, 0, -1, 0, 3)))
    private val sortKeys = listOf("title", "artist", "album", "duration", "size", "mostplayed", "lyrics")
    private val repeatModes = listOf("off", "one", "all")

    private fun equalizer(): JSONObject {
        val playback = host().playback
        return JSONObject().put("enabled", playback.equalizerEnabled).put("preset", playback.equalizerPreset)
            .put("byGenre", playback.equalizerByGenre)
            .put("bands", JSONArray(playback.equalizerValues.mapIndexed { index, value ->
                JSONObject().put("band", index).put("frequency", bandLabels[index]).put("value", value) }))
            .put("range", "-10..10").put("presets", JSONArray(presets.keys))
    }

    private fun queueIndex(args: JSONObject, name: String): Int {
        val index = args.getInt(name); val size = host().playback.queue.size
        if (index !in 0 until size) throw ActionException("$name must be between 0 and ${size - 1} (see player_queue)")
        return index
    }

    fun all(): List<StoneAction> = listOf(
        // ---- queue ----
        StoneAction("player.queue", "List the playback queue in order. 'index' is what the other queue tools take; 'current' marks the song playing.",
            ActionRisk.READ, listOf(ActionParam("limit", "integer", "Maximum entries (default 50, max 300)."))) { args ->
            val playback = host().playback
            JSONObject().put("size", playback.queue.size).put("currentIndex", playback.index)
                .put("shuffle", playback.shuffle).put("repeat", repeatModes.getOrElse(playback.repeat) { "off" })
                .put("queue", JSONArray(playback.queue.take(args.optInt("limit", 50).coerceIn(1, 300)).mapIndexed { index, entry ->
                    JSONObject().put("index", index).put("songId", entry.id).put("title", entry.title)
                        .put("artist", entry.artist).put("current", index == playback.index) }))
        },
        StoneAction("player.queue_play", "Jump to a position of the queue and play it.", ActionRisk.REVERSIBLE,
            listOf(ActionParam("index", "integer", "Queue index from player_queue.", true))) { args ->
            host().playQueueIndex(queueIndex(args, "index")); ok("Playing queue item")
        },
        StoneAction("player.queue_move", "Reorder the queue: move one entry to another position.", ActionRisk.REVERSIBLE, listOf(
            ActionParam("from", "integer", "Current index of the entry.", true),
            ActionParam("to", "integer", "Index it should end at.", true))) { args ->
            host().moveQueue(queueIndex(args, "from"), queueIndex(args, "to")); ok("Queue reordered")
        },
        StoneAction("player.queue_remove", "Remove one entry from the queue (the file is not deleted).", ActionRisk.REVERSIBLE,
            listOf(ActionParam("index", "integer", "Queue index from player_queue.", true))) { args ->
            host().removeFromQueue(queueIndex(args, "index")); ok("Removed from the queue")
        },
        StoneAction("player.queue_clear", "Empty the queue and stop playback.", ActionRisk.REVERSIBLE) {
            host().clearQueue(); ok("Queue cleared")
        },
        StoneAction("player.play_songs", "Play a custom list of songs in the given order (this replaces the queue).", ActionRisk.REVERSIBLE,
            listOf(ActionParam("songIds", "string", "Comma separated song ids from library_search, in playback order.", true))) { args ->
            val targets = args.getString("songIds").split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(::song)
            if (targets.isEmpty()) throw ActionException("songIds must contain at least one id")
            host().play(targets.first(), targets); ok("Playing ${targets.size} song(s)")
        },
        StoneAction("player.set_shuffle", "Turn shuffle on or off.", ActionRisk.REVERSIBLE,
            listOf(ActionParam("enabled", "boolean", "true mixes the queue; false restores its order.", true))) { args ->
            host().setShuffle(args.getBoolean("enabled")); ok("Shuffle ${if (args.getBoolean("enabled")) "on" else "off"}")
        },
        StoneAction("player.set_repeat", "Set the repeat mode.", ActionRisk.REVERSIBLE,
            listOf(ActionParam("mode", "string", "off, one (repeat the song) or all (repeat the queue).", true, repeatModes))) { args ->
            host().setRepeat(repeatModes.indexOf(args.getString("mode"))); ok("Repeat ${args.getString("mode")}")
        },
        StoneAction("player.set_favorite", "Mark or unmark the song that is playing as favourite.", ActionRisk.REVERSIBLE,
            listOf(ActionParam("favorite", "boolean", "true adds it to favourites, false removes it.", true))) { args ->
            val playback = host().playback
            if (playback.currentId.isEmpty()) throw ActionException("Nothing is playing.")
            if (playback.favorite != args.getBoolean("favorite")) host().toggleFavorite()
            ok(if (args.getBoolean("favorite")) "Added to favourites" else "Removed from favourites")
        },
        // ---- equalizer ----
        StoneAction("equalizer.get", "Equalizer state: on/off, preset, the ten bands (31 Hz to 16 kHz, each -10..10) and preset keys.",
            ActionRisk.READ) { equalizer() },
        StoneAction("equalizer.set_enabled", "Turn the equalizer on or off (band values are kept).", ActionRisk.REVERSIBLE,
            listOf(ActionParam("enabled", "boolean", "true = on.", true))) { args ->
            host().setEqualizerEnabled(args.getBoolean("enabled")); ok("Equalizer ${if (args.getBoolean("enabled")) "on" else "off"}")
        },
        StoneAction("equalizer.set_band", "Move ONE equalizer bar. Bands: 0=31Hz 1=63Hz 2=125Hz 3=250Hz 4=500Hz 5=1kHz 6=2kHz 7=4kHz 8=8kHz 9=16kHz. Turns the equalizer on.",
            ActionRisk.REVERSIBLE, listOf(
                ActionParam("band", "integer", "Band index 0-9.", true),
                ActionParam("value", "integer", "Level from -10 (cut) to 10 (boost); 0 is flat.", true))) { args ->
            val band = args.getInt("band"); val value = args.getInt("value")
            if (band !in 0..9) throw ActionException("band must be between 0 and 9")
            if (value !in -10..10) throw ActionException("value must be between -10 and 10")
            host().setEqualizerBand(band, value); ok("${bandLabels[band]} set to $value")
        },
        StoneAction("equalizer.set_bands", "Set ALL ten equalizer bars at once, from 31 Hz to 16 kHz. Turns the equalizer on.",
            ActionRisk.REVERSIBLE, listOf(
                ActionParam("values", "string", "Ten comma separated integers from -10 to 10, e.g. \"5,4,2,0,0,0,1,2,3,4\".", true))) { args ->
            val values = args.getString("values").split(',').map { it.trim().toIntOrNull()
                ?: throw ActionException("values must be ten integers separated by commas") }
            if (values.size != 10 || values.any { it !in -10..10 })
                throw ActionException("values must be exactly ten integers between -10 and 10")
            host().setEqualizerPreset("Personalizado", values); ok("Equalizer bands updated")
        },
        StoneAction("equalizer.set_preset", "Apply one of Stone's equalizer presets. Turns the equalizer on.", ActionRisk.REVERSIBLE,
            listOf(ActionParam("preset", "string", "Preset key.", true, presets.keys.toList()))) { args ->
            val (name, values) = presets.getValue(args.getString("preset"))
            host().setEqualizerPreset(name, values); ok("Preset $name applied")
        },
        StoneAction("equalizer.set_by_genre", "Equalizer by genre: on every song change Stone applies the preset of the " +
            "song's genre tag (Electrónica, Rock, Latín…); songs without a known genre use the user's own bands. " +
            "Turning it on also turns the equalizer on. Fill missing genres first with metadata_suggest_tags_many.",
            ActionRisk.REVERSIBLE, listOf(ActionParam("enabled", "boolean", "true = follow the genre.", true))) { args ->
            host().setEqualizerByGenre(args.getBoolean("enabled")); ok("Equalizer by genre " + if (args.getBoolean("enabled")) "on" else "off")
        },
        // ---- video / screen ----
        StoneAction("video.get", "Video settings: how the picture is fitted (scale) and the options available.", ActionRisk.READ) {
            JSONObject().put("scale", host().videoScale).put("scales", JSONArray(listOf("auto", "fit", "zoom", "fill")))
                .put("orientations", JSONArray(listOf("portrait", "landscape", "auto")))
                .put("playingVideo", host().songs.firstOrNull { it.id == host().playback.currentId }?.isVideo == true)
        },
        StoneAction("video.set_scale", "How the video fills the screen: auto, fit (whole picture), zoom (crop to fill) or fill (stretch).",
            ActionRisk.REVERSIBLE, listOf(ActionParam("scale", "string", "Scale key.", true, listOf("auto", "fit", "zoom", "fill")))) { args ->
            host().setVideoScale(args.getString("scale")); ok("Video scale ${args.getString("scale")}")
        },
        StoneAction("video.set_brightness", "Screen brightness while Stone is in front (the same control as swiping the left side of a video). " +
            "It does not change Android's system brightness.", ActionRisk.REVERSIBLE, listOf(
                ActionParam("percent", "integer", "Brightness 2-100. Omit it together with auto=true to follow the system again."),
                ActionParam("auto", "boolean", "true returns to the system brightness."))) { args ->
            val auto = args.optBoolean("auto", false)
            if (!auto && !args.has("percent")) throw ActionException("Give percent (2-100) or auto=true")
            val percent = if (auto) null else args.getInt("percent").also {
                if (it !in 2..100) throw ActionException("percent must be between 2 and 100") }
            if (!host().setScreenBrightness(percent)) throw ActionException("Stone's screen is not visible; open the app.")
            ok(if (percent == null) "Brightness follows the system" else "Brightness $percent%")
        },
        StoneAction("video.set_orientation", "Rotate Stone's screen: portrait, landscape or auto (follow the sensor).", ActionRisk.REVERSIBLE,
            listOf(ActionParam("orientation", "string", "Orientation key.", true, listOf("portrait", "landscape", "auto")))) { args ->
            if (!host().setOrientation(args.getString("orientation"))) throw ActionException("Stone's screen is not visible; open the app.")
            ok("Orientation ${args.getString("orientation")}")
        },
        // ---- library view and collections ----
        StoneAction("library.get_sort", "How the song list on screen is ordered, and the sort keys available.", ActionRisk.READ) {
            val (key, descending) = host().librarySort
            JSONObject().put("by", key).put("descending", descending).put("keys", JSONArray(sortKeys))
        },
        StoneAction("library.sort", "Order the song list shown in Stone.", ActionRisk.REVERSIBLE, listOf(
            ActionParam("by", "string", "Sort key.", true, sortKeys),
            ActionParam("descending", "boolean", "true reverses the order (default false)."))) { args ->
            host().sortLibrary(args.getString("by"), args.optBoolean("descending", false)); ok("Library sorted by ${args.getString("by")}")
        },
        StoneAction("library.browse", "List the artists, albums, folders or genres of the library with their song counts.", ActionRisk.READ, listOf(
            ActionParam("kind", "string", "What to list.", true, listOf("artists", "albums", "folders", "genres")),
            ActionParam("limit", "integer", "Maximum entries (default 100, max 500)."))) { args ->
            val groups = host().songs.filterNot { it.isVideo }.groupBy { when (args.getString("kind")) {
                "artists" -> it.artist; "albums" -> it.album; "genres" -> it.genre.trim(); else -> it.folder } }.filterKeys { it.isNotBlank() }
            JSONObject().put("total", groups.size).put("items", JSONArray(groups.entries.sortedBy { it.key.lowercase() }
                .take(args.optInt("limit", 100).coerceIn(1, 500)).map { JSONObject().put("name", it.key).put("songs", it.value.size) }))
        },
        StoneAction("library.favorites", "List the favourite songs.", ActionRisk.READ) {
            val favorites = host().favoriteIds
            JSONObject().put("songs", JSONArray(host().songs.filter { it.id in favorites }.map { it.json() }))
        },
        StoneAction("library.lyrics", "Lyrics stored for a song or video (empty when it has none). metadata_set_lyrics changes them.", ActionRisk.READ,
            listOf(ActionParam("songId", "string", "Id returned by library_search.", true))) { args ->
            val target = song(args.getString("songId"))
            JSONObject().put("title", target.title).put("synced", target.syncedLyrics.isNotEmpty())
                .put("lyrics", StoneMetadataActions.lyrics(StonePlaybackService.instance?.applicationContext, target))
        },
        StoneAction("playlist.songs", "List the songs of a playlist in order.", ActionRisk.READ,
            listOf(ActionParam("playlistId", "string", "Id from playlist_list.", true))) { args ->
            val playlist = host().playlists.firstOrNull { it.id == args.getString("playlistId") }
                ?: throw ActionException("No playlist with that id. Use playlist_list.")
            val byId = host().songs.associateBy { it.id }
            JSONObject().put("name", playlist.name).put("songs", JSONArray(playlist.songIds.mapNotNull { byId[it]?.json() }))
        },
        StoneAction("playlist.remove_songs", "Remove songs from a playlist (the files are not deleted).", ActionRisk.REVERSIBLE, listOf(
            ActionParam("playlistId", "string", "Id from playlist_list.", true),
            ActionParam("songIds", "string", "Comma separated song ids.", true))) { args ->
            val playlist = host().playlists.firstOrNull { it.id == args.getString("playlistId") }
                ?: throw ActionException("No playlist with that id. Use playlist_list.")
            val targets = args.getString("songIds").split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(::song)
            targets.forEach { host().removeFromPlaylist(playlist.id, it) }
            ok("${targets.size} song(s) removed from '${playlist.name}'")
        },
        StoneAction("playlist.play", "Play a playlist from its first song.", ActionRisk.REVERSIBLE,
            listOf(ActionParam("playlistId", "string", "Id from playlist_list.", true))) { args ->
            val playlist = host().playlists.firstOrNull { it.id == args.getString("playlistId") }
                ?: throw ActionException("No playlist with that id. Use playlist_list.")
            val byId = host().songs.associateBy { it.id }
            val tracks = playlist.songIds.mapNotNull { byId[it] }
            if (tracks.isEmpty()) throw ActionException("That playlist is empty.")
            host().play(tracks.first(), tracks); ok("Playing '${playlist.name}'")
        },
    )
}
