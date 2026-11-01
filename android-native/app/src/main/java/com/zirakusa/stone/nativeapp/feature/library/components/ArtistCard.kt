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

@Composable fun ArtistCard(name: String, tracks: List<Song>, open: () -> Unit) {
    val representative = tracks.firstOrNull()
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 9.dp)
        .clip(RoundedCornerShape(26.dp)).background(Color(0xFF19191D))
        .clickable(onClick = open).padding(12.dp).height(112.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF302D39)), contentAlignment = Alignment.Center) {
            if (representative != null) StoneArtwork(representative.uri, representative.modifiedMs,
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
