package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** One checkbox per MCP tool, grouped by category, so the user decides exactly what an agent may use. */
@Composable internal fun ApiModeToolList() {
    val actions = StoneAutomation.actions
    Text(stringResource(R.string.api_tools_hint), color = StonePalette.Muted, fontSize = 12.sp)
    Text(stringResource(R.string.api_risk_hint), color = StonePalette.Muted, fontSize = 12.sp)
    Row {
        TextButton(onClick = { ApiMode.allowAllTools(true, actions.map { it.toolName }) }) {
            Text(stringResource(R.string.api_tools_all))
        }
        TextButton(onClick = { ApiMode.allowAllTools(false, actions.map { it.toolName }) }) {
            Text(stringResource(R.string.api_tools_none))
        }
    }
    actions.groupBy { it.name.substringBefore('.') }.forEach { (category, group) ->
        Text(category, color = StonePalette.Yellow, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp))
        group.forEach { action ->
            val levelAllowed = ApiMode.permits(action.risk)
            val checked = action.toolName !in ApiMode.disabledTools
            Row(Modifier.fillMaxWidth().clickable(enabled = levelAllowed) { ApiMode.allowTool(action.toolName, !checked) },
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked && levelAllowed, { ApiMode.allowTool(action.toolName, it) }, enabled = levelAllowed)
                Text(when (action.risk) { ActionRisk.READ -> "○"; ActionRisk.REVERSIBLE -> "◐"; ActionRisk.PERSISTENT -> "●" },
                    color = if (action.risk == ActionRisk.PERSISTENT) StonePalette.Yellow else StonePalette.Muted,
                    modifier = Modifier.width(20.dp))
                Text(action.toolName, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                    color = if (ApiMode.permits(action)) Color.Unspecified else StonePalette.Muted,
                    textDecoration = if (ApiMode.permits(action)) null else TextDecoration.LineThrough)
            }
        }
    }
}
