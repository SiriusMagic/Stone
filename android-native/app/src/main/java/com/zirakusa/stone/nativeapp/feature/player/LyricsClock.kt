package com.zirakusa.stone.nativeapp.feature.player

import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import com.zirakusa.stone.core.LyricCursor
import com.zirakusa.stone.core.LyricTimeline
import com.zirakusa.stone.core.LyricsDocument
import com.zirakusa.stone.core.LyricsParser
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.core.lyricsDocument
import com.zirakusa.stone.nativeapp.data.library.LyricsOverrides
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val NoLyrics = LyricsDocument(emptyList(), emptyList())

/**
 * The song's lyrics: the user's saved version first, then the file's own (embedded or .lrc).
 * Read off the main thread; [refreshKey] reloads after the lyrics sheet saved an edit.
 */
@Composable internal fun rememberLyricsDocument(song: Song?, refreshKey: Any? = null): LyricsDocument {
    val context = LocalContext.current.applicationContext
    val overrides = remember(context) { LyricsOverrides(context) }
    val custom by produceState<String?>(null, song?.id, refreshKey) {
        val id = song?.id ?: run { value = null; return@produceState }
        value = withContext(Dispatchers.IO) { overrides.get(id) }
    }
    return remember(song?.id, song?.lyrics, song?.syncedLyrics, custom) {
        custom?.let(LyricsParser::parse) ?: song?.lyricsDocument() ?: NoLyrics
    }
}

/**
 * Current line/word, read from the player itself. Instead of a free-running timer, it sleeps
 * exactly until the next timestamp (scaled by playback speed) and then asks the player again,
 * so it cannot drift. Seeks, pauses, speed and track changes wake it at once. The state only
 * changes when the line or word changes, so the UI recomposes once per phrase.
 */
@Composable internal fun rememberLyricCursor(playback: PlaybackViewModel, timeline: LyricTimeline): State<LyricCursor> {
    val cursor = remember(timeline) {
        mutableStateOf(if (timeline.isTimed) timeline.cursorAt(playback.progress().first) else LyricCursor.None)
    }
    if (!timeline.isTimed) return cursor
    val player = playback.player()
    val owner = LocalLifecycleOwner.current
    var wake by remember { mutableIntStateOf(0) }
    DisposableEffect(player, timeline) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                cursor.value = timeline.cursorAt(player.currentPosition)
                wake++
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    LaunchedEffect(player, timeline, wake) {
        val source = player ?: return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val position = source.currentPosition
                cursor.value = timeline.cursorAt(position)
                if (!source.isPlaying) break
                val next = timeline.nextChangeAfter(position) ?: break
                val speed = source.playbackParameters.speed.coerceAtLeast(.1f)
                // Re-check at least every second in case the player was moved without an event.
                delay(((next - position) / speed).toLong().coerceIn(8L, 1_000L))
            }
        }
    }
    return cursor
}

/** System "Remove animations" (accessibility): animator scale 0 means move instantly. */
@Composable internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
