package com.zirakusa.stone.nativeapp.feature.library.components

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.ActionRisk
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.StoneAutomation
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ajustes › Modo API: one switch and the link card; technical details folded under "Opciones avanzadas". */
@Composable internal fun ApiModeSettings() {
    val context = LocalContext.current
    val history by StoneAutomation.history.collectAsState()
    fun copy(label: String, value: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, R.string.api_copied, Toast.LENGTH_SHORT).show()
    }
    Text(stringResource(R.string.api_intro), color = StonePalette.Muted)
    SettingsGroup { ToggleSetting(R.string.api_enable, R.string.api_enable_hint, ApiMode.enabled, ApiMode::enable) }
    if (ApiMode.enabled) ApiModeLinkCard(::copy)
    var advanced by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { advanced = !advanced }, Modifier.padding(top = 12.dp)) {
        Text(stringResource(R.string.api_advanced) + if (advanced) "  ▲" else "  ▼")
    }
    if (advanced) {
        Header(R.string.api_access)
        SettingsGroup {
            ToggleSetting(R.string.api_read_only, R.string.api_read_only_hint, ApiMode.readOnly, ApiMode::restrictToReading)
            if (!ApiMode.readOnly) ToggleSetting(R.string.api_write, R.string.api_write_hint, ApiMode.writeAccess, ApiMode::allowWrite)
        }
        SettingsGroup { ToggleSetting(R.string.api_lan, R.string.api_lan_hint, ApiMode.localNetwork, ApiMode::allowLocalNetwork) }
        if (ApiMode.enabled) {
            Header(R.string.api_address)
            ApiMode.urls().forEach { url ->
                TextButton(onClick = { copy("MCP", url) }) { Text(url, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
            }
            Header(R.string.api_token)
            Text(ApiMode.token, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            FilledTonalButton(onClick = { copy("token", ApiMode.token) }, Modifier.padding(top = 6.dp)) {
                Text(stringResource(R.string.api_copy_token))
            }
            Text(stringResource(R.string.api_header_hint), color = StonePalette.Muted, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp))
            ApiModeConnect(::copy)
        }
        Header(R.string.api_tools)
        Text(stringResource(R.string.api_risk_hint), color = StonePalette.Muted, fontSize = 12.sp)
        StoneAutomation.actions.forEach { action ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(when (action.risk) { ActionRisk.READ -> "○"; ActionRisk.REVERSIBLE -> "◐"; ActionRisk.PERSISTENT -> "●" },
                    color = when (action.risk) { ActionRisk.PERSISTENT -> StonePalette.Yellow; else -> StonePalette.Muted },
                    modifier = Modifier.width(22.dp))
                Text(action.toolName, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                    color = if (ApiMode.permits(action.risk)) Color.Unspecified else StonePalette.Muted,
                    textDecoration = if (ApiMode.permits(action.risk)) null else TextDecoration.LineThrough)
            }
        }
    }
    if (history.isNotEmpty()) {
        Header(R.string.api_activity)
        val clock = SimpleDateFormat("HH:mm:ss", Locale.US)
        history.forEach { record ->
            Text("${clock.format(Date(record.timeMs))}  ${if (record.ok) "✓" else "✗"} ${record.action}  ${record.detail}",
                fontSize = 12.sp, color = if (record.ok) StonePalette.Secondary else MaterialTheme.colorScheme.error,
                maxLines = 2, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable private fun Header(title: Int) {
    Text(stringResource(title), color = StonePalette.Yellow, fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
}
