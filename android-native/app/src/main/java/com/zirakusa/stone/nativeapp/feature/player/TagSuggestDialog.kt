package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.LyricsMatcher
import com.zirakusa.stone.nativeapp.data.library.TagCandidate
import com.zirakusa.stone.nativeapp.data.library.TagSuggestions
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.net.URL
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Results of "Completar etiquetas": tap one to fill the editor (nothing is saved until Guardar). */
@Composable internal fun TagSuggestDialog(title: String, artist: String, album: String, durationMs: Long,
    onPick: (TagCandidate) -> Unit, onDismiss: () -> Unit) {
    var results by remember { mutableStateOf<List<TagCandidate>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { TagSuggestions.find(title, artist, album, durationMs) } }
            .onSuccess { results = it }.onFailure { failed = true; results = emptyList() }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.tags_auto_title)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                when {
                    results == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.tags_auto_searching), Modifier.padding(start = 12.dp))
                    }
                    results!!.isEmpty() -> Text(stringResource(if (failed) R.string.tags_auto_offline else R.string.tags_auto_none))
                    else -> results!!.forEach { candidate ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(candidate) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            NetThumb(candidate.coverUrl.replace("600x600bb", "100x100bb"), Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(candidate.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(candidate.artist, candidate.album).filter { it.isNotBlank() }.joinToString(" · "),
                                    fontSize = 13.sp, color = StonePalette.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(candidate.genre, candidate.year).filter { it.isNotBlank() }.joinToString(" · ") + "  ·  " +
                                    stringResource(R.string.lyrics_auto_match, (candidate.confidence * 100).roundToInt()),
                                    fontSize = 12.sp, color = if (candidate.confidence >= LyricsMatcher.CONFIDENT) StonePalette.Yellow else StonePalette.Muted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

/** Small cover from the internet (100 px), loaded in the background. */
@Composable private fun NetThumb(url: String, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = if (url.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching { URL(url).openStream().use(BitmapFactory::decodeStream) }.getOrNull()
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop) }
        ?: androidx.compose.foundation.layout.Box(modifier.background(StonePalette.Cover))
}
