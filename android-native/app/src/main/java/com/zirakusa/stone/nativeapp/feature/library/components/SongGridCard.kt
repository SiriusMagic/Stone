@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Song card of the "Grande" layout: square cover with title and artist below. */
@Composable internal fun SongGridCard(song: Song, current: Boolean, checked: Boolean?, onEdit: (() -> Unit)?,
    onLongClick: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().combinedClickable(onLongClick = onLongClick, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
            .border(if (current) 2.dp else 0.dp, if (current) StonePalette.Yellow else Color.Transparent, shape)) {
            StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.fillMaxSize())
            if (checked != null) SelectionCheck(checked, Modifier.align(Alignment.TopStart).padding(8.dp))
            if (onEdit != null) Box(Modifier.align(Alignment.TopEnd)) { EditTagsButton(onEdit) }
        }
        Text(song.title, Modifier.padding(start = 4.dp, top = 8.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (current) StonePalette.Yellow else Color.White)
        Text(song.artist, Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp), fontSize = 12.sp,
            color = StonePalette.SongSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
