package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.artwork.ArtworkLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.zirakusa.stone.nativeapp.feature.player.video.coverBackgroundColor

/** Colour taken from the current song's artwork, used by the queue and option sheets. */
@Composable internal fun rememberSongTint(song: Song?, fallback: Color = Color(0xFF2B2830)): Color {
    val context = LocalContext.current
    val tint by produceState(fallback, song?.id, song?.artworkSource, song?.modifiedMs) {
        value = withContext(Dispatchers.IO) {
            val current = song ?: return@withContext fallback
            val artwork = ArtworkLoader.load(context.applicationContext, current.artworkSource, current.modifiedMs)
                ?: if (current.isVideo) ArtworkLoader.loadVideoFrame(context.applicationContext, current.uri,
                    current.modifiedMs).bitmap else null
            // Slightly lifted so the sheet reads as a surface over the player background.
            artwork?.let { Color.White.copy(alpha = .10f).compositeOver(coverBackgroundColor(it)) } ?: fallback
        }
    }
    return animateColorAsState(tint, tween(350), label = "song-tint").value
}
