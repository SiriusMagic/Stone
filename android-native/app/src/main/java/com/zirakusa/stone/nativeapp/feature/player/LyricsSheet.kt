package com.zirakusa.stone.nativeapp.feature.player

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zirakusa.stone.core.LyricLine
import com.zirakusa.stone.core.LyricsDocument
import com.zirakusa.stone.core.LyricsParser
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.core.lyricsDocument
import com.zirakusa.stone.nativeapp.data.library.LyricsOverrides
import com.zirakusa.stone.nativeapp.data.library.AudioTagEditor
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener

private const val copySelectionScript = """
    (function() {
        var selected = window.getSelection ? window.getSelection().toString().trim() : '';
        if (selected) return selected;
        var candidates = Array.from(document.querySelectorAll('[data-attrid], [aria-label]'))
            .filter(function(element) {
                var key = ((element.getAttribute('data-attrid') || '') + ' ' +
                    (element.getAttribute('aria-label') || '')).toLowerCase();
                var text = (element.innerText || '').trim();
                return (key.indexOf('lyrics') >= 0 || key.indexOf('letra') >= 0) &&
                    text.split('\n').length > 3;
            })
            .sort(function(a, b) {
                return (b.innerText || '').length - (a.innerText || '').length;
            });
        return candidates.length ? candidates[0].innerText.trim() : '';
    })();
"""

private fun decodeJavascriptText(raw: String): String = runCatching {
    JSONTokener(raw).nextValue()?.toString().orEmpty()
}.getOrDefault("")

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
    val scope = rememberCoroutineScope()
    var editor by remember { mutableStateOf(false) }
    var browserVisible by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }
    var offset by remember(song.id) { mutableLongStateOf(overrides.offset(song.id)) }
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
    val active = if (document.synced.isNotEmpty()) LyricsParser.activeIndex(document.synced,
        progress.first + offset) else -1
    val listState = rememberLazyListState()
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            val text = if (document.synced.isNotEmpty()) LyricsParser.toLrc(document.synced.map {
                it.copy(timeMs = it.timeMs - offset)
            }) else document.plain.joinToString("\n")
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }
        }
    }
    val embedPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri ->
        if (uri != null) scope.launch {
            val text = if (document.synced.isNotEmpty()) LyricsParser.toLrc(document.synced.map {
                it.copy(timeMs = it.timeMs - offset)
            }) else document.plain.joinToString("\n")
            val error = withContext(Dispatchers.IO) { runCatching {
                AudioTagEditor(context).writeLyricsCopy(song.uri, uri, text)
            }.exceptionOrNull() }
            Toast.makeText(context, if (error == null) context.getString(R.string.lrc_embedded)
                else error.message ?: context.getString(R.string.lrc_embed_failed), Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(active, lines.size, autoScroll) {
        if (autoScroll && active in lines.indices && active != listState.firstVisibleItemIndex) {
            listState.animateScrollToItem(active, -180)
        }
    }
    BackHandler(enabled = browserVisible) { browserVisible = false }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color(0xFF17151B),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF3A353D)) }) {
        if (browserVisible) {
            LyricsSearchBrowser(song, onClose = { browserVisible = false })
            return@ModalBottomSheet
        }
        if (editor) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { editor = false }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
                    Text(stringResource(
                        if (document.synced.isNotEmpty() || document.plain.isNotEmpty()) R.string.lyrics_edit
                        else R.string.lyrics_add
                    ), Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.size(64.dp))
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
                    val text = document.editableText()
                    draft = TextFieldValue(text, TextRange(text.length))
                    editor = true
                }) { Icon(Icons.Default.Edit, "Editar letras") }
                IconButton(onClick = {
                    exportPicker.launch("${song.title.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "stone" }}.lrc")
                }) { Icon(Icons.Default.FileDownload, stringResource(R.string.export_lrc)) }
                if (song.uri.substringBefore('?').substringAfterLast('.', "").equals("mp3", true)) {
                    IconButton(onClick = {
                        embedPicker.launch("${song.title.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "stone" }}-lyrics.mp3")
                    }) { Icon(Icons.Default.LibraryMusic, stringResource(R.string.embed_lrc)) }
                }
                IconButton(onClick = {
                    overrides.delete(song.id)
                    overrides.deleteOffset(song.id)
                    offset = 0L
                    document = song.lyricsDocument()
                    val text = document.editableText()
                    draft = TextFieldValue(text, TextRange(text.length))
                }) { Icon(Icons.Default.Delete, "Borrar letras") }
            }
            if (document.synced.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.lyrics_offset, offset), Modifier.weight(1f),
                        color = StonePalette.Muted, fontSize = 13.sp)
                    TextButton(onClick = {
                        offset = (offset - 250L).coerceIn(-120_000L, 120_000L)
                        overrides.putOffset(song.id, offset)
                    }) { Text("−250") }
                    TextButton(onClick = {
                        offset = (offset + 250L).coerceIn(-120_000L, 120_000L)
                        overrides.putOffset(song.id, offset)
                    }) { Text("+250") }
                    TextButton(onClick = {
                        offset = 0L; overrides.deleteOffset(song.id)
                    }) { Text(stringResource(R.string.reset)) }
                }
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
                        browserVisible = true
                    }, Modifier.size(62.dp), colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = StonePalette.Yellow, contentColor = StonePalette.Background)) {
                        Icon(Icons.Default.Search, stringResource(R.string.search_lyrics))
                    }
                }
            }
        }
    }
}

@Composable private fun LyricsSearchBrowser(song: Song, onClose: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val searchUrl = remember(song.id, song.title, song.artist) {
        val query = Uri.encode("Letras ${song.title} - ${song.artist}")
        "https://www.google.com/search?q=$query"
    }
    var browserView by remember { mutableStateOf<WebView?>(null) }
    BackHandler {
        val view = browserView
        if (view?.canGoBack() == true) {
            view.goBack()
        } else {
            onClose()
        }
    }
    DisposableEffect(browserView) {
        val view = browserView
        onDispose {
            view?.stopLoading()
            view?.destroy()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val view = browserView
                if (view?.canGoBack() == true) view.goBack() else onClose()
            }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.lyrics_browser_title), fontSize = 24.sp,
                fontWeight = FontWeight.Black)
        }
        AndroidView(
            factory = { webContext ->
                WebView(webContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = WebViewClient()
                    loadUrl(searchUrl)
                }.also { browserView = it }
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        Text(stringResource(R.string.lyrics_browser_select_hint), color = StonePalette.Muted,
            fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        Button(enabled = browserView != null, onClick = {
            browserView?.evaluateJavascript(copySelectionScript) { raw ->
                val selected = decodeJavascriptText(raw).trim()
                if (selected.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.lyrics_select_to_copy),
                        Toast.LENGTH_SHORT).show()
                } else {
                    clipboard.setText(AnnotatedString(selected))
                    Toast.makeText(context, context.getString(R.string.lyrics_copied),
                        Toast.LENGTH_SHORT).show()
                }
            }
        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
            Icon(Icons.Default.ContentCopy, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.lyrics_browser_copy))
        }
    }
}
