package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.feature.library.LibraryState
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Short videos (< 1:30) as tall 9:16 cards, two per row like a shorts feed. */
@Composable internal fun ShortsGrid(shorts: List<Song>, state: LibraryState, currentId: String,
    modifier: Modifier, onPlay: (Song, List<Song>) -> Unit, onOptions: (Song) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(150.dp), modifier.fillMaxSize().testTag("shorts-grid"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(pluralStringResource(R.plurals.shorts_count, shorts.size, shorts.size),
                color = StonePalette.SongSubtitle, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
        }
        if (shorts.isEmpty() && !state.scanning) item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryEmptyState(videos = true)
        }
        items(shorts, key = { it.id }) { short ->
            Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clickable { onPlay(short, shorts) }) {
                VideoThumbnail(short, selected = short.id == currentId, modifier = Modifier.fillMaxSize(),
                    showPlay = false, onOptions = { onOptions(short) }, fill = true)
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp)) {
                    Text(videoDisplayTitle(short), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(formatVideoDuration(short.durationMs), Modifier.padding(top = 4.dp)
                        .background(Color(0xB3000000), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
