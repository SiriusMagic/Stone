package com.zirakusa.stone.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.core.*
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.feature.library.*
import com.zirakusa.stone.nativeapp.feature.player.*
import com.zirakusa.stone.nativeapp.playback.*
import com.zirakusa.stone.nativeapp.ui.theme.*

internal val landscapeSongs = (1..45).map { index ->
    Song("content://fixture/$index", "Song %02d".format(index), "Artist $index", "Album", "Music",
        237_000, 1, 1, lyrics = if (index == 1) listOf("Example lyrics") else emptyList())
}

internal val landscapePlayback = PlaybackState(ready = true, playing = true,
    title = landscapeSongs[0].title, artist = landscapeSongs[0].artist,
    queue = landscapeSongs.map { QueueEntry(it.id, it.title, it.artist) },
    index = 0, currentId = landscapeSongs[0].id)

@Composable internal fun LandscapeTestContent(
    layout: HomeLayout = homeLayout(LocalConfiguration.current.screenWidthDp.toFloat(), true,
        false, false, false, LandscapeLayoutMode.ALWAYS),
    songs: List<Song> = landscapeSongs, event: (String) -> Unit = {},
    store: StoneSettingsStore? = null, inset: Int = 0,
) {
    val state = if (songs.isEmpty()) PlaybackState() else landscapePlayback
    StoneTheme { Surface(color = StonePalette.Background) {
        Column(Modifier.fillMaxSize().padding(horizontal = inset.dp)) {
            LibraryScreen(LibraryState(songs, hasFolder = true), true,
                { event("folder") }, { event("scan") }, { _, _ -> event("play-song") },
                Modifier.weight(1f), currentId = state.currentId, layout = layout,
                settingsStore = store, onOpenSettings = { event("settings") },
                onEqualizer = { event("equalizer") }, playerContent = { visible ->
                    if (visible) LandscapePlayerPanel(state, songs.firstOrNull(), 84_000L to 237_000L,
                        CompactPlayerActions({ event("toggle") }, { event("previous") }, { event("next") },
                            { event("shuffle") }, { event("repeat") }, { event("favorite") },
                            { event("seek") }, { event("options") }, { event("lyrics") },
                            { event("queue") }, { event("open") }))
                })
            if (layout.showMiniPlayer) MiniPlayer(state, { event("open") }, { event("toggle") })
        }
    } }
}
