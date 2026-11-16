package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.AccessMode
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.ApiToken
import com.zirakusa.stone.nativeapp.automation.ApiTokens
import com.zirakusa.stone.nativeapp.automation.StoneAutomation
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import java.text.DateFormat
import java.util.Date

/** One token: name, its own link with copy, a summary and "Configurar token" (editor with tools and analytics). */
@Composable internal fun ApiTokenRow(token: ApiToken, main: Boolean, copy: (String, String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    HorizontalDivider(Modifier.padding(top = 8.dp), color = StonePalette.Muted.copy(alpha = .2f))
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(token.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (main) Text(stringResource(R.string.api_token_main), color = StonePalette.Yellow, fontSize = 12.sp)
            // Each token has its own link: give one to each agent, and revoke one without touching the others.
            Text(ApiMode.linkFor(token.secret), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = StonePalette.Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.api_token_created, format.format(Date(token.createdAt))) + " · " +
                if (token.lastUsedAt > 0) stringResource(R.string.api_token_used, format.format(Date(token.lastUsedAt)))
                else stringResource(R.string.api_token_never), color = StonePalette.Muted, fontSize = 11.sp)
            if (token.expiresAt > 0) Text(if (token.expired) stringResource(R.string.api_token_expired)
                else stringResource(R.string.api_token_expires, format.format(Date(token.expiresAt))),
                color = if (token.expired) ApiRed else StonePalette.Muted, fontSize = 11.sp)
        }
        IconButton(onClick = { copy("Stone MCP · ${token.name}", ApiMode.shareText(token.secret)) }) {
            Icon(Icons.Default.ContentCopy, stringResource(R.string.api_token_copy_link))
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.api_token_copy_link)) }, { menu = false; copy("Stone MCP", ApiMode.shareText(token.secret)) })
                DropdownMenuItem({ Text(stringResource(R.string.api_token_configure)) }, { menu = false; editing = true })
                DropdownMenuItem({ Text(stringResource(R.string.api_token_rotate)) }, { menu = false; ApiTokens.rotate(token.id) })
            }
        }
    }
    val total = remember(token.disabledTools) { StoneAutomation.actions.size - 1 } // stone_help is always on
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        ApiPill(if (token.expired) stringResource(R.string.api_token_expired) else stringResource(R.string.api_token_active),
            if (token.expired) ApiRed else ApiGreen)
        Text(stringResource(token.mode.label()) + " · " + stringResource(R.string.api_token_tools_count,
            total - token.disabledTools.size, total), Modifier.weight(1f).padding(start = 8.dp),
            color = StonePalette.Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        FilledTonalButton(onClick = { editing = true }) { Text(stringResource(R.string.api_token_configure), fontSize = 12.sp) }
    }
    if (editing) ApiTokenEditor(token) { editing = false }
}

/**
 * Create or edit a token: name, access mode and expiry. [onSave] gets the expiry in days from now
 * (0 = never), or null when editing and the expiry was not touched.
 */
@Composable internal fun ApiTokenDialog(initialName: String = "", initialMode: AccessMode = AccessMode.WRITE,
    initialExpiresAt: Long = 0, onDismiss: () -> Unit, onSave: (String, AccessMode, Int?) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var mode by remember { mutableStateOf(initialMode) }
    val editing = initialName.isNotEmpty()
    // Editing starts on "no change"; choosing an option counts the new expiry from today.
    var choice by remember { mutableStateOf<Int?>(if (editing) null else 0) }
    var custom by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initialName.isEmpty()) R.string.api_token_create else R.string.api_token_rename)) },
        text = {
            Column {
                OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.api_token_name)) })
                AccessMode.entries.forEach { item -> ApiModeCard(item, mode == item) { mode = item } }
                // Every token can have its own end date: a test, a friend or a second agent stops by itself.
                Text(stringResource(R.string.api_token_validity), Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
                if (editing) Text(if (initialExpiresAt > 0) stringResource(R.string.api_token_expires,
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(initialExpiresAt)))
                    else stringResource(R.string.api_token_forever), color = StonePalette.Muted, fontSize = 12.sp)
                ExpiryChips(choice) { choice = it }
                if (choice == -1) OutlinedTextField(custom, { value -> custom = value.filter(Char::isDigit).take(4) },
                    Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.api_token_custom_days)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = { TextButton(enabled = choice != -1 || (custom.toIntOrNull() ?: 0) in 1..3650,
            onClick = { onSave(name, mode, if (choice == -1) custom.toInt() else choice) }) {
            Text(stringResource(if (initialName.isEmpty()) R.string.create else R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
