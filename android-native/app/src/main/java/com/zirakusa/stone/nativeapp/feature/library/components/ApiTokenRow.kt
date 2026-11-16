package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.AccessMode
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.ApiToken
import com.zirakusa.stone.nativeapp.automation.ApiTokens
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import java.text.DateFormat
import java.util.Date

/** One token: name, partial secret, mode chips and a menu (copy link, rename, rotate, revoke). */
@Composable internal fun ApiTokenRow(token: ApiToken, main: Boolean, copy: (String, String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    HorizontalDivider(Modifier.padding(top = 8.dp), color = StonePalette.Muted.copy(alpha = .2f))
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(token.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (main) Text(stringResource(R.string.api_token_main), color = StonePalette.Yellow, fontSize = 12.sp)
            Text(token.preview, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = StonePalette.Secondary)
            Text(stringResource(R.string.api_token_created, format.format(Date(token.createdAt))) + " · " +
                if (token.lastUsedAt > 0) stringResource(R.string.api_token_used, format.format(Date(token.lastUsedAt)))
                else stringResource(R.string.api_token_never), color = StonePalette.Muted, fontSize = 11.sp)
            if (token.expiresAt > 0) Text(if (token.expired) stringResource(R.string.api_token_expired)
                else stringResource(R.string.api_token_expires, format.format(Date(token.expiresAt))),
                color = if (token.expired) ApiRed else StonePalette.Muted, fontSize = 11.sp)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.api_token_copy_link)) }, { menu = false; copy("Stone MCP", ApiMode.linkFor(token.secret)) })
                DropdownMenuItem({ Text(stringResource(R.string.api_token_rename)) }, { menu = false; renaming = true })
                DropdownMenuItem({ Text(stringResource(R.string.api_token_rotate)) }, { menu = false; ApiTokens.rotate(token.id) })
                DropdownMenuItem({ Text(stringResource(R.string.api_token_revoke), color = ApiRed) }, { menu = false; ApiTokens.revoke(token.id) })
            }
        }
    }
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AccessMode.entries.forEach { mode ->
            FilterChip(token.mode == mode, { ApiTokens.setMode(token.id, mode) }, { Text(stringResource(mode.label()), fontSize = 12.sp) })
        }
    }
    if (renaming) ApiTokenDialog(token.name, token.mode, onDismiss = { renaming = false }) { name, mode, _ ->
        ApiTokens.rename(token.id, name); ApiTokens.setMode(token.id, mode); renaming = false
    }
}

/** Create or edit a token: name and access mode. */
@Composable internal fun ApiTokenDialog(initialName: String = "", initialMode: AccessMode = AccessMode.WRITE,
    onDismiss: () -> Unit, onSave: (String, AccessMode, Int) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var mode by remember { mutableStateOf(initialMode) }
    var days by remember { mutableIntStateOf(0) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initialName.isEmpty()) R.string.api_token_create else R.string.api_token_rename)) },
        text = {
            Column {
                OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.api_token_name)) })
                AccessMode.entries.forEach { item -> ApiModeCard(item, mode == item) { mode = item } }
                // Expiry only when creating: a token for a test or a friend can end by itself.
                if (initialName.isEmpty()) {
                    Text(stringResource(R.string.api_token_validity), Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 7, 30, 90).forEach { option ->
                            FilterChip(days == option, { days = option }, { Text(if (option == 0) stringResource(R.string.api_token_forever)
                                else stringResource(R.string.api_token_days, option), fontSize = 12.sp) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, mode, days) }) {
            Text(stringResource(if (initialName.isEmpty()) R.string.create else R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
