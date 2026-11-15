package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.AccessMode
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.StoneAutomation
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** General tab: on/off, access mode of the main token, link, tools summary and the last requests. */
@Composable internal fun ApiGeneralTab(copy: (String, String) -> Unit, openTab: (ApiTab) -> Unit) {
    SettingsGroup { ToggleSetting(R.string.api_enable, R.string.api_enable_hint, ApiMode.enabled, ApiMode::enable) }
    ApiProgress()
    ApiSection(R.string.api_mode_title, Icons.Default.ViewInAr, R.string.api_mode_subtitle) {
        AccessMode.entries.forEach { mode -> ApiModeCard(mode, ApiMode.mode == mode) { ApiMode.changeMode(mode) } }
    }
    if (ApiMode.enabled) ApiModeLinkCard(copy)
    ApiSection(R.string.api_tools_summary, Icons.Default.Layers, R.string.api_tools_summary_hint,
        R.string.api_manage to { openTab(ApiTab.TOOLS) }) {
        val groups = StoneAutomation.actions.groupBy { it.name.substringBefore('.') }
        apiCategories.filterKeys { it in groups }.forEach { (key, value) ->
            val tools = groups.getValue(key)
            val on = tools.count { it.toolName !in ApiMode.disabledTools }
            Row(Modifier.fillMaxWidth().clickable { openTab(ApiTab.TOOLS) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(value.second, null, Modifier.size(20.dp), tint = StonePalette.Secondary)
                Text(stringResource(value.first), Modifier.weight(1f).padding(start = 12.dp))
                Text("$on/${tools.size}", color = StonePalette.Muted, fontSize = 13.sp)
                Icon(Icons.Default.CheckCircle, null, Modifier.padding(start = 8.dp).size(18.dp),
                    tint = if (on == tools.size) StonePalette.Yellow else StonePalette.Muted)
            }
        }
    }
    ApiSection(R.string.api_activity, Icons.Default.MonitorHeart, R.string.api_recent_hint,
        R.string.api_see_all to { openTab(ApiTab.ACTIVITY) }) {
        ApiActivityList(limit = 5)
    }
}

/** Bar shown while a batch tool runs (rename, update_many, fetch_lyrics…). */
@Composable internal fun ApiProgress() {
    val progress by StoneAutomation.progress.collectAsState()
    progress?.let { batch ->
        Text(stringResource(R.string.api_batch_progress, batch.action, batch.done, batch.total), Modifier.padding(top = 14.dp),
            color = StonePalette.Yellow, fontSize = 13.sp)
        LinearProgressIndicator({ if (batch.total == 0) 0f else batch.done / batch.total.toFloat() },
            Modifier.fillMaxWidth().padding(top = 6.dp), color = StonePalette.Yellow)
    }
}
