package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.feature.player.VideoControlRegistry
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@Composable fun VideoControlSettingsDetail(store: StoneSettingsStore?, onChanged: () -> Unit) {
    val defaults = remember { VideoControlRegistry.defaultIds }
    var order by remember(store) {
        mutableStateOf(store?.videoControlOrder(defaults.map { it.key })
            ?.mapNotNull { key -> defaults.firstOrNull { it.key == key } } ?: defaults)
    }
    var visible by remember(store) {
        mutableStateOf(defaults.associateWith { id -> store?.videoControlVisible(id.key, true) ?: true })
    }
    Text(
        stringResource(R.string.video_controls_hint),
        color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    Text(
        stringResource(R.string.video_controls_future),
        color = StonePalette.Yellow,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    OutlinedButton(
        onClick = {
            order = defaults
            visible = defaults.associateWith { true }
            store?.setVideoControlOrder(defaults.map { it.key })
            defaults.forEach { store?.setVideoControlVisible(it.key, true) }
            onChanged()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.restore_defaults))
    }
    order.forEachIndexed { index, id ->
        val definition = VideoControlRegistry.definition(id)
        fun move(direction: Int) {
            val target = index + direction
            if (target !in order.indices) return
            order = order.toMutableList().also { values ->
                values.add(target, values.removeAt(index))
            }
            store?.setVideoControlOrder(order.map { it.key })
            onChanged()
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(definition.icon, stringResource(definition.label), tint = StonePalette.Yellow)
            Text(
                stringResource(definition.label),
                Modifier.weight(1f).padding(horizontal = 12.dp),
                fontWeight = FontWeight.SemiBold,
            )
            RepeatingReorderButton(index > 0, Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up)) {
                move(-1)
            }
            RepeatingReorderButton(index < order.lastIndex, Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down)) {
                move(1)
            }
            Switch(checked = visible[id] == true, onCheckedChange = { enabled ->
                visible = visible + (id to enabled)
                store?.setVideoControlVisible(id.key, enabled)
                onChanged()
            })
        }
    }
}
