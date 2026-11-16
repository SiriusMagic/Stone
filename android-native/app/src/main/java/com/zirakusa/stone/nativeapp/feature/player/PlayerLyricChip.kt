@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.get
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.core.LyricsDocument
import com.zirakusa.stone.core.LyricsParser
import com.zirakusa.stone.nativeapp.data.library.LyricsOverrides
import com.zirakusa.stone.core.lyricsDocument
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun rememberCurrentLyric(song: Song?, positionMs: Long): Pair<Int, String> {
    val context = LocalContext.current.applicationContext
    val overrides = remember(context) { LyricsOverrides(context) }
    val custom by produceState<String?>(initialValue = null, song?.id) {
        val id = song?.id ?: return@produceState
        value = withContext(Dispatchers.IO) { overrides.get(id) }
    }
    val document = remember(song?.id, song?.lyrics, song?.syncedLyrics, custom) {
        custom?.let(LyricsParser::parse)
            ?: song?.lyricsDocument()
            ?: LyricsDocument(emptyList(), emptyList())
    }
    val active = LyricsParser.activeIndex(document.synced, positionMs)
    val index = if (document.synced.isEmpty()) 0 else active
    val line = if (document.synced.isNotEmpty()) document.synced.getOrNull(index)?.text.orEmpty()
        else document.plain.firstOrNull().orEmpty()
    return index to line
}

@Composable internal fun CurrentLyricChip(song: Song?, positionMs: Long, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    val (lineIndex, line) = rememberCurrentLyric(song, positionMs)
    Surface(onClick = onClick, modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp), color = Color(0xFF242633)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ChatBubbleOutline, stringResource(R.string.lyrics),
                tint = StonePalette.Secondary, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f).padding(start = 8.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(targetState = lineIndex to line, transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()).togetherWith(
                        slideOutVertically { -it / 2 } + fadeOut())
                }, label = "current-lyric") { index ->
                    Text(index.second.ifBlank { stringResource(R.string.lyrics) },
                        color = if (line.isBlank()) StonePalette.Secondary else Color(0xFFD8E5E3),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        lineHeight = 16.sp)
                }
            }
        }
    }
}
