package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

private val cardShape = RoundedCornerShape(24.dp)

/** Icon and tint in front of a smart playlist title. */
internal fun smartPlaylistIcon(key: String): Pair<ImageVector, Color>? = when (key) {
    "favorites" -> Icons.Default.Favorite to Color(0xFFFF5A4E)
    "recent" -> Icons.Default.Schedule to Color.White
    "most" -> Icons.Default.LocalFireDepartment to Color.White
    "lyrics" -> Icons.Default.Lyrics to Color.White
    else -> null
}

/** Big "+ Nueva playlist" card at the top of the Playlists tab. */
@Composable internal fun NewPlaylistCard(onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).height(120.dp).clip(cardShape).background(StonePalette.Surface)
        .combinedClickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Add, null, Modifier.size(36.dp), tint = StonePalette.Secondary)
        Text(stringResource(R.string.new_playlist), Modifier.padding(top = 6.dp), color = StonePalette.Secondary, fontSize = 16.sp)
    }
}

/**
 * Playlist card: cover (custom or first songs), icon + title, song count and the first two
 * songs. Long press changes the cover.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun PlaylistCard(key: String, title: String, tracks: List<Song>, coverUri: String?, playing: Boolean,
    compact: Boolean, open: () -> Unit, onCoverOptions: () -> Unit) {
    val art = if (compact) 72.dp else 104.dp
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(cardShape).background(StonePalette.Surface)
        .then(if (playing) Modifier.border(1.5.dp, StonePalette.Yellow, cardShape) else Modifier)
        .combinedClickable(onClick = open, onLongClick = onCoverOptions).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(art).clip(RoundedCornerShape(18.dp)).background(StonePalette.Cover), contentAlignment = Alignment.Center) {
            val first = tracks.firstOrNull()
            when {
                !coverUri.isNullOrBlank() -> StoneArtwork(coverUri, 0L, Modifier.fillMaxSize())
                first != null -> StoneArtwork(first.artworkSource, first.modifiedMs, Modifier.fillMaxSize())
                else -> Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.empty_playlist),
                    Modifier.size(40.dp), tint = StonePalette.Secondary)
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                smartPlaylistIcon(key)?.let { (icon, tint) -> Icon(icon, null, Modifier.size(22.dp).padding(end = 2.dp), tint = tint) }
                Text(title, Modifier.weight(1f).padding(start = if (smartPlaylistIcon(key) != null) 6.dp else 0.dp),
                    fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tracks.isNotEmpty()) Text(tracks.size.toString(), Modifier.padding(start = 8.dp), color = StonePalette.Muted, fontSize = 13.sp)
                if (playing) Icon(Icons.Default.Equalizer, stringResource(R.string.playing), Modifier.padding(start = 4.dp).size(18.dp),
                    tint = StonePalette.Yellow)
            }
            if (tracks.isEmpty()) Text(stringResource(R.string.playlist_no_songs_yet), Modifier.padding(top = 6.dp),
                color = StonePalette.Muted, fontSize = 14.sp)
            else tracks.take(if (compact) 1 else 2).forEach { song ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, null, Modifier.size(16.dp), tint = StonePalette.Muted)
                    Text(song.title, Modifier.padding(start = 6.dp), color = StonePalette.Secondary, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
