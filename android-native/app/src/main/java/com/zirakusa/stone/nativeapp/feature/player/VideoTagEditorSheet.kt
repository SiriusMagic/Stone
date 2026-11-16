package com.zirakusa.stone.nativeapp.feature.player

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.ArtworkLoader
import com.zirakusa.stone.nativeapp.data.library.MediaFileIo
import com.zirakusa.stone.nativeapp.data.library.VideoMetadataDocument
import com.zirakusa.stone.nativeapp.data.library.VideoMetadataDraft
import com.zirakusa.stone.nativeapp.data.library.VideoMetadataEditor
import com.zirakusa.stone.nativeapp.feature.library.components.formatVideoDuration
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * "Editar información de video": thumbnail (tap to load your own image or use a frame),
 * name, optional details (author, album, genre, tags, description) and file information.
 */
@Composable internal fun VideoTagEditorSheet(song: Song, close: () -> Unit, saved: () -> Unit) {
    val context = LocalContext.current
    val stoneResources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    val editor = remember { VideoMetadataEditor(context.applicationContext) }
    val writeGate = rememberMediaWriteGate()
    var document by remember(song.id) { mutableStateOf<VideoMetadataDocument?>(null) }
    var draft by remember(song.id) { mutableStateOf(VideoMetadataDraft(title = song.title, author = song.artist)) }
    var original by remember(song.id) { mutableStateOf(draft) }
    var frame by remember(song.id) { mutableStateOf<ArtworkLoader.VideoFrame?>(null) }
    var busy by remember(song.id) { mutableStateOf(true) }
    var error by remember(song.id) { mutableStateOf<String?>(null) }
    var path by remember(song.id) { mutableStateOf("") }
    var details by remember(song.id) { mutableStateOf(false) }
    LaunchedEffect(song.id) {
        val loaded = withContext(Dispatchers.IO) {
            path = MediaFileIo.displayPath(context, song.uri)
            runCatching { editor.read(song.uri) }.getOrNull()
        }
        document = loaded
        loaded?.let { draft = it.draft; original = it.draft }
        busy = false
        if (song.durationMs > 0) frame = ArtworkLoader.loadVideoFrameAt(context.applicationContext, song.uri,
            song.modifiedMs, song.durationMs / 3, maxDimension = 640)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            withContext(Dispatchers.IO) { runCatching { editor.saveImageFromGallery(song.uri, uri) } }
                .onSuccess { draft = draft.copy(thumbnailUri = it, thumbnailChanged = true); error = null }
                .onFailure { error = it.message ?: stoneResources.getString(R.string.video_metadata_image_failed) }
            busy = false
        }
    }
    fun saveNow() = scope.launch {
        busy = true
        withContext(Dispatchers.IO) { runCatching { editor.save(song.uri, draft) } }.onSuccess { outcome ->
            val message = when {
                outcome.nativeFileWritten -> R.string.video_metadata_saved_native
                outcome.nativeFailureMessage != null -> R.string.video_metadata_native_failed
                else -> R.string.video_metadata_saved_internal
            }
            Toast.makeText(context, stoneResources.getString(message), Toast.LENGTH_LONG).show()
            saved()
        }.onFailure { error = it.message ?: stoneResources.getString(R.string.video_metadata_save_failed) }
        busy = false
    }
    fun save() = writeGate(song.uri) { saveNow() }
    TagEditorScreen(R.string.video_edit_title, canSave = draft != original, busy = busy, onBack = close, onSave = ::save) {
        Box(Modifier.padding(top = 16.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
            .clickable { imagePicker.launch("image/*") }) {
            val bitmap = frame?.bitmap
            when {
                draft.thumbnailUri.isNotBlank() -> StoneArtwork(draft.thumbnailUri, 0L, Modifier.fillMaxSize())
                bitmap != null -> Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.fillMaxSize())
            }
            Icon(Icons.Default.Edit, stringResource(R.string.tags_change), tint = StonePalette.Blue,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(22.dp))
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { imagePicker.launch("image/*") }, enabled = !busy) {
                Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.tags_change))
            }
            OutlinedButton(enabled = frame?.bitmap != null && !busy, onClick = {
                val copy = frame?.bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                if (copy != null) scope.launch {
                    busy = true
                    withContext(Dispatchers.IO) { runCatching { editor.saveVideoFrame(song.uri, copy) } }
                        .onSuccess { draft = draft.copy(thumbnailUri = it, thumbnailChanged = true) }
                        .onFailure { error = it.message }
                    busy = false
                }
            }) { Text(stringResource(R.string.video_metadata_frame)) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
        UnderlineField(R.string.tags_name, draft.title) { draft = draft.copy(title = it) }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp).clickable { details = !details },
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.video_more_details), Modifier.weight(1f), color = StonePalette.Blue,
                fontWeight = FontWeight.Bold)
            Icon(if (details) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = StonePalette.Blue)
        }
        if (details) {
            UnderlineField(R.string.video_metadata_author, draft.author) { draft = draft.copy(author = it) }
            UnderlineField(R.string.album, draft.album) { draft = draft.copy(album = it) }
            UnderlineField(R.string.video_metadata_genre, draft.genre) { draft = draft.copy(genre = it) }
            UnderlineField(R.string.video_metadata_date, draft.date, R.string.video_metadata_date_hint) { draft = draft.copy(date = it) }
            UnderlineField(R.string.video_metadata_keywords, draft.keywords, R.string.video_metadata_keywords_hint) {
                draft = draft.copy(keywords = it)
            }
            UnderlineField(R.string.video_metadata_description, draft.description, singleLine = false) {
                draft = draft.copy(description = it)
            }
        }
        InfoRow(R.string.tags_duration, formatVideoDuration(song.durationMs))
        InfoRow(R.string.tags_size, formatSize(song.size))
        InfoRow(R.string.tags_path, path)
        document?.let {
            Text(stringResource(if (it.nativeWriteAvailable) R.string.video_metadata_native_status
                else R.string.video_metadata_internal_status), color = StonePalette.Muted, fontSize = 12.sp,
                modifier = Modifier.padding(top = 18.dp))
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1fGB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1fMB", bytes / (1L shl 20).toDouble())
    bytes > 0 -> String.format(Locale.US, "%.0fKB", bytes / 1024.0)
    else -> "—"
}
