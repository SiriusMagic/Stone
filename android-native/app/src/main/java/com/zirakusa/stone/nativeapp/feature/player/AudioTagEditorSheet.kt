package com.zirakusa.stone.nativeapp.feature.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.AudioTagEditor
import com.zirakusa.stone.nativeapp.data.library.AudioTags
import com.zirakusa.stone.nativeapp.ui.ImmersiveWindowEffect
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.core.graphics.scale

/** Audio tag editor: writes title, artist, album, genre and cover into the original file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AudioTagEditorSheet(song: Song, close: () -> Unit, saved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editor = remember { AudioTagEditor(context.applicationContext) }
    val writeGate = rememberMediaWriteGate()
    var tags by remember(song.id) { mutableStateOf(AudioTags(song.title, song.artist, song.album)) }
    var original by remember(song.id) { mutableStateOf(tags) }
    var preview by remember(song.id) { mutableStateOf<Bitmap?>(null) }
    var tab by remember(song.id) { mutableIntStateOf(0) }
    var busy by remember(song.id) { mutableStateOf(true) }
    var error by remember(song.id) { mutableStateOf<String?>(null) }
    val supported = remember(song.id) { editor.supports(song.uri) }
    LaunchedEffect(song.id) {
        withContext(Dispatchers.IO) { runCatching { editor.read(song.uri) }.getOrNull() }
            ?.let { tags = it; original = it }
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
    ModalBottomSheet(onDismissRequest = close, containerColor = StonePalette.Chip,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ImmersiveWindowEffect()
        Column(Modifier.fillMaxHeight(.92f).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))) { Cover(song, preview, tags.removeCover) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tag_editor), color = StonePalette.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(tags.title.ifBlank { song.title }, fontSize = 18.sp, fontWeight = FontWeight.Black,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tags.artist.ifBlank { song.artist }, color = StonePalette.Muted, fontSize = 13.sp, maxLines = 1)
                }
                Button(onClick = ::save, enabled = !busy && supported && tags != original,
                    colors = ButtonDefaults.buttonColors(containerColor = StonePalette.Yellow, contentColor = Color(0xFF0A0A0E))) {
                    Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = StonePalette.Yellow)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            Text(stringResource(if (supported) R.string.tags_write_original else R.string.tags_format_unsupported),
                color = if (supported) StonePalette.Secondary else StonePalette.Yellow, fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp))
            TabRow(tab, containerColor = Color.Transparent, contentColor = StonePalette.Yellow) {
                listOf(R.string.tags, R.string.cover).forEachIndexed { index, label ->
                    Tab(tab == index, { tab = index }, text = { Text(stringResource(label)) })
                }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 14.dp)) {
                if (tab == 0) item { FieldGroup(R.string.tags) {
                    EditorField(Icons.Default.Title, R.string.title, tags.title) { tags = tags.copy(title = it) }
                    EditorField(Icons.Default.Person, R.string.artist, tags.artist) { tags = tags.copy(artist = it) }
                    EditorField(Icons.Default.Album, R.string.album, tags.album) { tags = tags.copy(album = it) }
                    EditorField(Icons.Default.Category, R.string.genre, tags.genre) { tags = tags.copy(genre = it) }
                } } else item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp))
                            .border(1.dp, StonePalette.Border, RoundedCornerShape(18.dp))) { Cover(song, preview, tags.removeCover) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { picker.launch("image/*") }, Modifier.weight(1f), enabled = !busy && supported) {
                                Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.video_metadata_gallery), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(onClick = { tags = tags.copy(cover = null, removeCover = true); preview = null },
                                enabled = !busy && supported) {
                                Icon(Icons.Default.HideImage, stringResource(R.string.video_metadata_remove_cover))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Cover(song: Song, preview: Bitmap?, removed: Boolean) {
    when {
        preview != null -> Image(preview.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        removed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = StonePalette.Muted)
        }
        else -> StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.fillMaxSize())
    }
}

/** Gallery image → square-ish JPEG of at most 1000 px, a size every player handles. */
private fun coverBytes(context: Context, uri: Uri): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Imagen no válida")
    val scale = minOf(1f, 1000f / maxOf(decoded.width, decoded.height))
    val bitmap = if (scale < 1f) decoded.scale((decoded.width * scale).toInt(),
        (decoded.height * scale).toInt()) else decoded
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out); out.toByteArray()
    }
}
