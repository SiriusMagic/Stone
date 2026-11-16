package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.AccessMode
import com.zirakusa.stone.nativeapp.automation.ApiToken
import com.zirakusa.stone.nativeapp.automation.ApiTokens
import com.zirakusa.stone.nativeapp.automation.StoneAutomation
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import java.text.DateFormat
import java.util.Date

private val expiryChoices = listOf(0, 7, 30, 90, 365, -1)

/**
 * Full-screen editor of one token: name, access mode, expiry, the tools it may use (by category)
 * and its own request analytics. Nothing is stored until "Guardar"; revoking asks first.
 */
@Composable internal fun ApiTokenEditor(token: ApiToken, onClose: () -> Unit) {
    var name by remember(token.id) { mutableStateOf(token.name) }
    var mode by remember(token.id) { mutableStateOf(token.mode) }
    var expiry by remember(token.id) { mutableStateOf<Int?>(null) } // null = keep the current date
    var customDays by remember(token.id) { mutableStateOf("") }
    var blocked by remember(token.id) { mutableStateOf(token.disabledTools) }
    var open by remember(token.id) { mutableStateOf<String?>(null) }
    var confirmRevoke by remember { mutableStateOf(false) }
    val groups = remember { StoneAutomation.actions.filter { it.toolName != "stone_help" }.groupBy { it.name.substringBefore('.') } }
    val customOk = expiry != -1 || (customDays.toIntOrNull() ?: 0) in 1..3650
    val format = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = StonePalette.Background) {
            LazyColumn(Modifier.fillMaxSize().systemBarsPadding(), contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                        Text(stringResource(R.string.api_token_configure), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Text(token.preview, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = StonePalette.Muted)
                    OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                        label = { Text(stringResource(R.string.api_token_name)) })
                }
                item { AccessMode.entries.forEach { item -> ApiModeCard(item, mode == item) { mode = item } } }
                item {
                    Text(stringResource(R.string.api_token_validity), fontWeight = FontWeight.Bold)
                    Text(if (token.expiresAt > 0) stringResource(if (token.expired) R.string.api_token_expired else R.string.api_token_expires,
                        format.format(Date(token.expiresAt))) else stringResource(R.string.api_token_forever),
                        color = if (token.expired) ApiRed else StonePalette.Muted, fontSize = 12.sp)
                    ExpiryChips(expiry) { expiry = it }
                    if (expiry == -1) OutlinedTextField(customDays, { value -> customDays = value.filter(Char::isDigit).take(4) },
                        Modifier.fillMaxWidth(), singleLine = true, isError = !customOk,
                        label = { Text(stringResource(R.string.api_token_custom_days)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                item {
                    Text(stringResource(R.string.api_token_tools_title), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.api_token_tools_hint), color = StonePalette.Muted, fontSize = 12.sp)
                }
                items(groups.keys.toList(), key = { it }) { category ->
                    val tools = groups.getValue(category).map { it.toolName }
                    val on = tools.count { it !in blocked }
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().clickable { open = if (open == category) null else category },
                            verticalAlignment = Alignment.CenterVertically) {
                            TriStateCheckbox(when (on) { tools.size -> ToggleableState.On; 0 -> ToggleableState.Off
                                else -> ToggleableState.Indeterminate }, onClick = {
                                blocked = if (on == tools.size) blocked + tools else blocked - tools.toSet()
                            })
                            Text(category, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("$on/${tools.size}", color = StonePalette.Muted, fontSize = 12.sp)
                            Icon(if (open == category) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                        }
                        if (open == category) tools.forEach { tool ->
                            Row(Modifier.fillMaxWidth().padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(tool !in blocked, { allowed -> blocked = if (allowed) blocked - tool else blocked + tool })
                                Text(tool, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.api_analytics), fontWeight = FontWeight.Bold)
                    ApiAnalyticsCard(Modifier.padding(top = 6.dp), fixedTokenId = token.id)
                }
                item {
                    Button(enabled = name.isNotBlank() && customOk, modifier = Modifier.fillMaxWidth().height(50.dp), onClick = {
                        ApiTokens.rename(token.id, name); ApiTokens.setMode(token.id, mode)
                        ApiTokens.setDisabledTools(token.id, blocked)
                        expiry?.let { ApiTokens.setExpiry(token.id, if (it == -1) customDays.toInt() else it) }
                        onClose()
                    }) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { confirmRevoke = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(stringResource(R.string.api_token_revoke), color = ApiRed)
                    }
                }
            }
        }
    }
    if (confirmRevoke) AlertDialog(onDismissRequest = { confirmRevoke = false },
        title = { Text(stringResource(R.string.api_token_revoke_title, token.name)) },
        text = { Text(stringResource(R.string.api_token_revoke_text)) },
        confirmButton = { TextButton(onClick = { confirmRevoke = false; ApiTokens.revoke(token.id); onClose() }) {
            Text(stringResource(R.string.api_token_revoke), color = ApiRed) } },
        dismissButton = { TextButton(onClick = { confirmRevoke = false }) { Text(stringResource(R.string.cancel)) } })
}

/** Never, 7, 30, 90 days, 1 year or a custom number of days. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ExpiryChips(choice: Int?, onChoice: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        expiryChoices.forEach { option ->
            FilterChip(choice == option, { onChoice(option) }, { Text(when (option) {
                0 -> stringResource(R.string.api_token_forever)
                365 -> stringResource(R.string.api_token_year)
                -1 -> stringResource(R.string.api_token_custom)
                else -> stringResource(R.string.api_token_days, option)
            }, fontSize = 12.sp) })
        }
    }
}
