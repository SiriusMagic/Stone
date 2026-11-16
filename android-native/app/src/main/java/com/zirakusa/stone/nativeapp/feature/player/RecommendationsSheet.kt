package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.ImmersiveWindowEffect
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import kotlin.math.ln

/**
 * Songs to play after [current]: same artist, genre or album first, then favourites and the most
 * played ones. Videos, the current song and songs already queued are left out.
 */
internal fun recommendations(songs: List<Song>, current: Song?, queued: Set<String>, playCounts: Map<String, Int>,
    favorites: Set<String>, limit: Int = 8): List<Song> {
    val pool = songs.filter { !it.isVideo && it.id != current?.id && it.id !in queued }
        .ifEmpty { songs.filter { !it.isVideo && it.id != current?.id } }
    fun same(a: String, b: String?) = a.isNotBlank() && b != null && a.equals(b, ignoreCase = true)
    return pool.sortedByDescending { song ->
        (if (same(song.artist, current?.artist)) 3.0 else 0.0) + (if (same(song.genre, current?.genre)) 2.0 else 0.0) +
            (if (same(song.album, current?.album)) 1.0 else 0.0) + (if (song.id in favorites) 1.5 else 0.0) +
            ln(1.0 + (playCounts[song.id] ?: 0)) * .6 +
            // Stable small variety so the list is not always alphabetical among equals.
            ((song.id + current?.id).hashCode() and 0xFF) / 1000.0
    }.take(limit)
}

/** "Recomendaciones para ti": opened by swiping the mini player upwards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun RecommendationsSheet(songs: List<Song>, tint: Color, onPlay: (Song) -> Unit,
    onPlayNext: (Song) -> Unit, onClose: () -> Unit) {
    val ink = if (tint.luminance() > .5f) Color(0xFF1E1A1C) else Color.White
    ModalBottomSheet(onDismissRequest = onClose, containerColor = tint, contentColor = ink) {
        ImmersiveWindowEffect()
        Text(stringResource(R.string.recommendations_title), Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            fontSize = 24.sp, fontWeight = FontWeight.Medium)
        if (songs.isEmpty()) Text(stringResource(R.string.recommendations_empty), Modifier.padding(24.dp), color = ink.copy(alpha = .7f))
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 28.dp)) {
            items(songs, key = { it.id }) { song ->
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPlay(song) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(song.title, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val seconds = song.durationMs / 1000
                        Text(listOf("%d:%02d".format(seconds / 60, seconds % 60), song.artist).filter { it.isNotBlank() }
                            .joinToString(" • "), color = ink.copy(alpha = .65f), fontSize = 14.sp, maxLines = 1)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text(stringResource(R.string.play)) }, { menu = false; onPlay(song) })
                            DropdownMenuItem({ Text(stringResource(R.string.selection_play_next)) }, { menu = false; onPlayNext(song) })
                        }
                    }
                }
            }
        }
    }
}
