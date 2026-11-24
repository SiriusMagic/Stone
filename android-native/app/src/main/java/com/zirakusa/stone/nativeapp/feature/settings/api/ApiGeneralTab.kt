package com.zirakusa.stone.nativeapp.feature.settings.api

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.auth.ApiTokens
import com.zirakusa.stone.nativeapp.automation.relay.RelayClient
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/**
 * General: 1) turn MCP on, 2) «Redireccionar a Hub», 3) create a token, then one compact card per token.
 * While redirecting, the hub manages everything: «Activar MCP» is not needed and tokens are not shown here.
 */
@Composable internal fun ApiGeneralTab(copy: (String, String) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    val redirect = ApiMode.hubRedirect
    ApiActionCard(Icons.Default.ViewInAr, R.string.api_enable, if (redirect) R.string.api_enable_by_hub else R.string.api_enable_hint,
        trailing = { Switch(ApiMode.active, ApiMode::enable, enabled = !redirect) }) {
        if (ApiMode.active) {
            val (color, label) = when {
                !ApiMode.running -> MaterialTheme.colorScheme.error to R.string.api_status_error
                !ApiMode.relayAvailable -> StonePalette.Yellow to R.string.api_relay_missing
                ApiMode.relayState == RelayClient.State.ONLINE -> ApiGreen to R.string.api_relay_online
                ApiMode.relayState == RelayClient.State.CONNECTING -> StonePalette.Yellow to R.string.api_relay_connecting
                else -> MaterialTheme.colorScheme.error to R.string.api_relay_offline
            }
            ApiPill(stringResource(label), color, Modifier.padding(top = 10.dp))
        }
    }
    ApiActionCard(Icons.Default.Key, R.string.api_local_mcp, R.string.api_local_mcp_hint,
        trailing = { Switch(ApiMode.localMcp, ApiMode::allowLocalMcp) }) { }
    ApiHubCard(copy)
    ApiProgress()
    if (redirect) return
    ApiActionCard(Icons.Default.Key, R.string.api_token_create, R.string.api_create_hint, trailing = {
        Button(onClick = { creating = true }, shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StonePalette.Yellow, contentColor = Color.Black)) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Text(stringResource(R.string.api_token_create), Modifier.padding(start = 4.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    })
    ApiTokens.listed.forEach { token -> ApiTokenCard(token, copy) }
    if (creating) ApiTokenEditor(null, copy) { creating = false }
}

/** Bar shown while a batch tool runs (rename, update_many, fetch_lyrics…). */
@Composable internal fun ApiProgress() {
    val progress by com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation.progress.collectAsState()
    progress?.let { batch ->
        Text(stringResource(R.string.api_batch_progress, batch.action, batch.done, batch.total), Modifier.padding(top = 14.dp),
            color = StonePalette.Yellow, fontSize = 13.sp)
        LinearProgressIndicator({ if (batch.total == 0) 0f else batch.done / batch.total.toFloat() },
            Modifier.fillMaxWidth().padding(top = 6.dp), color = StonePalette.Yellow)
    }
}
