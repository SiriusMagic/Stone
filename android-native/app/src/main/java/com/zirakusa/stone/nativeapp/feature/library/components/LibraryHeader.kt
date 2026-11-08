package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.feature.library.QuickActionDefinition
import com.zirakusa.stone.nativeapp.ui.components.*
import kotlinx.coroutines.launch

@Composable fun LibraryHeader(scanning: Boolean, onSearch: () -> Unit, onFolder: () -> Unit,
    onScan: () -> Unit, onSort: () -> Unit, onSettings: () -> Unit,
    onEqualizer: () -> Unit = {}, onSleep: () -> Unit = {},
    onPenguinClick: () -> Unit = {},
    showSearch: Boolean = true, showFolder: Boolean = true, showScan: Boolean = true,
    showSort: Boolean = true, showEqualizer: Boolean = true,
    showSleep: Boolean = true, quickActions: List<QuickActionDefinition> = emptyList(),
    onQuickAction: (String) -> Unit = {}, onMoreQuickActions: () -> Unit = {},
    activeQuickActionIds: Set<String> = emptySet(), sleepMinutes: Int = 0,
    sleepEndAtMs: Long = 0L) {
    Box(Modifier.fillMaxWidth()) {
    val penguinMotion = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.stone_penguin), stringResource(R.string.stone),
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable {
                onPenguinClick()
                scope.launch {
                    penguinMotion.snapTo(0f)
                    penguinMotion.animateTo(1f, tween(180))
                    penguinMotion.animateTo(0f, tween(260))
                }
            }.graphicsLayer {
                rotationZ = (penguinMotion.value * 8f) - 4f
                translationY = (kotlin.math.sin(penguinMotion.value * Math.PI) * -3.5f).toFloat()
            }, contentScale = ContentScale.Fit)
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (quickActions.isNotEmpty()) {
                quickActions.take(8).forEach { action ->
                    val active = action.id in activeQuickActionIds
                    if (action.id == "sleep" && sleepMinutes > 0) {
                        SleepTimerIndicator(sleepMinutes, Modifier.size(44.dp),
                            endAtMillis = sleepEndAtMs, showLabel = false) { onQuickAction(action.id) }
                    } else IconButton(
                        onClick = { onQuickAction(action.id) },
                        modifier = Modifier.size(44.dp).then(
                            if (active) Modifier.background(Color(0xFFF5C518), CircleShape)
                            else Modifier
                        )
                    ) {
                        Icon(action.icon, if (active) "${action.label} (Activado)" else action.label,
                            tint = if (active) Color(0xFF0A0A0E) else Color(0xFFF5C518))
                    }
                }
                if (quickActions.size > 8) IconButton(onClick = onMoreQuickActions, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.MoreHoriz, stringResource(R.string.more_options), tint = Color(0xFFF5C518))
                }
            } else {
                if (showSearch) StoneIconButton(StoneGlyph.Search, stringResource(R.string.search_toggle), onSearch)
                if (showFolder) StoneIconButton(StoneGlyph.Folder, stringResource(R.string.choose_folder), onFolder, enabled = !scanning)
                if (showScan) StoneIconButton(StoneGlyph.Scan, stringResource(R.string.scan_folders), onScan, enabled = !scanning)
                if (showSort) StoneIconButton(StoneGlyph.Sort, stringResource(R.string.sort), onSort)
                if (showEqualizer) IconButton(onClick = onEqualizer, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.GraphicEq, stringResource(R.string.equalizer), tint = Color(0xFFF5C518))
                }
                if (showSleep && sleepMinutes > 0) SleepTimerIndicator(sleepMinutes,
                    Modifier.size(44.dp), endAtMillis = sleepEndAtMs, showLabel = false,
                    onClick = onSleep)
                else if (showSleep) IconButton(onClick = onSleep, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Timer, stringResource(R.string.sleep_timer), tint = Color(0xFFF5C518))
                }
            }
        }
        StoneIconButton(StoneGlyph.Settings, stringResource(R.string.settings), onSettings)
    }
    }
}
