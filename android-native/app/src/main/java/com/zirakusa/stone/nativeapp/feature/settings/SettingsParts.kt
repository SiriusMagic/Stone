package com.zirakusa.stone.nativeapp.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.onClick
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.feature.settings.api.color
import com.zirakusa.stone.nativeapp.feature.settings.api.label

@Composable internal fun SettingsChoice(label: Int, selected: Boolean, action: () -> Unit) =
    SettingsChoiceText(stringResource(label), selected, action)

@Composable internal fun SettingsChoiceText(label: String, selected: Boolean, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp).clickable(onClick = action), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
            tint = if (selected) StonePalette.Yellow else StonePalette.Muted)
    }
}

@Composable internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp), content = content)
}

@Composable internal fun ToggleSetting(title: Int, subtitle: Int, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(stringResource(title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(subtitle), color = StonePalette.Muted, fontSize = 13.sp)
        }
        Switch(value, change)
    }
}
