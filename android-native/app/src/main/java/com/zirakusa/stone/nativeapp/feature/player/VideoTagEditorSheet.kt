package com.zirakusa.stone.nativeapp.feature.player

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.ArtworkLoader
import com.zirakusa.stone.nativeapp.data.library.VideoMetadataDocument
import com.zirakusa.stone.nativeapp.data.library.VideoMetadataDraft
import com.zirakusa.stone.nativeapp.data.library.VideoMetadataEditor
import com.zirakusa.stone.nativeapp.ui.ImmersiveWindowEffect
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Video metadata editor: details (title, author, album, tags…) and thumbnail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun VideoTagEditorSheet(song: Song, close: () -> Unit, saved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editor = remember { VideoMetadataEditor(context.applicationContext) }
    var document by remember(song.id) { mutableStateOf<VideoMetadataDocument?>(null) }
    var draft by remember(song.id) { mutableStateOf(VideoMetadataDraft(title = song.title, author = song.artist)) }
    var original by remember(song.id) { mutableStateOf(draft) }
    var framePosition by remember(song.id) { mutableLongStateOf(song.durationMs / 3) }
    var frame by remember(song.id) { mutableStateOf<ArtworkLoader.VideoFrame?>(null) }
    var tab by remember(song.id) { mutableIntStateOf(0) }
    var busy by remember(song.id) { mutableStateOf(true) }
    var error by remember(song.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(song.id) {
        val loaded = withContext(Dispatchers.IO) { runCatching { editor.read(song.uri) }.getOrNull() }
        document = loaded
        loaded?.let { draft = it.draft; original = it.draft }
        busy = false
    }
    LaunchedEffect(song.id, framePosition) {
        if (song.durationMs > 0L) frame = ArtworkLoader.loadVideoFrameAt(context.applicationContext,
            song.uri, song.modifiedMs, framePosition, maxDimension = 640)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            withContext(Dispatchers.IO) { runCatching { editor.saveImageFromGallery(song.uri, uri) } }
                .onSuccess { draft = draft.copy(thumbnailUri = it, thumbnailChanged = true); error = null }
                .onFailure { error = it.message ?: context.getString(R.string.video_metadata_image_failed) }
            busy = false
        }
    }
    fun save() = scope.launch {
        busy = true
        withContext(Dispatchers.IO) { runCatching { editor.save(song.uri, draft) } }.onSuccess { outcome ->
            val message = when {
                outcome.nativeFileWritten -> R.string.video_metadata_saved_native
                outcome.nativeFailureMessage != null -> R.string.video_metadata_native_failed
                else -> R.string.video_metadata_saved_internal
            }
            Toast.makeText(context, context.getString(message), Toast.LENGTH_LONG).show()
            saved()
        }.onFailure { error = it.message ?: context.getString(R.string.video_metadata_save_failed) }
        busy = false
    }
    ModalBottomSheet(onDismissRequest = close, containerColor = StonePalette.Chip,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ImmersiveWindowEffect()
        Column(Modifier.fillMaxHeight(.94f).padding(horizontal = 20.dp)) {
            EditorHeader(song, draft, frame?.bitmap, busy, changed = draft != original, onSave = ::save)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp),
                color = StonePalette.Yellow, trackColor = StonePalette.Border)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            document?.let { WriteStatus(it) }
            TabRow(tab, containerColor = Color.Transparent, contentColor = StonePalette.Yellow) {
                listOf(R.string.video_metadata_details, R.string.video_metadata_thumbnail).forEachIndexed { index, label ->
                    Tab(tab == index, { tab = index }, text = { Text(stringResource(label)) })
                }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 14.dp)) {
                if (tab == 0) {
                    item { FieldGroup(R.string.video_metadata_group_main) {
                        EditorField(Icons.Default.Title, R.string.title, draft.title) { draft = draft.copy(title = it) }
                        EditorField(Icons.Default.Person, R.string.video_metadata_author, draft.author) { draft = draft.copy(author = it) }
                        EditorField(Icons.Default.Album, R.string.album, draft.album) { draft = draft.copy(album = it) }
                    } }
                    item { FieldGroup(R.string.video_metadata_group_classification) {
                        EditorField(Icons.Default.Category, R.string.video_metadata_genre, draft.genre) { draft = draft.copy(genre = it) }
                        EditorField(Icons.Default.Event, R.string.video_metadata_date, draft.date,
                            hint = R.string.video_metadata_date_hint) { draft = draft.copy(date = it) }
                        EditorField(Icons.Default.Sell, R.string.video_metadata_keywords, draft.keywords,
                            hint = R.string.video_metadata_keywords_hint) { draft = draft.copy(keywords = it) }
                        KeywordChips(draft.keywords)
                    } }
                    item { FieldGroup(R.string.video_metadata_description) {
                        OutlinedTextField(draft.description, { draft = draft.copy(description = it) },
                            Modifier.fillMaxWidth(), minLines = 4, colors = editorFieldColors(),
                            supportingText = { Text("${draft.description.length}") })
                    } }
                } else item {
                    ThumbnailEditor(song, draft, frame?.bitmap, framePosition, busy,
                        onPosition = { framePosition = it },
                        onUseFrame = {
                            val copy = frame?.bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            if (copy != null) scope.launch {
                                busy = true
                                withContext(Dispatchers.IO) { runCatching { editor.saveVideoFrame(song.uri, copy) } }
                                    .onSuccess { draft = draft.copy(thumbnailUri = it, thumbnailChanged = true) }
                                    .onFailure { error = it.message }
                                busy = false
                            }
                        },
                        onGallery = { imagePicker.launch("image/*") },
                        onRemove = { draft = draft.copy(thumbnailUri = "", thumbnailChanged = true) })
                }
            }
        }
    }
}

