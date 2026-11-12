package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@Composable fun LandscapePlayerPanel(state: PlaybackState, song: Song?, progress: Pair<Long, Long>,
    actions: CompactPlayerActions) {
    Surface(Modifier.fillMaxSize().testTag("home-player"), shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, StonePalette.Border), color = StonePalette.Background) {
        if (state.queue.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.MusicNote, null, Modifier.size(48.dp), tint = StonePalette.Muted)
                Text(stringResource(R.string.choose_song_to_play), Modifier.padding(top = 12.dp),
                    color = StonePalette.Secondary, textAlign = TextAlign.Center)
            }
        } else BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 280.dp
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconToggleButton(state.favorite, { actions.favorite() }, enabled = state.ready) {
                        Icon(if (state.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            stringResource(R.string.favorite), tint = if (state.favorite) StonePalette.Yellow else StonePalette.Secondary)
                    }
                    IconButton(onClick = actions.options) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val textHeight = (72 * LocalDensity.current.fontScale + 20).dp
                val artworkSize = minOf(maxWidth, (maxHeight - textHeight).coerceAtLeast(40.dp), 240.dp)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val openLabel = stringResource(if (song?.isVideo == true) R.string.open_video else R.string.open_player)
                    val openModifier = actions.open?.let { open ->
                        Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = open)
                            .semantics { contentDescription = openLabel }
                    } ?: Modifier
                    Box(Modifier.size(artworkSize).clip(RoundedCornerShape(16.dp)).then(openModifier),
                        contentAlignment = Alignment.Center) {
                        StoneArtwork(song?.artworkSource, song?.modifiedMs ?: 0, Modifier.fillMaxSize())
                        if (song?.isVideo == true) Icon(Icons.Default.PlayCircle, null, Modifier.size(40.dp), tint = StonePalette.Yellow)
                    }
                    Text(state.title, Modifier.padding(top = 8.dp).testTag("compact-player-title"), fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(state.artist, Modifier.padding(top = 4.dp, bottom = 8.dp).testTag("compact-player-artist"), color = StonePalette.Secondary,
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                }
                CompactPlayerTimeline(progress, actions.seek)
                CompactPlayerControls(state, actions, wide)
            }
        }
    }
}
