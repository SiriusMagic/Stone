package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.feature.library.QuickActionDefinition
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@Composable fun HomeNavigationRail(actions: List<QuickActionDefinition>, activeIds: Set<String>,
    expanded: Boolean, scanning: Boolean, onHome: () -> Unit, onAction: (String) -> Unit,
    onEqualizer: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        val homeLabel = stringResource(R.string.home)
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onHome)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = homeLabel },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.stone_penguin), null, Modifier.size(44.dp))
            if (expanded) Text(homeLabel, Modifier.padding(start = 8.dp), maxLines = 1)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            actions.forEach { action ->
                RailAction(action.icon, action.label, expanded, action.id in activeIds,
                    enabled = action.id != "scan" || !scanning) { onAction(action.id) }
            }
            RailAction(Icons.Default.Tune, stringResource(R.string.equalizer), expanded,
                "equalizer" in activeIds, onClick = onEqualizer)
        }
        RailAction(Icons.Default.Settings, stringResource(R.string.settings), expanded,
            onClick = onSettings)
    }
}

@Composable private fun RailAction(icon: ImageVector, label: String, expanded: Boolean,
    active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
        .background(if (active) StonePalette.Yellow.copy(alpha = .14f) else StonePalette.Background)
        .clickable(enabled = enabled, onClick = onClick)
        .semantics(mergeDescendants = true) {
            role = Role.Button; contentDescription = label; selected = active
        }.padding(horizontal = if (expanded) 10.dp else 0.dp, vertical = 8.dp),
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        val color = if (!enabled) StonePalette.Muted else if (active) StonePalette.Yellow else StonePalette.Secondary
        Icon(icon, null, Modifier.size(24.dp), tint = color)
        if (expanded) Text(label, Modifier.weight(1f).padding(start = 10.dp), color = color,
            fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
