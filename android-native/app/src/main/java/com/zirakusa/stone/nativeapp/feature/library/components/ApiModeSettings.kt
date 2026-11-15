package com.zirakusa.stone.nativeapp.feature.library.components

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.RelayClient
import com.zirakusa.stone.nativeapp.automation.StoneAutomation
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Ajustes › Modo API: status, then five tabs (General, Herramientas, Seguridad, Enlace, Actividad). */
@Composable internal fun ApiModeSettings() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(ApiTab.GENERAL) }
    fun copy(label: String, value: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, R.string.api_copied, Toast.LENGTH_SHORT).show()
    }
    Text(stringResource(R.string.api_intro), color = StonePalette.Muted)
    ApiStatusCard()
    ApiTabRow(tab) { tab = it }
    when (tab) {
        ApiTab.GENERAL -> ApiGeneralTab(::copy) { tab = it }
        ApiTab.TOOLS -> ApiModeToolList()
        ApiTab.SECURITY -> ApiSecurityTab(::copy)
        ApiTab.LINK -> ApiLinkTab(::copy)
        ApiTab.ACTIVITY -> ApiActivityTab()
    }
    Spacer(Modifier.height(16.dp))
}

/** Running state, internet link, tools the main token can use and requests so far. */
@Composable private fun ApiStatusCard() {
    val history by StoneAutomation.history.collectAsState()
    val (color, label) = when {
        !ApiMode.enabled -> StonePalette.Muted to R.string.api_status_inactive
        !ApiMode.running -> MaterialTheme.colorScheme.error to R.string.api_status_error
        else -> ApiGreen to R.string.api_status_active
    }
    val relay = when {
        !ApiMode.enabled -> null
        !ApiMode.relayAvailable -> R.string.api_relay_missing
        ApiMode.relayState == RelayClient.State.ONLINE -> R.string.api_relay_online
        ApiMode.relayState == RelayClient.State.CONNECTING -> R.string.api_relay_connecting
        else -> R.string.api_relay_offline
    }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(20.dp)).background(StonePalette.Chip)
        .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(stringResource(label), color = color, fontWeight = FontWeight.Black, fontSize = 20.sp)
            relay?.let { Text(stringResource(it), color = StonePalette.Muted, fontSize = 13.sp) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.api_status_tools, StoneAutomation.available(ApiMode.mode).size,
                StoneAutomation.actions.size), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.api_status_requests, history.size), color = StonePalette.Muted, fontSize = 13.sp)
        }
    }
}

@Composable private fun ApiTabRow(selected: ApiTab, onSelect: (ApiTab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(18.dp)).background(StonePalette.Chip).padding(4.dp)) {
        ApiTab.entries.forEach { item ->
            val active = item == selected
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                .background(if (active) StonePalette.Yellow else Color.Transparent)
                .clickable { onSelect(item) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val tint = if (active) Color.Black else StonePalette.Text
                Icon(item.icon, null, Modifier.size(22.dp), tint = tint)
                Text(stringResource(item.label), color = tint, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
