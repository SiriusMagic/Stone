package com.zirakusa.stone.nativeapp.feature.player.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.core.LyricCursor
import com.zirakusa.stone.core.LyricLine
import com.zirakusa.stone.core.LyricTimeline
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.control.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlin.math.abs

private val LyricEasing = CubicBezierEasing(.22f, 1f, .36f, 1f)
private const val LyricSlideMs = 650

/**
 * Compact lyric window under the player controls: one phrase at a time. Timed lyrics run as
 * a continuous list moving up (the old phrase leaves through the top while the next one comes
 * in from below). Tapping opens the full lyrics.
 */
@Composable internal fun CurrentLyricChip(song: Song?, playback: PlaybackViewModel, onClick: () -> Unit,
    modifier: Modifier = Modifier, refreshKey: Any? = null) {
    val document = rememberLyricsDocument(song, refreshKey)
    val timeline = remember(document) { LyricTimeline(document) }
    Surface(onClick = onClick, modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp), color = Color(0xFF242633)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ChatBubbleOutline, stringResource(R.string.lyrics),
                tint = StonePalette.Secondary, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp).clipToBounds(),
                contentAlignment = Alignment.CenterStart) {
                if (timeline.isTimed) {
                    val cursor by rememberLyricCursor(playback, timeline)
                    LyricStrip(song?.id, timeline.lines, cursor)
                } else {
                    // No timing to follow: show the start of plain lyrics, or just the label.
                    LyricPhrase(document.plain.firstOrNull() ?: stringResource(R.string.lyrics),
                        muted = true, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * Lines around the current one, each moved with a draw-time translation of the same animated
 * position, so the slide never recomposes and stays smooth on slower phones. A new song gets a
 * new position (no animation carried over); seeks further than one line jump without sliding.
 */
@Composable internal fun LyricStrip(songId: String?, lines: List<LyricLine>, cursor: LyricCursor) {
    val reduced = rememberReducedMotion()
    val target = cursor.line
    val position = remember(songId, lines) { Animatable(target.toFloat()) }
    LaunchedEffect(position, target) {
        if (reduced || abs(target - position.value) > 1.5f) position.snapTo(target.toFloat())
        else position.animateTo(target.toFloat(), tween(LyricSlideMs, easing = LyricEasing))
    }
    // Each row fills the window, so moving it by its own height is exactly one line of the list.
    for (index in target - 2..target + 1) key(index) {
        val line = lines.getOrNull(index)
        // Before the first phrase and during instrumental gaps, a quiet note marks the wait.
        val text = line?.text?.takeIf { it.isNotBlank() } ?: if (index in -1..lines.lastIndex) "♪" else ""
        Box(Modifier.fillMaxSize().graphicsLayer {
            val distance = index - position.value
            translationY = distance * size.height
            alpha = (1f - abs(distance)).coerceIn(0f, 1f)
        }, contentAlignment = Alignment.CenterStart) {
            LyricPhrase(text, muted = line == null || line.text.isBlank(), Modifier.fillMaxWidth(),
                words = if (index == target) line?.words.orEmpty() else emptyList(),
                activeWord = if (index == target) cursor.word else -1)
        }
    }
}
