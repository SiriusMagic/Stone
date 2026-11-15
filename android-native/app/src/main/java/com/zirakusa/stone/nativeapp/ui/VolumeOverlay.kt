package com.zirakusa.stone.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Compact volume card. The thick bar is Android's own range (0-100 %); each dot on the right
 * is +50 % of Stone amplification allowed by the current mode (Balanceado 150 %, Cine 300 %).
 * In Dormilón the label shows the real output (at most 10 %).
 */
@Composable fun VolumeOverlay(state: StonePlaybackService.VolumeUiState, modifier: Modifier = Modifier) {
    val gain = StonePlaybackService.currentModeGain
    val boosted = state.percent > 100
    val accent = StonePalette.Yellow
    var dragging by remember { mutableStateOf<Int?>(null) }
    val percent = dragging ?: state.percent
    val shown = if (gain < 1f) String.format(Locale.US, if (percent * gain < 10) "%.1f" else "%.0f", percent * gain)
        else percent.toString()
    Surface(modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Color(0xF0121116),
        shape = RoundedCornerShape(26.dp), shadowElevation = 12.dp) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Icon(when {
                    gain < 1f -> Icons.Default.Bedtime
                    percent == 0 -> Icons.Default.VolumeMute
                    percent < 50 -> Icons.Default.VolumeDown
                    else -> Icons.Default.VolumeUp
                }, stringResource(R.string.volume), tint = if (boosted) accent else Color(0xFFDADAE0),
                    modifier = Modifier.size(26.dp))
                if (boosted) Text(stringResource(R.string.volume), Modifier.padding(start = 8.dp),
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(" $shown%", color = if (boosted) accent else Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Android range, draggable.
                Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF34333B))
                    .pointerInput(state.maxPercent) {
                        fun at(x: Float) = (x / size.width * 100).roundToInt().coerceIn(0, 100)
                        detectTapGestures { StonePlaybackService.setVolumePercent(at(it.x)) }
                    }
                    .pointerInput(state.maxPercent) {
                        detectHorizontalDragGestures(onDragEnd = { dragging = null }, onDragCancel = { dragging = null }) { change, _ ->
                            val value = (change.position.x / size.width * 100).roundToInt().coerceIn(0, 100)
                            dragging = value
                            StonePlaybackService.setVolumePercent(value)
                        }
                    }) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(percent.coerceAtMost(100) / 100f)
                        .clip(RoundedCornerShape(13.dp)).background(if (boosted) accent else Color(0xFFF2F2F5)))
                }
                // One dot per +50 % of amplification allowed by the mode.
                val steps = ((state.maxPercent - 100) / 50).coerceAtLeast(0)
                repeat(steps) { index ->
                    val level = 100 + (index + 1) * 50
                    Box(Modifier.padding(start = 10.dp).size(14.dp).clip(CircleShape)
                        .background(if (percent >= level) accent else Color(0xFF4A4952))
                        .clickable { StonePlaybackService.setVolumePercent(level) })
                }
            }
        }
    }
}
