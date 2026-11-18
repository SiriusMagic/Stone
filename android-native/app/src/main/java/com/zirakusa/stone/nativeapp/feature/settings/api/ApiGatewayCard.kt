package com.zirakusa.stone.nativeapp.feature.settings.api

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.provider.Act
import com.zirakusa.stone.nativeapp.automation.provider.ExecPackage
import com.zirakusa.stone.nativeapp.automation.provider.Packages
import com.zirakusa.stone.nativeapp.automation.provider.ProviderState
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import java.text.DateFormat
import java.util.Date

/**
 * Gateway Hub (provider mode): the link state Gateway reports and the execution packages it offered.
 * Reading packages are active by themselves; any other waits for "Aprobar". Nothing to switch on here:
 * the only control is cutting the link.
 */
@Composable internal fun ApiGatewayCard() {
    ApiActionCard(Icons.Default.Hub, R.string.api_gw_title, R.string.api_gw_hint, trailing = {}) {
        val status = when {
            ProviderState.disconnectedByUser -> stringResource(R.string.api_gw_cut)
            !ProviderState.linked -> stringResource(R.string.api_gw_unlinked)
            else -> stringResource(R.string.api_gw_linked, environmentName(ProviderState.environment), modeName(ProviderState.mode))
        }
        Text(status, Modifier.padding(top = 10.dp), color = if (ProviderState.linked) ApiGreen else StonePalette.Muted,
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        val (pending, active) = Packages.all.partition { !it.approved }
        if (pending.isNotEmpty()) Section(R.string.api_gw_pending)
        pending.forEach { p -> PackageRow(p) {
            TextButton({ Packages.reject(p.id) }) { Text(stringResource(R.string.api_gw_reject), color = ApiRed) }
            Button({ Packages.approve(p.id) }) { Text(stringResource(R.string.api_gw_approve)) }
        } }
        if (ProviderState.linked) Section(R.string.api_gw_active)
        if (ProviderState.linked && active.isEmpty()) Text(stringResource(R.string.api_gw_none), color = StonePalette.Muted, fontSize = 13.sp)
        active.forEach { p -> PackageRow(p) { TextButton({ Packages.revoke(p.id) }) { Text(stringResource(R.string.api_gw_revoke), color = ApiRed) } } }
        when {
            ProviderState.disconnectedByUser -> OutlinedButton(ProviderState::allowAgain, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.api_gw_allow)) }
            ProviderState.linked -> TextButton(ProviderState::disconnect, Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.api_gw_disconnect), color = ApiRed) }
        }
    }
}

@Composable private fun Section(title: Int) =
    Text(stringResource(title), Modifier.padding(top = 12.dp, bottom = 2.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)

@Composable private fun PackageRow(p: ExecPackage, actions: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(p.label.ifBlank { p.id }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        val who = if (p.agent == "*") stringResource(R.string.api_gw_all_agents) else stringResource(R.string.api_gw_agent, p.agent)
        val acts = p.acts.sortedBy { it.ordinal }.map { actName(it) }.joinToString(", ")
        val until = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(p.exp * 1000))
        Text("$who · $acts · ${stringResource(R.string.api_gw_until, until)}", color = StonePalette.Secondary, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable private fun actName(act: Act) = stringResource(when (act) {
    Act.LEER -> R.string.api_act_read; Act.REVISAR -> R.string.api_act_review; Act.ESCRIBIR -> R.string.api_act_write
    Act.MODIFICAR -> R.string.api_act_modify; Act.REVERTIR -> R.string.api_act_revert
})

@Composable private fun environmentName(value: String) = stringResource(when (value) {
    "paused" -> R.string.api_gw_env_paused; "disconnected" -> R.string.api_gw_env_disconnected; else -> R.string.api_gw_env_active
})

@Composable private fun modeName(value: String) = stringResource(if (value == "background") R.string.api_gw_mode_background else R.string.api_gw_mode_safe)
