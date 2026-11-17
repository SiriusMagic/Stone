package com.zirakusa.stone.nativeapp.feature.player.lyrics

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.lyrics.LyricsMatcher
import com.zirakusa.stone.nativeapp.data.library.lyrics.LyricsTextCleaner
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Search engines offered as chips; each turns "title artist" into a search URL. */
private val lyricsSites = listOf(
    "Google" to { q: String -> "https://www.google.com/search?q=" + Uri.encode("letra $q") },
    "Letras.com" to { q: String -> "https://www.letras.com/?q=" + Uri.encode(q) },
    "Genius" to { q: String -> "https://genius.com/search?q=" + Uri.encode(q) },
    "Musixmatch" to { q: String -> "https://www.musixmatch.com/search?query=" + Uri.encode(q) })

/**
 * In-app browser for lyrics. "Copiar" reads the lyrics from the page (or the selected text),
 * cleans credits and buttons, and hands them back to the editor through [onLyrics].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable internal fun LyricsSearchBrowser(song: Song, onClose: () -> Unit, onLyrics: (String) -> Unit) {
    val context = LocalContext.current
    val query = remember(song.id) {
        LyricsMatcher.query(song.title, song.artist, song.album, song.durationMs).let { "${it.title} ${it.artist}".trim() }
    }
    var site by remember { mutableIntStateOf(0) }
    var browserView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    BackHandler { browserView?.takeIf { it.canGoBack() }?.goBack() ?: onClose() }
    DisposableEffect(browserView) {
        val view = browserView
        onDispose { view?.stopLoading(); view?.destroy() }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { browserView?.takeIf { it.canGoBack() }?.goBack() ?: onClose() }) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
            }
            Text(stringResource(R.string.lyrics_browser_title), fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lyricsSites.forEachIndexed { index, (name, url) ->
                FilterChip(site == index, { site = index; browserView?.loadUrl(url(query)) }, { Text(name) })
            }
        }
        if (loading in 1..99) LinearProgressIndicator({ loading / 100f }, Modifier.fillMaxWidth(), color = StonePalette.Yellow)
        if (failed) Text(stringResource(R.string.lyrics_browser_failed), Modifier.padding(16.dp), color = StonePalette.Muted)
        AndroidView(factory = { webContext ->
            WebView(webContext).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(android.graphics.Color.WHITE)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) { loading = newProgress }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) { failed = false }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) failed = true
                    }
                    // Links stay inside this browser, except app links (intent:, market:) which are ignored.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                        request.url.scheme !in setOf("http", "https")
                }
                loadUrl(lyricsSites[0].second(query))
            }.also { browserView = it }
        }, modifier = Modifier.weight(1f).fillMaxWidth())
        Text(stringResource(R.string.lyrics_browser_select_hint), color = StonePalette.Muted,
            fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        Button(enabled = browserView != null, onClick = {
            browserView?.evaluateJavascript(LyricsWebExtractor.SCRIPT) { raw ->
                val lyrics = LyricsTextCleaner.clean(LyricsWebExtractor.decode(raw))
                if (lyrics.lines().count { it.isNotBlank() } < 2)
                    Toast.makeText(context, R.string.lyrics_select_to_copy, Toast.LENGTH_SHORT).show()
                else onLyrics(lyrics)
            }
        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp).height(52.dp)) {
            Icon(Icons.Default.ContentCopy, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.lyrics_browser_copy), fontWeight = FontWeight.Bold)
        }
    }
}
