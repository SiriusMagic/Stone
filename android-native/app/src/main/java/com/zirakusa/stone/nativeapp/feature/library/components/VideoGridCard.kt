package com.zirakusa.stone.nativeapp.feature.library.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

private val VideoShape = RoundedCornerShape(16.dp)

@Composable
private fun rememberVideoFrame(song: Song): ArtworkLoader.VideoFrame {
    val context = LocalContext.current.applicationContext
    var frame by remember(song.uri, song.modifiedMs) {
        mutableStateOf(ArtworkLoader.VideoFrame(null, 0, 0))
    }
    LaunchedEffect(song.uri, song.modifiedMs) {
        frame = ArtworkLoader.loadVideoFrame(context, song.uri, song.modifiedMs)
    }
    return frame
}

internal fun videoDisplayTitle(song: Song): String {
    song.title.trim().takeIf { it.isNotEmpty() }?.let { return it }
    val fileName = Uri.parse(song.uri).lastPathSegment.orEmpty()
        .substringBeforeLast('.')
        .trim()
    return fileName.ifEmpty { "Video" }
}

internal fun formatVideoDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, (totalSeconds / 60) % 60, seconds)
    else "%d:%02d".format(totalSeconds / 60, seconds)
}

private fun qualityLabel(frame: ArtworkLoader.VideoFrame): String? {
    val resolution = maxOf(frame.width, frame.height)
    if (resolution <= 0) return null
    return when {
        resolution >= 3840 -> "4K"
        resolution >= 1920 -> "1080P"
        resolution >= 1280 -> "720P"
        else -> "${resolution}P"
    }
}

@Composable
internal fun VideoThumbnail(song: Song, selected: Boolean, modifier: Modifier = Modifier,
    showPlay: Boolean = true, onOptions: (() -> Unit)? = null) {
    val frame = rememberVideoFrame(song)
    Box(modifier.clip(VideoShape).background(Color(0xFF17161D)).border(
        if (selected) 2.dp else 0.dp,
        if (selected) StonePalette.Yellow else Color.Transparent,
        VideoShape
    )) {
        frame.bitmap?.let { bitmap ->
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                // Portrait sources stay centered in the 16:9 tile instead of being stretched.
                contentScale = if (frame.height > frame.width) ContentScale.Fit else ContentScale.Crop
            )
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StoneIcon(StoneGlyph.Video, color = Color(0xFF85828E), size = 32)
        }
        if (showPlay) {
            Icon(
                Icons.Default.PlayArrow,
                stringResource(R.string.play),
                Modifier.align(Alignment.Center).size(34.dp),
                tint = Color.White
            )
        }
        onOptions?.let { options ->
            IconButton(
                onClick = options,
                modifier = Modifier.align(Alignment.TopEnd).padding(1.dp)
            ) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.more_options), tint = Color.White)
            }
        }
        qualityLabel(frame)?.let { quality ->
            Surface(
                Modifier.align(Alignment.TopStart).padding(7.dp),
                shape = RoundedCornerShape(6.dp),
                color = if (selected) StonePalette.Yellow else Color(0xCC17151E)
            ) {
                Text(
                    quality,
                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = if (selected) StonePalette.Background else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun VideoGridCard(song: Song, selected: Boolean = false, play: () -> Unit,
    onOptions: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().clickable(onClick = play)) {
        VideoThumbnail(
            song = song,
            selected = selected,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            onOptions = onOptions
        )
        Text(
            videoDisplayTitle(song),
            Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 1
        )
        Text(
            stringResource(R.string.video_details, "—", formatVideoDuration(song.durationMs)),
            Modifier.padding(start = 4.dp, top = 3.dp, bottom = 10.dp),
            fontSize = 12.sp,
            color = Color(0xFFAAA7B2),
            maxLines = 1
        )
    }
}

@Composable
fun VideoListRow(song: Song, selected: Boolean = false, play: () -> Unit,
    onOptions: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = play).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoThumbnail(
            song = song,
            selected = selected,
            modifier = Modifier.width(132.dp).aspectRatio(16f / 9f),
            onOptions = null
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                videoDisplayTitle(song),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.video_details, "—", formatVideoDuration(song.durationMs)),
                Modifier.padding(top = 4.dp),
                fontSize = 12.sp,
                color = Color(0xFFAAA7B2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onOptions) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.more_options), tint = Color.White)
        }
    }
}
