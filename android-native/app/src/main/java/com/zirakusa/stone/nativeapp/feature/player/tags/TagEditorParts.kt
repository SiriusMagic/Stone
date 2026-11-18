package com.zirakusa.stone.nativeapp.feature.player.tags

import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.system.ImmersiveWindowEffect
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Full-screen editor frame: back, title and a "Guardar" button enabled only with changes. */
@Composable internal fun TagEditorScreen(title: Int, canSave: Boolean, busy: Boolean, onBack: () -> Unit,
    onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false)) {
        ImmersiveWindowEffect()
        Surface(Modifier.fillMaxSize(), color = StonePalette.Background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, stringResource(R.string.back)) }
                    Text(stringResource(title), Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Button(onClick = onSave, enabled = canSave && !busy, colors = ButtonDefaults.buttonColors(
                        containerColor = StonePalette.Blue, contentColor = Color.White)) { Text(stringResource(R.string.save)) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = StonePalette.Blue)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                    content = content)
            }
        }
    }
}

/** Label above an underlined text field, as in the reference editor. */
@Composable internal fun UnderlineField(label: Int, value: String, placeholder: Int? = null,
    singleLine: Boolean = true, onChange: (String) -> Unit) {
    Text(stringResource(label), color = StonePalette.Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 22.dp))
    TextField(value, onChange, Modifier.fillMaxWidth(), singleLine = singleLine,
        placeholder = placeholder?.let { { Text(stringResource(it), color = StonePalette.Hint) } },
        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = StonePalette.Blue,
            unfocusedIndicatorColor = StonePalette.Border, cursorColor = StonePalette.Blue))
}

/** Read-only information row (duration, size, path). */
@Composable internal fun InfoRow(label: Int, value: String) {
    Text(stringResource(label), color = StonePalette.Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 26.dp))
    Text(value, color = StonePalette.Secondary, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
}

/** Suggestion parsed from the file name ("Artista - Título"), with a one-tap replace. */
@Composable internal fun SuggestionCard(title: String, artist: String, onReplace: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).border(1.dp, StonePalette.Border, RoundedCornerShape(16.dp))
        .padding(start = 16.dp, top = 8.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tags_suggestion), Modifier.weight(1f), color = StonePalette.Muted)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tags_suggestion_name, title))
                if (artist.isNotBlank()) Text(stringResource(R.string.tags_suggestion_artist, artist), Modifier.padding(top = 4.dp))
            }
            TextButton(onClick = onReplace) { Text(stringResource(R.string.tags_replace), color = StonePalette.Blue, fontSize = 16.sp) }
        }
    }
}

/** "Artista - Título (lyrics)" → ("Título", "Artista"); null when the name has no such pattern. */
internal fun suggestionFromFileName(fileName: String): Pair<String, String>? {
    val base = fileName.substringAfterLast('/').substringBeforeLast('.')
        .replace(Regex("(?i)[-_ ]*(lyrics?|letra|official (video|audio)|audio|video oficial)$"), "")
        .replace('_', ' ').trim()
    val parts = base.split(" - ", limit = 2)
    if (parts.size < 2) return null
    val artist = parts[0].trim(); val title = parts[1].trim()
    return if (title.isBlank()) null else title to artist
}
