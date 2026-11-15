@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song

/** Tall 9:16 card for a short video (< 1:30), used by the "Grande" layout of the Shorts tab. */
@Composable internal fun ShortCard(short: Song, selected: Boolean, play: () -> Unit, onOptions: () -> Unit,
    checked: Boolean?, onLongClick: () -> Unit, onEdit: (() -> Unit)?) {
    Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).combinedClickable(onLongClick = onLongClick, onClick = play)) {
        VideoThumbnail(short, selected = selected, modifier = Modifier.fillMaxSize(), showPlay = false,
            onOptions = onOptions.takeIf { checked == null }, fill = true, checked = checked)
        if (onEdit != null) Box(Modifier.align(Alignment.TopEnd)) { EditTagsButton(onEdit) }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp)) {
            Text(videoDisplayTitle(short), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatVideoDuration(short.durationMs), Modifier.padding(top = 4.dp)
                .background(Color(0xB3000000), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
