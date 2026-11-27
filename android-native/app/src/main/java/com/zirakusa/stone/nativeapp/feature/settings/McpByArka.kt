package com.zirakusa.stone.nativeapp.feature.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.relay.RelayClient
import com.zirakusa.stone.nativeapp.automation.server.McpTrace
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@Composable internal fun McpByArka() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(when {
            !ApiMode.active -> R.string.api_state_off
            ApiMode.relayState == RelayClient.State.ONLINE -> R.string.api_state_online
            ApiMode.relayState == RelayClient.State.CONNECTING -> R.string.api_state_connecting
            else -> R.string.api_state_offline
        }), color = StonePalette.Yellow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        McpSwitch(R.string.api_enable_mcp, ApiMode.enabled, ApiMode::enable)
        McpSwitch(R.string.api_local_mcp, ApiMode.localMcp, ApiMode::allowLocal)
        McpSwitch(R.string.api_redirect_mcp, ApiMode.remote, ApiMode::turnRemote)
        McpTrace.last?.let { trace ->
            Text(stringResource(R.string.api_last_trace, trace.tool, trace.code, trace.millis, trace.id),
                Modifier.padding(top = 12.dp))
        }
        Button(onClick = {
            val open = context.packageManager.getLaunchIntentForPackage(ARKA_PACKAGE)
            if (open != null) context.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else Toast.makeText(context, R.string.api_no_arka, Toast.LENGTH_LONG).show()
        }, Modifier.padding(top = 16.dp), shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StonePalette.Yellow, contentColor = Color.Black)) {
            Text(stringResource(R.string.api_open_arka), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun McpSwitch(label: Int, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = change)
    }
}

/** Arka (the family's Gateway Hub and Lucía), same developer. */
private const val ARKA_PACKAGE = "com.zirakusa.gatewayhub"
