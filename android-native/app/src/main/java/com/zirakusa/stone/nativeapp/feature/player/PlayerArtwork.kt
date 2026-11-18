package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork

/**
 * Cover in the portrait player, in the shape chosen in Ajustes › Personalizado: square, round,
 * or round and turning like a record while the song plays (it stops where it is on pause).
 */
@Composable internal fun PlayerArtwork(song: Song, shape: String, playing: Boolean, modifier: Modifier) {
    val round = shape == "round" || shape == "round_spin"
    val angle = remember(song.id) { Animatable(0f) }
    LaunchedEffect(playing, shape, song.id) {
        if (shape == "round_spin" && playing) while (true) {
            val start = angle.value % 360f
            angle.snapTo(start)
            angle.animateTo(start + 360f, tween(20_000, easing = LinearEasing))
        } else if (shape != "round_spin") angle.snapTo(0f)
    }
    if (!round) return StoneArtwork(song.artworkSource, song.modifiedMs, modifier.clip(RoundedCornerShape(28.dp)))
    Box(modifier.graphicsLayer { rotationZ = angle.value }.clip(CircleShape).border(2.dp, Color.White.copy(alpha = .12f), CircleShape),
        contentAlignment = Alignment.Center) {
        StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.fillMaxSize())
        // Small centre hole so the round cover reads as a record.
        Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xCC0D0C11)))
    }
}

/** "Pantalla completa": the cover fills the player behind the controls and fades into [background]. */
@Composable internal fun FullscreenArtwork(song: Song, background: Color) {
    Box(Modifier.fillMaxSize()) {
        StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to background.copy(alpha = .55f),
            .3f to Color.Transparent, .55f to background.copy(alpha = .35f), .78f to background.copy(alpha = .92f), 1f to background)))
    }
}
