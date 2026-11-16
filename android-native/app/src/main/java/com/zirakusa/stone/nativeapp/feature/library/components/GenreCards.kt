package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import kotlin.math.abs

/** Card colours; each genre always gets the same one (by its name). */
private val genreColors = listOf(Color(0xFFE8820C), Color(0xFFF2C21B), Color(0xFF2E7DD7), Color(0xFFD9425B),
    Color(0xFF7B4FD6), Color(0xFF1FA67A), Color(0xFFE0567F), Color(0xFF3A9FB5), Color(0xFF8E6A3D), Color(0xFF5B6CE0))

/** Géneros tab: coloured card with the name, the song count and a tilted cover in the corner. */
@Composable internal fun GenreCard(name: String, tracks: List<Song>, modifier: Modifier, onClick: () -> Unit) {
    val color = genreColors[abs(name.lowercase().hashCode()) % genreColors.size]
    Box(modifier.height(110.dp).clip(RoundedCornerShape(10.dp)).background(color).clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp).fillMaxWidth(.7f)) {
            Text(name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Text(pluralStringResource(R.plurals.song_count, tracks.size, tracks.size), color = Color.White.copy(alpha = .8f),
                fontSize = 12.sp)
        }
        tracks.firstOrNull()?.let { song ->
            StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.align(Alignment.BottomEnd)
                .offset(x = 14.dp, y = 14.dp).size(78.dp).rotate(22f).clip(RoundedCornerShape(8.dp)))
        }
    }
}