@Composable private fun EditorHeader(song: Song, draft: VideoMetadataDraft, frame: Bitmap?, busy: Boolean,
    changed: Boolean, onSave: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(width = 96.dp, height = 56.dp).clip(RoundedCornerShape(10.dp))
            .border(1.dp, StonePalette.Border, RoundedCornerShape(10.dp))) {
            Thumbnail(song, draft, frame)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.video_metadata_title), color = StonePalette.Yellow,
                fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(draft.title.ifBlank { song.title }, fontSize = 18.sp, fontWeight = FontWeight.Black,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(draft.author.ifBlank { song.artist }, color = StonePalette.Muted, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(onClick = onSave, enabled = !busy && changed, colors = ButtonDefaults.buttonColors(
            containerColor = StonePalette.Yellow, contentColor = Color(0xFF0A0A0E))) {
            Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun Thumbnail(song: Song, draft: VideoMetadataDraft, frame: Bitmap?) {
    when {
        draft.thumbnailUri.isNotBlank() -> StoneArtwork(draft.thumbnailUri, 0L, Modifier.fillMaxSize())
        else -> StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.fillMaxSize())
    }
}

@Composable private fun WriteStatus(info: VideoMetadataDocument) {
    val native = info.nativeWriteAvailable
    val tint = if (native) Color(0xFF4CD27A) else StonePalette.Yellow
    Row(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(tint.copy(alpha = .1f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(if (native) Icons.Default.CheckCircle else Icons.Default.Info, null, tint = tint, modifier = Modifier.size(18.dp))
        Column {
            Text(stringResource(R.string.video_metadata_format,
                info.container.extension.ifBlank { "?" }.uppercase()), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(if (native) R.string.video_metadata_native_status
                else R.string.video_metadata_internal_status), color = StonePalette.Secondary, fontSize = 12.sp)
        }
    }
}

@Composable private fun FieldGroup(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(Brush.verticalGradient(listOf(StonePalette.Surface, StonePalette.Background)))
        .border(1.dp, StonePalette.Border, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(title).uppercase(), color = StonePalette.Muted, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        content()
    }
}

@Composable private fun EditorField(icon: ImageVector, label: Int, value: String, hint: Int? = null,
    change: (String) -> Unit) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(stringResource(label)) },
        placeholder = hint?.let { { Text(stringResource(it), color = StonePalette.Hint) } },
        leadingIcon = { Icon(icon, null, tint = StonePalette.Muted) },
        trailingIcon = if (value.isNotEmpty()) { {
            IconButton(onClick = { change("") }) { Icon(Icons.Default.Close, stringResource(R.string.video_metadata_clear)) }
        } } else null,
        colors = editorFieldColors())
}

@Composable private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = StonePalette.Yellow, focusedLabelColor = StonePalette.Yellow,
    cursorColor = StonePalette.Yellow, unfocusedBorderColor = StonePalette.Border)

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun KeywordChips(keywords: String) {
    val items = keywords.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
    if (items.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { keyword ->
            Text("#$keyword", Modifier.clip(RoundedCornerShape(50)).background(StonePalette.Purple)
                .padding(horizontal = 10.dp, vertical = 4.dp), color = StonePalette.PurpleText, fontSize = 12.sp)
        }
    }
}

@Composable private fun ThumbnailEditor(song: Song, draft: VideoMetadataDraft, frame: Bitmap?, position: Long,
    busy: Boolean, onPosition: (Long) -> Unit, onUseFrame: () -> Unit, onGallery: () -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
            .border(1.dp, StonePalette.Border, RoundedCornerShape(16.dp))) { Thumbnail(song, draft, null) }
        if (song.durationMs > 0) FieldGroup(R.string.video_metadata_frame_pick) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                .background(StonePalette.Background), contentAlignment = Alignment.Center) {
                if (frame != null) Image(frame.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else CircularProgressIndicator(color = StonePalette.Yellow)
            }
            Slider(position.toFloat(), { onPosition(it.toLong().coerceIn(0L, song.durationMs)) },
                valueRange = 0f..song.durationMs.toFloat(), colors = SliderDefaults.colors(
                    thumbColor = StonePalette.Yellow, activeTrackColor = StonePalette.Yellow))
            Text(formatPosition(position), color = StonePalette.Muted, fontSize = 12.sp)
            Button(onUseFrame, Modifier.fillMaxWidth(), enabled = frame != null && !busy) {
                Icon(Icons.Default.Crop, null); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.video_metadata_frame))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onGallery, Modifier.weight(1f), enabled = !busy) {
                Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.video_metadata_gallery), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onRemove, enabled = !busy && draft.thumbnailUri.isNotBlank()) {
                Icon(Icons.Default.HideImage, stringResource(R.string.video_metadata_remove_cover))
            }
        }
    }
}

private fun formatPosition(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
