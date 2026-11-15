package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.ActionRisk
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.StoneAction
import com.zirakusa.stone.nativeapp.automation.StoneAutomation
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Herramientas tab: one switch per MCP tool, grouped by category with a checkbox for the whole group. */
@Composable internal fun ApiModeToolList() {
    val actions = StoneAutomation.actions
    val names = actions.map { it.toolName }
    ApiSection(R.string.api_tab_tools, Icons.Default.Layers, R.string.api_tools_hint) {
        Row {
            TextButton(onClick = { ApiMode.allowAllTools(true, names) }) { Text(stringResource(R.string.api_tools_all)) }
            TextButton(onClick = { ApiMode.allowAllTools(false, names) }) { Text(stringResource(R.string.api_tools_none)) }
        }
        val groups = actions.groupBy { it.name.substringBefore('.') }
        apiCategories.filterKeys { it in groups }.forEach { (key, value) -> ToolGroup(value.first, groups.getValue(key)) }
    }
    ApiNote(R.string.api_tools, R.string.api_risk_hint)
}

@Composable private fun ToolGroup(label: Int, tools: List<StoneAction>) {
    var open by rememberSaveable(label) { mutableStateOf(true) }
    val on = tools.count { it.toolName !in ApiMode.disabledTools }
    val state = when (on) { 0 -> ToggleableState.Off; tools.size -> ToggleableState.On; else -> ToggleableState.Indeterminate }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
        TriStateCheckbox(state, { tools.forEach { ApiMode.allowTool(it.toolName, state != ToggleableState.On) } })
        Text(stringResource(label), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("$on/${tools.size}", color = StonePalette.Muted, fontSize = 13.sp)
        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.padding(start = 6.dp))
    }
    if (open) tools.forEach { action ->
        val allowed = action.toolName !in ApiMode.disabledTools
        Row(Modifier.fillMaxWidth().clickable { ApiMode.allowTool(action.toolName, !allowed) }
            .padding(start = 14.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(action.toolName, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                color = if (allowed) StonePalette.Text else StonePalette.Muted)
            when (action.risk) {
                ActionRisk.CRITICAL -> Icon(Icons.Default.Shield, null, Modifier.size(18.dp), tint = StonePalette.Yellow)
                ActionRisk.PERSISTENT -> Icon(Icons.Default.Restore, null, Modifier.size(18.dp), tint = StonePalette.Secondary)
                else -> Unit
            }
            Switch(allowed, { ApiMode.allowTool(action.toolName, it) }, Modifier.padding(start = 8.dp).scale(.8f))
        }
    }
}
