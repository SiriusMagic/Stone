package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.LrcLibClient
import com.zirakusa.stone.nativeapp.data.library.LyricsCandidate
import com.zirakusa.stone.nativeapp.data.library.LyricsMatcher
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * "Buscar automáticamente": asks lrclib.net and lists the results with how sure Stone is that
 * each one is this song. Picking one hands its lyrics (synced when available) to the editor.
 */
@Composable internal fun LyricsAutoSearchDialog(song: Song, onPick: (String) -> Unit, onWeb: () -> Unit, onDismiss: () -> Unit) {
    var results by remember(song.id) { mutableStateOf<List<LyricsCandidate>?>(null) }
    var failed by remember(song.id) { mutableStateOf(false) }
    LaunchedEffect(song.id) {
        runCatching { withContext(Dispatchers.IO) {
            LrcLibClient.candidates(LyricsMatcher.query(song.title, song.artist, song.album, song.durationMs))
        } }.onSuccess { results = it }.onFailure { failed = true; results = emptyList() }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_auto_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                when {
                    results == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.lyrics_auto_searching), Modifier.padding(start = 12.dp))
                    }
                    results!!.isEmpty() -> Text(stringResource(if (failed) R.string.lyrics_auto_offline else R.string.lyrics_auto_none))
                    else -> Column(Modifier.weight(1f, fill = false)) {
                        results!!.forEach { candidate -> CandidateRow(candidate) { onPick(candidate.text) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onWeb) { Text(stringResource(R.string.lyrics_auto_web)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun CandidateRow(candidate: LyricsCandidate, onClick: () -> Unit) {
    val percent = (candidate.confidence * 100).roundToInt()
    val minutes = candidate.durationSeconds.roundToInt().let { "%d:%02d".format(it / 60, it % 60) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(candidate.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOf(candidate.artist, minutes, stringResource(if (candidate.synced) R.string.lyrics_auto_synced
            else R.string.lyrics_auto_plain)).joinToString(" · "), color = StonePalette.Secondary, fontSize = 13.sp, maxLines = 1)
        Text(stringResource(R.string.lyrics_auto_match, percent), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (candidate.confidence >= LyricsMatcher.CONFIDENT) StonePalette.Yellow else StonePalette.Muted)
    }
}
