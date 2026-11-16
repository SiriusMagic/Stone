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
        IconButton(onClick = { copy("Stone MCP · ${token.name}", ApiMode.linkFor(token.secret)) }) {
            Icon(Icons.Default.ContentCopy, stringResource(R.string.api_token_copy_link))
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
    if (renaming) ApiTokenDialog(token.name, token.mode, token.expiresAt, onDismiss = { renaming = false }) { name, mode, days ->
        ApiTokens.rename(token.id, name); ApiTokens.setMode(token.id, mode)
        if (days != null) ApiTokens.setExpiry(token.id, days)
        renaming = false
    }
}

/** Expiry choices in days; 0 = never, -1 = custom number of days. */
private val expiryOptions = listOf(0, 7, 30, 90, 365, -1)

/**
 * Create or edit a token: name, access mode and expiry. [onSave] gets the expiry in days from now
 * (0 = never), or null when editing and the expiry was not touched.
 */
@OptIn(ExperimentalLayoutApi::class)
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    expiryOptions.forEach { option ->
                        FilterChip(choice == option, { choice = option }, { Text(when (option) {
                            0 -> stringResource(R.string.api_token_forever)
                            365 -> stringResource(R.string.api_token_year)
                            -1 -> stringResource(R.string.api_token_custom)
                            else -> stringResource(R.string.api_token_days, option)
                        }, fontSize = 12.sp) })
                    }
                }
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
