package com.zirakusa.stone.nativeapp.feature.settings.api

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
import com.zirakusa.stone.nativeapp.automation.relay.RelayClient
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Ajustes › Modo MCP: General (MCP on/off, create tokens, the tokens) and Analítica, on its own. */
@Composable internal fun ApiModeSettings() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(ApiTab.GENERAL) }
    fun copy(label: String, value: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, R.string.api_copied, Toast.LENGTH_SHORT).show()
    }
    // While redirecting to the hub, the hub has the analytics: only General is shown.
    if (ApiMode.hubRedirect) { ApiGeneralTab(::copy); Spacer(Modifier.height(16.dp)); return }
    ApiTabRow(tab) { tab = it }
    when (tab) {
        ApiTab.GENERAL -> ApiGeneralTab(::copy)
        ApiTab.ANALYTICS -> ApiActivityTab()
    }
    Spacer(Modifier.height(16.dp))
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
