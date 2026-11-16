package com.zirakusa.stone.nativeapp.feature.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.AudioTagEditor
import com.zirakusa.stone.nativeapp.data.library.AudioTags
import com.zirakusa.stone.nativeapp.data.library.MediaFileIo
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * "Editar etiquetas": name, artist, album, genre and cover written into the original file.
 * Tapping the cover (or "Cambiar") loads your own image. A suggestion parsed from the file
 * name can fill name and artist in one tap.
 */
@Composable internal fun AudioTagEditorSheet(song: Song, close: () -> Unit, saved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editor = remember { AudioTagEditor(context.applicationContext) }
    val writeGate = rememberMediaWriteGate()
    var tags by remember(song.id) { mutableStateOf(AudioTags(song.title, song.artist, song.album)) }
    var original by remember(song.id) { mutableStateOf(tags) }
    var preview by remember(song.id) { mutableStateOf<Bitmap?>(null) }
    var busy by remember(song.id) { mutableStateOf(true) }
    var error by remember(song.id) { mutableStateOf<String?>(null) }
    var path by remember(song.id) { mutableStateOf("") }
    var suggestionDismissed by remember(song.id) { mutableStateOf(false) }
    var autoFill by remember(song.id) { mutableStateOf(false) }
    val supported = remember(song.id) { editor.supports(song.uri) }
    LaunchedEffect(song.id) {
        withContext(Dispatchers.IO) {
            path = MediaFileIo.displayPath(context, song.uri)
            runCatching { editor.read(song.uri) }.getOrNull()
        }?.let { tags = it; original = it }
        busy = false
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val cover = withContext(Dispatchers.IO) { runCatching { coverBytes(context, uri) }.getOrNull() }
            if (cover == null) error = context.getString(R.string.video_metadata_image_failed)
            else { tags = tags.copy(cover = cover, removeCover = false); preview = BitmapFactory.decodeByteArray(cover, 0, cover.size) }
        }
    }
    fun save() = writeGate(song.uri) {
        scope.launch {
            busy = true
            error = withContext(Dispatchers.IO) { runCatching { editor.write(song.uri, tags) }.exceptionOrNull()?.message }
            busy = false
            if (error == null) {
                android.widget.Toast.makeText(context, R.string.tags_saved_in_file, android.widget.Toast.LENGTH_SHORT).show()
                saved()
            }
        }
    }
    TagEditorScreen(R.string.tags_edit_title, canSave = supported && tags != original, busy = busy,
        onBack = close, onSave = ::save) {
        val suggestion = remember(path) { suggestionFromFileName(path) }
        if (!suggestionDismissed && suggestion != null &&
            (suggestion.first != tags.title || suggestion.second != tags.artist)) {
            SuggestionCard(suggestion.first, suggestion.second,
                onReplace = { tags = tags.copy(title = suggestion.first, artist = suggestion.second); suggestionDismissed = true },
                onDismiss = { suggestionDismissed = true })
        }
        // Fill name, artist, album, genre and cover from the internet; nothing is written until Guardar.
        FilledTonalButton(onClick = { autoFill = true }, enabled = supported, modifier = Modifier.padding(top = 12.dp)) {
            Icon(Icons.Default.AutoAwesome, null, tint = StonePalette.Yellow, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.tags_auto_title))
        }
        if (autoFill) TagSuggestDialog(tags.title, tags.artist, tags.album, song.durationMs, onDismiss = { autoFill = false },
            onPick = { pick ->
                autoFill = false
                tags = tags.copy(title = pick.title, artist = pick.artist, album = pick.album.ifBlank { tags.album },
                    genre = pick.genre.ifBlank { tags.genre })
                if (pick.coverUrl.isNotBlank()) scope.launch {
                    val cover = withContext(Dispatchers.IO) {
                        runCatching { com.zirakusa.stone.nativeapp.data.library.CoverDownloader.download(pick.coverUrl) }.getOrNull()
                    }
                    if (cover != null) { tags = tags.copy(cover = cover, removeCover = false); preview = BitmapFactory.decodeByteArray(cover, 0, cover.size) }
                }
            })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        if (!supported) Text(stringResource(R.string.tags_format_unsupported), color = StonePalette.Yellow,
            fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        UnderlineField(R.string.tags_name, tags.title) { tags = tags.copy(title = it) }
        UnderlineField(R.string.artist, tags.artist, R.string.tags_artist_hint) { tags = tags.copy(artist = it) }
        UnderlineField(R.string.album, tags.album) { tags = tags.copy(album = it) }
        UnderlineField(R.string.genre, tags.genre) { tags = tags.copy(genre = it) }
        Text(stringResource(R.string.cover), color = StonePalette.Muted, fontSize = 17.sp,
            modifier = Modifier.padding(top = 26.dp, bottom = 12.dp))
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(18.dp)).clickable(enabled = supported) { picker.launch("image/*") }) {
            when {
                preview != null -> Image(preview!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                tags.removeCover -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = StonePalette.Muted)
                }
                else -> StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.fillMaxSize())
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { picker.launch("image/*") }, enabled = supported) {
                Icon(Icons.Default.Edit, null, tint = StonePalette.Blue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.tags_change), color = StonePalette.Blue)
            }
            OutlinedButton(onClick = { tags = tags.copy(cover = null, removeCover = true); preview = null }, enabled = supported) {
                Icon(Icons.Default.HideImage, stringResource(R.string.video_metadata_remove_cover))
            }
        }
        InfoRow(R.string.tags_path, path)
    }
}

/** Gallery image → JPEG of at most 1000 px, a size every player handles. */
private fun coverBytes(context: Context, uri: Uri): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Imagen no válida")
    val scale = minOf(1f, 1000f / maxOf(decoded.width, decoded.height))
    val bitmap = if (scale < 1f) decoded.scale((decoded.width * scale).toInt(), (decoded.height * scale).toInt()) else decoded
    return ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out); out.toByteArray() }
}
