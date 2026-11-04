package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.data.library.ArtworkLoader
import com.zirakusa.stone.nativeapp.ui.components.*
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@Composable fun MiniPlayer(state: PlaybackState, open: () -> Unit, toggle: () -> Unit,
    song: Song? = null) {
    if (state.queue.isEmpty()) return
    val context = LocalContext.current.applicationContext
    var videoFrame by remember(song?.uri, song?.modifiedMs) { mutableStateOf<ArtworkLoader.VideoFrame?>(null) }
    LaunchedEffect(song?.uri, song?.modifiedMs, song?.isVideo) {
        videoFrame = if (song?.isVideo == true) ArtworkLoader.loadVideoFrame(context, song.uri, song.modifiedMs) else null
    }
    Surface(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp).clickable(onClick = open),
        shape = RoundedCornerShape(24.dp), color = StonePalette.MiniBackground,
        border = BorderStroke(1.dp, StonePalette.MiniBorder), shadowElevation = 10.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                if (song?.isVideo == true && videoFrame?.bitmap != null) {
                    Image(videoFrame!!.bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else StoneArtwork(song?.artworkSource, song?.modifiedMs ?: 0, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(state.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = 0.sp)
                Text(stringResource(if (state.playing) R.string.now_playing else R.string.tap_to_play),
                    Modifier.padding(top = 4.dp), color = StonePalette.MiniSubtitle, fontSize = 12.sp,
                    maxLines = 1, letterSpacing = 0.sp)
            }
            StoneIconButton(StoneGlyph.Options, stringResource(R.string.queue), open)
            StoneIconButton(if (state.playing) StoneGlyph.Pause else StoneGlyph.Play,
                stringResource(if (state.playing) R.string.pause else R.string.play), toggle,
                Modifier.clip(CircleShape).border(2.dp, StonePalette.Yellow, CircleShape))
        }
    }
}
