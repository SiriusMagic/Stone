package com.zirakusa.stone.nativeapp.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.LyricLine
import com.zirakusa.stone.core.LyricTimeline
import com.zirakusa.stone.core.LyricsDocument
import com.zirakusa.stone.core.LyricsParser
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.core.lyricsDocument
import com.zirakusa.stone.nativeapp.data.library.LyricsOverrides
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.ui.ImmersiveWindowEffect

private fun LyricsDocument.editableText(): String = if (synced.isNotEmpty()) {
    LyricsParser.toLrc(synced)
} else {
    plain.joinToString("\n")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LyricsSheet(song: Song, state: PlaybackState, playback: PlaybackViewModel,
    progress: Pair<Long, Long>, onClose: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val overrides = remember { LyricsOverrides(context.applicationContext) }
    var editor by remember { mutableStateOf(false) }
    var browserVisible by remember { mutableStateOf(false) }
    var autoSearch by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }
    var document by remember(song.id) {
        mutableStateOf(overrides.get(song.id)?.let(LyricsParser::parse)
            ?: song.lyricsDocument())
    }
    var draft by remember(song.id) {
        val text = document.editableText()
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    val lines = if (document.synced.isNotEmpty()) document.synced else
        document.plain.mapIndexed { index, value -> LyricLine(index.toLong(), value) }
    // Same player-driven clock as the compact chip, so both views always show the same line.
    val timeline = remember(document) { LyricTimeline(document) }
    val cursor by rememberLyricCursor(playback, timeline)
    val active = if (timeline.isTimed) cursor.line else -1
    val reduced = rememberReducedMotion()
    val listState = rememberLazyListState()
    LaunchedEffect(active, lines.size, autoScroll) {
        if (autoScroll && active in lines.indices && active != listState.firstVisibleItemIndex) {
            if (reduced) listState.scrollToItem(active, -180) else listState.animateScrollToItem(active, -180)
        }
    }
    BackHandler(enabled = browserVisible) { browserVisible = false }
    // Lyrics found on the web or on lrclib.net open in the editor, ready to review and save.
    fun openInEditor(text: String) {
        draft = TextFieldValue(text, TextRange(text.length))
        browserVisible = false; autoSearch = false; editor = true
    }
    // Full-screen window of its own: a WebView inside the sliding sheet can stay blank.
    if (browserVisible) Dialog(onDismissRequest = { browserVisible = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = StonePalette.Background) {
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                LyricsSearchBrowser(song, onClose = { browserVisible = false }, onLyrics = ::openInEditor)
            }
        }
    }
    if (autoSearch) LyricsAutoSearchDialog(song, ::openInEditor, onWeb = { autoSearch = false; browserVisible = true },
        onDismiss = { autoSearch = false })

    // Full height, coloured like the cover; the current line follows the song's timing.
    val tint = rememberSongTint(song)
    val ink = if (tint.luminance() > .5f) Color(0xFF1E1A1C) else Color.White
    ModalBottomSheet(onDismissRequest = onClose, containerColor = tint, contentColor = ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(color = ink.copy(alpha = .3f)) }) {
        ImmersiveWindowEffect()
        if (editor) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { editor = false }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
                    Text(stringResource(
                        if (document.synced.isNotEmpty() || document.plain.isNotEmpty()) R.string.lyrics_edit
                        else R.string.lyrics_add
                    ), Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { autoSearch = true }) { Icon(Icons.Default.AutoAwesome, stringResource(R.string.lyrics_auto_title)) }
                    IconButton(onClick = { browserVisible = true }) { Icon(Icons.Default.TravelExplore, stringResource(R.string.search_lyrics)) }
                    TextButton(onClick = {
                        clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { pasted ->
                            val start = draft.selection.min.coerceIn(0, draft.text.length)
                            val end = draft.selection.max.coerceIn(start, draft.text.length)
                            val updated = draft.text.replaceRange(start, end, pasted)
                            draft = TextFieldValue(updated, TextRange(start + pasted.length))
                        }
                    }) { Text(stringResource(R.string.lyrics_paste)) }
                    Button(enabled = draft.text.isNotBlank(), onClick = {
                        overrides.put(song.id, draft.text)
                        document = LyricsParser.parse(draft.text)
                        editor = false
                    }) { Text(stringResource(R.string.save)) }
                }
                OutlinedTextField(draft, { draft = it }, Modifier.fillMaxSize().padding(top = 20.dp),
                    placeholder = { Text(stringResource(R.string.lyrics_placeholder)) })
            }
            return@ModalBottomSheet
        }
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                    Text(state.title, fontWeight = FontWeight.ExtraBold, maxLines = 1, color = StonePalette.Yellow,
                        overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
                    Text(state.artist, color = ink.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator({ if (progress.second > 0) progress.first.toFloat() / progress.second else 0f },
                        Modifier.fillMaxWidth().padding(top = 8.dp), color = StonePalette.Yellow, trackColor = ink.copy(alpha = .15f))
                }
                IconButton(onClick = playback::toggle) {
                    Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (state.playing) R.string.pause else R.string.play), Modifier.size(34.dp), tint = StonePalette.Yellow)
                }
            }
            Spacer(Modifier.height(18.dp))
            if (lines.isNotEmpty()) LazyColumn(Modifier.weight(1f), state = listState,
                contentPadding = PaddingValues(bottom = 160.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                itemsIndexed(lines) { index, line ->
                    val selected = index == active
                    val color by animateColorAsState(
                        when { selected -> ink; active < 0 -> ink.copy(alpha = .85f); else -> ink.copy(alpha = .45f) },
                        animationSpec = tween(220), label = "lyric-color")
                    val emphasis by animateFloatAsState(
                        if (selected) 1f else .96f,
                        animationSpec = tween(220), label = "lyric-emphasis")
                    // Tapping a timed line jumps the song there and resumes following it.
                    Surface(
                        onClick = { playback.seek(line.timeMs); autoScroll = true },
                        enabled = timeline.isTimed,
                        color = Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(line.text.ifBlank { "♪" }, color = color,
                            fontSize = if (selected) 26.sp else 23.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                                .graphicsLayer { scaleX = emphasis; scaleY = emphasis })
                    }
                }
            } else Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(64.dp), tint = ink.copy(alpha = .45f))
                Text(stringResource(R.string.no_lyrics), Modifier.padding(18.dp), color = ink.copy(alpha = .8f), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedIconButton(onClick = { editor = true }, Modifier.size(62.dp)) { Icon(Icons.Default.Add, stringResource(R.string.lyrics_add)) }
                    // Automatic search (lrclib.net) is the main action; its dialog still offers the web as a fallback.
                    FilledIconButton(onClick = { autoSearch = true }, Modifier.size(62.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = StonePalette.Yellow, contentColor = StonePalette.Background)) {
                        Icon(Icons.Default.AutoAwesome, stringResource(R.string.lyrics_auto_title))
                    }
                }
            }
        }
            // Floating capsule: follow the song, edit, delete.
            if (lines.isNotEmpty()) Row(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 24.dp)
                .clip(RoundedCornerShape(28.dp)).background(ink.copy(alpha = .1f))
                .border(1.dp, ink.copy(alpha = .2f), RoundedCornerShape(28.dp)).padding(horizontal = 6.dp)) {
                if (document.synced.isNotEmpty()) IconButton(onClick = { autoScroll = !autoScroll }) {
                    Icon(Icons.Default.VerticalAlignCenter, stringResource(R.string.lyrics_follow),
                        tint = if (autoScroll) StonePalette.Yellow else ink.copy(alpha = .6f))
                }
                IconButton(onClick = {
                    val text = document.editableText()
                    draft = TextFieldValue(text, TextRange(text.length))
                    editor = true
                }) { Icon(Icons.Outlined.Edit, stringResource(R.string.lyrics_edit)) }
                IconButton(onClick = {
                    overrides.clear(song.id)
                    overrides.deleteOffset(song.id)
                    document = LyricsDocument(emptyList(), emptyList())
                    val text = document.editableText()
                    draft = TextFieldValue(text, TextRange(text.length))
                }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
            }
        }
    }
}
