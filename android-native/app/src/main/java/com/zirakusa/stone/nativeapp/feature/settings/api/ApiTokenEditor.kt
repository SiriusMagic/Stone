package com.zirakusa.stone.nativeapp.feature.settings.api

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.auth.ApiToken
import com.zirakusa.stone.nativeapp.automation.auth.ApiTokens
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import java.text.DateFormat
import java.util.Date

private val expiryChoices = listOf(0, 7, 30, 90, 365, -1)

/**
 * Create ([token] null) or configure a token, in this order: name, its link, how long it lasts,
 * access mode, and the tools it may use (categories folded). Configuring adds its analytics and
 * Revocar. Nothing is stored until "Guardar"; a new token's link is copied when it is saved.
 */
@Composable internal fun ApiTokenEditor(token: ApiToken?, copy: (String, String) -> Unit, onClose: () -> Unit) {
    val secret = remember(token?.id) { token?.secret ?: ApiTokens.secret() }
    var name by remember(token?.id) { mutableStateOf(token?.name.orEmpty()) }
    var mode by remember(token?.id) { mutableStateOf(token?.mode ?: AccessMode.WRITE) }
    // New tokens start without an end date; existing ones keep theirs until another option is chosen.
    var expiry by remember(token?.id) { mutableStateOf(if (token == null) 0 else null) }
    var customDays by remember(token?.id) { mutableStateOf("") }
    var blocked by remember(token?.id) { mutableStateOf(token?.disabledTools ?: emptySet()) }
    var open by remember(token?.id) { mutableStateOf<String?>(null) }
    var confirmRevoke by remember { mutableStateOf(false) }
    val groups = remember { StoneAutomation.actions.filter { it.toolName != "stone_help" }.groupBy { it.name.substringBefore('.') } }
    val customOk = expiry != -1 || (customDays.toIntOrNull() ?: 0) in 1..3650
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = StonePalette.Background) {
            LazyColumn(Modifier.fillMaxSize().systemBarsPadding(), contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                        Text(stringResource(if (token == null) R.string.api_token_create else R.string.api_token_configure),
                            fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                        label = { Text(stringResource(R.string.api_token_name)) })
                }
                item {
                    Text(stringResource(R.string.api_token_link_label), fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(StonePalette.Chip)
                        .padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ApiMode.linkFor(secret), Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            color = StonePalette.Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { copy("Stone MCP", ApiMode.shareText(secret)) }) {
                            Icon(Icons.Default.ContentCopy, stringResource(R.string.api_token_copy_link))
                        }
                    }
                    if (token == null) Text(stringResource(R.string.api_token_link_after_save), color = StonePalette.Muted, fontSize = 12.sp)
                }
                item {
                    Text(stringResource(R.string.api_token_validity), fontWeight = FontWeight.Bold)
                    token?.let { ExpiryNow(it) }
                    ExpiryChips(expiry) { expiry = it }
                    if (expiry == -1) OutlinedTextField(customDays, { value -> customDays = value.filter(Char::isDigit).take(4) },
                        Modifier.fillMaxWidth(), singleLine = true, isError = !customOk,
                        label = { Text(stringResource(R.string.api_token_custom_days)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                item {
                    Text(stringResource(R.string.api_mode_title), fontWeight = FontWeight.Bold)
                    AccessMode.entries.forEach { item -> ApiModeCard(item, mode == item) { mode = item } }
                }
                item {
                    Text(stringResource(R.string.api_token_tools_title), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.api_token_tools_hint), color = StonePalette.Muted, fontSize = 12.sp)
                }
                items(groups.keys.toList(), key = { it }) { category ->
                    ToolCategory(category, groups.getValue(category).map { it.toolName }, blocked, open == category,
                        onToggleOpen = { open = if (open == category) null else category }) { blocked = it }
                }
                if (token != null) item {
                    Text(stringResource(R.string.api_analytics), fontWeight = FontWeight.Bold)
                    ApiAnalyticsCard(Modifier.padding(top = 6.dp), fixedTokenId = token.id)
                }
                item {
                    Button(enabled = customOk, modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StonePalette.Yellow, contentColor = Color.Black), onClick = {
                        val days = expiry?.let { if (it == -1) customDays.toInt() else it }
                        if (token == null) {
                            val created = ApiTokens.create(name, mode, days ?: 0, secret, blocked)
                            copy("Stone MCP · ${created.name}", ApiMode.shareText(created.secret))
                        } else {
                            ApiTokens.rename(token.id, name); ApiTokens.setMode(token.id, mode)
                            ApiTokens.setDisabledTools(token.id, blocked)
                            days?.let { ApiTokens.setExpiry(token.id, it) }
                        }
                        onClose()
                    }) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
                    if (token != null) OutlinedButton(onClick = { confirmRevoke = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(stringResource(R.string.api_token_revoke), color = ApiRed)
                    }
                }
            }
        }
    }
    if (confirmRevoke && token != null) ApiRevokeDialog(token, { confirmRevoke = false }, onRevoked = onClose)
}

@Composable private fun ExpiryNow(token: ApiToken) {
    val format = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Text(if (token.expiresAt > 0) stringResource(if (token.expired) R.string.api_token_expired else R.string.api_token_expires,
        format.format(Date(token.expiresAt))) else stringResource(R.string.api_token_forever),
        color = if (token.expired) ApiRed else StonePalette.Muted, fontSize = 12.sp)
}

/** One folded category: a tri-state box for all its tools, opened to switch them one by one. */
@Composable private fun ToolCategory(category: String, tools: List<String>, blocked: Set<String>, open: Boolean,
    onToggleOpen: () -> Unit, onChange: (Set<String>) -> Unit) {
    val on = tools.count { it !in blocked }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(StonePalette.Chip)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggleOpen).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TriStateCheckbox(when (on) { tools.size -> ToggleableState.On; 0 -> ToggleableState.Off; else -> ToggleableState.Indeterminate },
                onClick = { onChange(if (on == tools.size) blocked + tools else blocked - tools.toSet()) })
            Text(apiCategories[category]?.let { stringResource(it.first) } ?: category, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("$on/${tools.size}", color = StonePalette.Muted, fontSize = 12.sp)
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (open) tools.forEach { tool ->
            Row(Modifier.fillMaxWidth().padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(tool !in blocked, { allowed -> onChange(if (allowed) blocked - tool else blocked + tool) })
                Text(tool, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
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
