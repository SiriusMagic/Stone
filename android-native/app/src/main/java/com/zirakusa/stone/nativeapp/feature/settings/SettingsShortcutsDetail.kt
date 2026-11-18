package com.zirakusa.stone.nativeapp.feature.settings

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.feature.library.QuickActionDefinition
import com.zirakusa.stone.nativeapp.feature.library.QuickActionRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import com.zirakusa.stone.nativeapp.feature.settings.api.color
import com.zirakusa.stone.nativeapp.feature.settings.api.icon
import com.zirakusa.stone.nativeapp.feature.settings.api.label

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun HomeShortcutDetail(store: StoneSettingsStore?,
    onChange: (String, Boolean) -> Unit) {
    val legacy = listOf(
        QuickActionDefinition("sort", "Ordenar", "Ordenar biblioteca", Icons.Default.Sort, true))
    val legacyIds = remember { legacy.map { it.id } }
    val defaultVisibleIds = legacyIds.toSet() + QuickActionRegistry.defaultVisibleIds
    val items = remember {
        legacy + QuickActionRegistry.preferredIds.mapNotNull { id ->
            QuickActionRegistry.definitions.find { it.id == id }
        } + QuickActionRegistry.definitions.filterNot { it.id in QuickActionRegistry.preferredIds }
    }
    var order by remember(store) { mutableStateOf(store?.shortcutOrder(items.map { it.id }) ?: items.map { it.id }) }
    var visible by remember(store) { mutableStateOf(items.associate { it.id to
        (store?.shortcutVisible(it.id, it.id in defaultVisibleIds) ?: (it.id in defaultVisibleIds)) }) }
    fun preserveCustomVisibility() {
        visible.forEach { (key, value) -> store?.setCustomShortcutVisible(key, value) }
    }
    fun enterCustomProfile() {
        if (store?.shortcutProfile() != "custom") {
            preserveCustomVisibility()
            store?.setShortcutProfile("custom")
        }
    }
    Text(stringResource(R.string.home_shortcut_hint), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 10.dp))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("minimal" to "Minimalista", "basic" to "Común",
            "audiophile" to "Audiófilo", "custom" to "Personalizado").forEach { (id, label) ->
            FilterChip(selected = store?.shortcutProfile() == id, onClick = {
                if (id != "custom" && store?.shortcutProfile() == "custom") preserveCustomVisibility()
                store?.setShortcutProfile(id)
                val enabled = when (id) {
                    "minimal" -> QuickActionRegistry.pinnedIds.toSet()
                    "basic" -> QuickActionRegistry.pinnedIds.toSet()
                    "audiophile" -> QuickActionRegistry.pinnedIds.toSet() + setOf(
                        "audio_mode_battery", "audio_mode_balanced", "audio_mode_hifi",
                        "audio_mode_sleepy", "diagnostics")
                    "custom" -> items.filter { item ->
                        store?.customShortcutVisible(item.id, visible[item.id] == true)
                            ?: (visible[item.id] == true)
                    }.map { it.id }.toSet()
                    else -> emptySet()
                }
                visible = visible.mapValues { it.key in enabled }
                visible.forEach { (key, value) ->
                    store?.setShortcutVisible(key, value)
                    if (id == "custom") store?.setCustomShortcutVisible(key, value)
                    onChange(key, value)
                }
            }, label = { Text(label) })
        }
    }
    OutlinedButton(onClick = {
        store?.setShortcutProfile("custom")
        order = items.map { it.id }; store?.setShortcutOrder(order)
        visible = items.associate { it.id to (it.id in defaultVisibleIds) }
        visible.forEach { (key, value) ->
            store?.setShortcutVisible(key, value)
            store?.setCustomShortcutVisible(key, value)
            onChange(key, value)
        }
    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_defaults)) }
    order.forEachIndexed { index, key ->
        val item = items.find { it.id == key } ?: return@forEachIndexed
        fun moveShortcut(direction: Int) {
            enterCustomProfile()
            val currentIndex = order.indexOf(key)
            val targetIndex = currentIndex + direction
            if (currentIndex !in order.indices || targetIndex !in order.indices) return
            order = order.toMutableList().also {
                it.add(targetIndex, it.removeAt(currentIndex))
            }
            store?.setShortcutOrder(order)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, item.label, tint = StonePalette.Yellow)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.label, fontWeight = FontWeight.SemiBold)
                Text(if (item.available) item.description else "No disponible en esta versión", color = StonePalette.Muted, fontSize = 12.sp)
            }
            RepeatingReorderButton(index > 0, Icons.Default.KeyboardArrowUp, "Subir") {
                moveShortcut(-1)
            }
            RepeatingReorderButton(index < order.lastIndex, Icons.Default.KeyboardArrowDown, "Bajar") {
                moveShortcut(1)
            }
            Switch(checked = visible[key] == true, enabled = item.available, onCheckedChange = { enabled ->
                enterCustomProfile()
                store?.setCustomShortcutVisible(key, enabled)
                store?.setShortcutVisible(key, enabled)
                visible = visible + (key to enabled)
                onChange(key, enabled)
            })
        }
    }
}

/** A tap moves once; holding the control keeps moving the same shortcut. */
@Composable internal fun RepeatingReorderButton(enabled: Boolean, icon: ImageVector,
    description: String, onMove: () -> Unit) {
    val latestMove by rememberUpdatedState(onMove)
    Box(
        modifier = Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else .35f)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = description) {
                    if (!enabled) return@onClick false
                    latestMove()
                    true
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val gestureScope = CoroutineScope(coroutineContext)
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var repeated = false
                    val repeatJob = gestureScope.launch {
                        delay(android.view.ViewConfiguration.getLongPressTimeout().toLong())
                        repeated = true
                        latestMove()
                        while (isActive) {
                            delay(120)
                            latestMove()
                        }
                    }
                    val up = waitForUpOrCancellation()
                    repeatJob.cancel()
                    if (!repeated && up != null) latestMove()
                    if (repeated) up?.consume()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null)
    }
}
