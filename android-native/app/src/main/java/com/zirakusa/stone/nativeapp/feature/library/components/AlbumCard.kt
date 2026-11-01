package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@Composable fun AlbumCard(name: String, tracks: List<Song>, open: () -> Unit) {
    val albumColors = listOf(Color(0xFF5B5749), Color(0xFF176D75), Color(0xFF8A6B0A),
        Color(0xFF55565C), Color(0xFF1766A0), Color(0xFF654B68))
    val frameColor = albumColors[Math.floorMod(name.hashCode(), albumColors.size)]
    val artwork = tracks.firstOrNull()
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 9.dp)
        .clip(RoundedCornerShape(26.dp)).background(Color(0xFF19191D))
        .clickable(onClick = open).padding(12.dp).height(112.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)).background(frameColor),
            contentAlignment = Alignment.Center) {
            if (artwork != null) StoneArtwork(artwork.uri, artwork.modifiedMs,
                Modifier.size(76.dp).clip(CircleShape))
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center) {
            Text(name, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            tracks.take(2).forEach { song ->
                Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, null, tint = StonePalette.SongSubtitle,
                        modifier = Modifier.size(17.dp))
                    Text(song.title, Modifier.padding(start = 8.dp).weight(1f),
                        color = StonePalette.SongSubtitle, fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text(tracks.size.toString(), color = StonePalette.SongSubtitle, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.Top).padding(top = 4.dp, end = 3.dp))
    }
}
