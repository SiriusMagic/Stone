package com.zirakusa.stone.nativeapp.feature.player

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.LyricLine
import com.zirakusa.stone.core.LyricsParser
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.library.LyricsOverrides
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LyricsSheet(song: Song, state: PlaybackState, playback: PlaybackViewModel,
    progress: Pair<Long, Long>, onClose: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val overrides = remember { LyricsOverrides(context.applicationContext) }
    var editor by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }
    var draft by remember(song.id) { mutableStateOf(overrides.get(song.id).orEmpty()) }
    var document by remember(song.id) {
        mutableStateOf(overrides.get(song.id)?.let(LyricsParser::parse)
            ?: com.zirakusa.stone.core.LyricsDocument(song.lyrics, song.syncedLyrics))
    }
    val lines = if (document.synced.isNotEmpty()) document.synced else
        document.plain.mapIndexed { index, value -> LyricLine(index.toLong(), value) }
    val active = if (document.synced.isNotEmpty()) LyricsParser.activeIndex(document.synced, progress.first) else -1
    val listState = rememberLazyListState()
    LaunchedEffect(active, lines.size, autoScroll) {
        if (autoScroll && active in lines.indices && active != listState.firstVisibleItemIndex) {
            listState.animateScrollToItem(active, -180)
        }
    }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color(0xFF17151B),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF3A353D)) }) {
        if (editor) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { editor = false }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
                    Text(stringResource(R.string.lyrics_add), Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { clipboard.getText()?.text?.let { draft = it } }) { Text(stringResource(R.string.lyrics_paste)) }
                    Button(enabled = draft.isNotBlank(), onClick = {
                        overrides.put(song.id, draft); document = LyricsParser.parse(draft); editor = false
                    }) { Text(stringResource(R.string.save)) }
                }
                OutlinedTextField(draft, { draft = it }, Modifier.fillMaxSize().padding(top = 20.dp),
                    placeholder = { Text(stringResource(R.string.lyrics_placeholder)) })
            }
            return@ModalBottomSheet
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoneArtwork(song.uri, song.modifiedMs, Modifier.size(64.dp))
                Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                    Text(state.title, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(state.artist, color = Color(0xFFA8A1AB), maxLines = 1)
                    LinearProgressIndicator({ if (progress.second > 0) progress.first.toFloat() / progress.second else 0f },
                        Modifier.fillMaxWidth().padding(top = 8.dp), color = StonePalette.Yellow)
                }
                FilledIconButton(onClick = playback::toggle,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = StonePalette.Yellow,
                        contentColor = StonePalette.Background)) {
                    Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lyrics), Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Black)
                IconButton(onClick = { autoScroll = !autoScroll }) {
                    Icon(Icons.Default.VerticalAlignBottom,
                        if (autoScroll) "Desactivar desplazamiento automático" else "Activar desplazamiento automático",
                        tint = if (autoScroll) StonePalette.Yellow else Color(0xFF8A848F))
                }
                IconButton(onClick = {
                    draft = overrides.get(song.id).orEmpty()
                    editor = true
                }) { Icon(Icons.Default.Edit, "Editar letras") }
                IconButton(onClick = {
                    overrides.delete(song.id)
                    document = com.zirakusa.stone.core.LyricsDocument(song.lyrics, song.syncedLyrics)
                    draft = ""
                }) { Icon(Icons.Default.Delete, "Borrar letras") }
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.dismiss)) }
            }
            if (lines.isNotEmpty()) LazyColumn(Modifier.weight(1f), state = listState,
                contentPadding = PaddingValues(bottom = 220.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                itemsIndexed(lines) { index, line ->
                    val selected = index == active
                    val color by animateColorAsState(
                        if (selected) StonePalette.Yellow else Color(0xFF77717B),
                        animationSpec = tween(220), label = "lyric-color")
                    val emphasis by animateFloatAsState(
                        if (selected) 1f else .96f,
                        animationSpec = tween(220), label = "lyric-emphasis")
                    Surface(
                        color = if (selected) StonePalette.Yellow.copy(alpha = .12f) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(line.text, color = color,
                            fontSize = if (selected) 24.sp else 22.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                                .graphicsLayer { scaleX = emphasis; scaleY = emphasis })
                    }
                }
            } else Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(64.dp), tint = Color(0xFF6D626F))
                Text(stringResource(R.string.no_lyrics), Modifier.padding(18.dp), color = Color(0xFFC6C0C9), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedIconButton(onClick = { editor = true }, Modifier.size(62.dp)) { Icon(Icons.Default.Add, stringResource(R.string.lyrics_add)) }
                    FilledIconButton(onClick = {
                        val query = Uri.encode("${song.title} ${song.artist} lyrics")
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query")))
                    }, Modifier.size(62.dp), colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = StonePalette.Yellow, contentColor = StonePalette.Background)) {
                        Icon(Icons.Default.Search, stringResource(R.string.search_lyrics))
                    }
                }
            }
        }
    }
}
