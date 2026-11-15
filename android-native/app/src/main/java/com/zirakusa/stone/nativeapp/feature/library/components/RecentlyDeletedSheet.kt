package com.zirakusa.stone.nativeapp.feature.library.components

import android.app.Activity
import androidx.core.net.toUri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.ImmersiveWindowEffect
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Trash: restore a song or remove its file from the device after confirmation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RecentlyDeletedSheet(songs: List<Song>, restore: (Song) -> Unit,
    deletePermanently: (List<Song>, (List<Song>) -> Unit) -> Unit, dismiss: () -> Unit,
    markPurged: (List<Song>) -> Unit = {}) {
    val context = LocalContext.current
    // Android 11+ only lets the user (not the app) delete media found through MediaStore.
    var systemPending by remember { mutableStateOf<List<Song>>(emptyList()) }
    val systemDelete = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) markPurged(systemPending)
        systemPending = emptyList()
    }
    var pending by remember { mutableStateOf<List<Song>?>(null) }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = StonePalette.Chip,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ImmersiveWindowEffect()
        Column(Modifier.fillMaxHeight(.8f).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.recently_deleted), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.recently_deleted_hint), color = StonePalette.Muted,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (songs.isNotEmpty()) TextButton(onClick = { pending = songs },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.empty_trash))
                }
            }
            Spacer(Modifier.height(12.dp))
            if (songs.isEmpty()) Text(stringResource(R.string.trash_empty),
                Modifier.padding(top = 32.dp), color = StonePalette.Muted)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(songs, key = { it.id }) { song ->
                    TrashRow(song, { restore(song) }, { pending = listOf(song) })
                }
            }
        }
    }
    pending?.let { targets ->
        AlertDialog(onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.delete_forever_title)) },
            text = { Text(pluralStringResource(R.plurals.delete_forever_message, targets.size, targets.size)) },
            confirmButton = { TextButton(onClick = {
                pending = null
                deletePermanently(targets) { failed ->
                    val media = failed.filter { it.uri.startsWith("content://media/") }
                    if (media.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
                        systemPending = media
                        val request = MediaStore.createDeleteRequest(context.contentResolver,
                            media.map { it.uri.toUri() })
                        systemDelete.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    }
                    val other = failed.size - if (Build.VERSION.SDK_INT >= 30) media.size else 0
                    if (other > 0) Toast.makeText(context, context.resources.getQuantityString(
                        R.plurals.delete_forever_failed, other, other), Toast.LENGTH_LONG).show()
                }
            }) { Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable private fun TrashRow(song: Song, restore: () -> Unit, deleteForever: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(StonePalette.Surface)
        .border(1.dp, StonePalette.Border, shape).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = StonePalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = restore) {
            Icon(Icons.Default.Restore, stringResource(R.string.restore), tint = StonePalette.Yellow)
        }
        IconButton(onClick = deleteForever) {
            Icon(Icons.Default.DeleteForever, stringResource(R.string.delete_forever),
                tint = MaterialTheme.colorScheme.error)
        }
    }
}
