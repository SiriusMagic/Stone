package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.zirakusa.stone.nativeapp.data.library.ArtworkLoader
import com.zirakusa.stone.nativeapp.ui.components.StoneGlyph
import com.zirakusa.stone.nativeapp.ui.components.StoneIcon
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@Composable fun VideoGridCard(song: Song, selected: Boolean = false, play: () -> Unit,
    onOptions: () -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    var frame by remember(song.uri, song.modifiedMs) {
        mutableStateOf(ArtworkLoader.VideoFrame(null, 0, 0))
    }
    LaunchedEffect(song.uri, song.modifiedMs) {
        frame = ArtworkLoader.loadVideoFrame(context, song.uri, song.modifiedMs)
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = play)) {
        val shape = RoundedCornerShape(18.dp)
        Box(Modifier.fillMaxWidth().aspectRatio(1.62f)
            .clip(shape).background(Color(0xFF201F27))
            .border(if (selected) 2.dp else 0.dp,
                if (selected) StonePalette.Yellow else Color.Transparent, shape)) {
            frame.bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop) } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StoneIcon(StoneGlyph.Video, color = Color(0xFF85828E), size = 32)
            }
            Icon(Icons.Default.PlayArrow, stringResource(R.string.play),
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(22.dp), tint = Color.White)
            androidx.compose.material3.IconButton(onClick = onOptions,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.more_options), tint = Color.White)
            }
            val resolution = maxOf(frame.width, frame.height)
            if (resolution > 0) {
                val quality = when {
                    resolution >= 3840 -> "4K"
                    resolution >= 1920 -> "1080P"
                    resolution >= 1280 -> "720P"
                    else -> "${resolution}P"
                }
                Surface(
                    Modifier.align(Alignment.TopStart).padding(7.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = if (selected) StonePalette.Yellow else Color(0xCC17151E),
                ) {
                    Text(quality, Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = if (selected) StonePalette.Background else Color.White,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(song.title, Modifier.fillMaxWidth().padding(start = 4.dp, top = 9.dp),
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        val resolution = maxOf(frame.width, frame.height)
        val details = stringResource(R.string.video_details,
            if (resolution > 0) "${resolution}P" else "—", formatVideoDuration(song.durationMs))
        Text(details, Modifier.padding(start = 4.dp, top = 3.dp, bottom = 12.dp),
            fontSize = 12.sp, color = Color(0xFFAAA7B2), maxLines = 1)
    }
}

private fun formatVideoDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(totalSeconds / 60, seconds)
}
