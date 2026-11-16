package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.draw.alpha
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.BuildConfig
import com.zirakusa.stone.nativeapp.playback.AudioMode
import com.zirakusa.stone.nativeapp.playback.AudioDiagnostics
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.playback.PerformanceDiagnostics
import kotlinx.coroutines.launch

@Composable internal fun SystemDiagnosticsDetail(playback: PlaybackViewModel?) {
    val context = LocalContext.current
    val mode = playback?.state?.collectAsState()?.value?.audioMode ?: AudioMode.BALANCED
    var snapshot by remember { mutableStateOf(PerformanceDiagnostics.read(context)) }
    var sampleCount by remember { mutableIntStateOf(0) }
    var summary by remember { mutableStateOf<com.zirakusa.stone.nativeapp.playback.DiagnosticSessionSummary?>(null) }
    var running by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(30_000L) }
    val scope = rememberCoroutineScope()
    val status = diagnosticStatus(snapshot)
    val statusColor = when (status) {
        "Crítico" -> Color(0xFFFF6B6B)
        "Atención" -> StonePalette.Yellow
        else -> Color(0xFF55D6BE)
    }
    val modeLabel = when (mode) {
        AudioMode.BATTERY -> stringResource(R.string.audio_mode_battery)
        AudioMode.BALANCED -> stringResource(R.string.audio_mode_balanced)
        AudioMode.HIFI -> stringResource(R.string.audio_mode_hifi)
        AudioMode.SLEEPY -> stringResource(R.string.audio_mode_sleepy)
        AudioMode.CUSTOM -> stringResource(R.string.audio_mode_custom)
    }
    Text(stringResource(R.string.system_diagnostics_hint), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 14.dp))
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = StonePalette.Chip) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, null, tint = StonePalette.Yellow, modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(stringResource(R.string.diagnostics_system_status), fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.audio_diagnostics_intro), color = StonePalette.Muted,
                        fontSize = 12.sp)
                }
                Surface(shape = RoundedCornerShape(50), color = statusColor.copy(alpha = .16f)) {
                    Text(status, color = statusColor, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DiagnosticMetricCard(Icons.Default.Speed, stringResource(R.string.cpu_stone),
            snapshot.cpuPercent?.let { "%.0f%%".format(it) } ?: "—", Modifier.weight(1f))
        DiagnosticMetricCard(Icons.Default.Memory, stringResource(R.string.pss_total),
            snapshot.pssTotalMb.takeIf { it >= 0 }?.let { "$it MB" } ?: "—", Modifier.weight(1f))
        DiagnosticMetricCard(Icons.Default.BatteryFull, stringResource(R.string.battery_level),
            snapshot.batteryPercent.takeIf { it >= 0 }?.let { "$it%" } ?: "—", Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !running, onClick = { snapshot = PerformanceDiagnostics.read(context) },
            modifier = Modifier.weight(1f)) { Text(stringResource(R.string.refresh_metrics)) }
        OutlinedButton(enabled = !running, onClick = {
            running = true; sampleCount = 0
            scope.launch {
                summary = PerformanceDiagnostics.session(context, duration) { sample ->
                    snapshot = sample; sampleCount++
                }
                running = false
            }
        }, modifier = Modifier.weight(1f)) {
            Text(if (running) stringResource(R.string.diagnostics_running) else stringResource(R.string.start_diagnostics))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(30_000L to "30 s", 60_000L to "1 min", 300_000L to "5 min").forEach { (value, label) ->
            FilterChip(selected = duration == value, onClick = { duration = value }, label = { Text(label) })
        }
    }
    summary?.let { result ->
        OutlinedButton(onClick = { com.zirakusa.stone.nativeapp.playback.DiagnosticsReportExporter.share(context, result) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.export_diagnostics))
        }
    }
    if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (BuildConfig.AUDIO_DIAGNOSTICS_ENABLED) {
        val audioDiagnostic by StonePlaybackService.audioDiagnosticUi.collectAsState()
        Surface(Modifier.fillMaxWidth().padding(top = 12.dp),
            shape = RoundedCornerShape(18.dp), color = StonePalette.Chip) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.audio_ab_title), fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.audio_ab_hint), color = StonePalette.Muted,
                        fontSize = 12.sp)
                    Text(if (audioDiagnostic.loudnessEnhancerDisabled)
                        stringResource(R.string.audio_ab_mode_b)
                    else stringResource(R.string.audio_ab_mode_a),
                        color = StonePalette.Yellow, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp))
                }
                Switch(checked = audioDiagnostic.loudnessEnhancerDisabled,
                    onCheckedChange = StonePlaybackService::setDiagnosticBoostDisabled)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    AudioDiagnostics.log(context, "USER_MARKER.sound_amplified",
                        state = StonePlaybackService.diagnosticState(), includeSystemEffects = true)
                }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.audio_ab_mark_loud), fontSize = 11.sp)
                }
                OutlinedButton(onClick = {
                    AudioDiagnostics.log(context, "USER_MARKER.sound_normalized",
                        state = StonePlaybackService.diagnosticState(), includeSystemEffects = true)
                }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.audio_ab_mark_normal), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    DiagnosticSection(stringResource(R.string.diagnostics_summary), initiallyOpen = true) {
        DiagnosticValue(stringResource(R.string.audio_mode), modeLabel)
        DiagnosticValue(stringResource(R.string.diagnostic_status), status)
        DiagnosticValue(stringResource(R.string.device_ram_used), "${snapshot.usedRamMb} MB")
        DiagnosticValue(stringResource(R.string.battery_level), if (snapshot.batteryPercent >= 0) "${snapshot.batteryPercent}%" else "No disponible")
    }
    DiagnosticSection(stringResource(R.string.stone_consumption), initiallyOpen = true) {
        Text(stringResource(R.string.stone_consumption_hint), color = StonePalette.Muted,
            fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        DiagnosticValue(stringResource(R.string.cpu_stone), snapshot.cpuPercent?.let { "%.1f%%".format(it) } ?: "Calculando")
        DiagnosticValue(stringResource(R.string.pss_total), snapshot.pssTotalMb.takeIf { it >= 0 }?.let { "$it MB" } ?: "No disponible")
        DiagnosticValue(stringResource(R.string.pss_native), snapshot.pssNativeMb.takeIf { it >= 0 }?.let { "$it MB" } ?: "No disponible")
        DiagnosticValue(stringResource(R.string.pss_dalvik), snapshot.pssDalvikMb.takeIf { it >= 0 }?.let { "$it MB" } ?: "No disponible")
        DiagnosticValue(stringResource(R.string.process_heap), "${snapshot.processHeapMb} MB")
        DiagnosticValue(stringResource(R.string.native_heap), snapshot.nativeHeapMb.takeIf { it >= 0 }?.let { "$it MB" } ?: "No disponible")
        DiagnosticValue(stringResource(R.string.cpu_threads), snapshot.threadCount.takeIf { it >= 0 }?.toString() ?: "No disponible")
    }
    DiagnosticSection(stringResource(R.string.diagnostics_device)) {
        val model = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
        DiagnosticValue(stringResource(R.string.diagnostics_device_model), model)
        DiagnosticValue(stringResource(R.string.diagnostics_android), android.os.Build.VERSION.RELEASE)
        DiagnosticValue(stringResource(R.string.diagnostics_api_level), android.os.Build.VERSION.SDK_INT.toString())
    }
    DiagnosticSection(stringResource(R.string.device_memory)) {
        DiagnosticValue(stringResource(R.string.device_ram_used), "${snapshot.usedRamMb} MB")
        DiagnosticValue(stringResource(R.string.ram_available), "${snapshot.availableRamMb} MB")
        DiagnosticValue(stringResource(R.string.low_memory), snapshot.lowMemory.toString())
    }
    DiagnosticSection(stringResource(R.string.diagnostics_audio)) {
        DiagnosticValue(stringResource(R.string.audio_route), snapshot.audioRoute)
        DiagnosticValue(stringResource(R.string.audio_errors), snapshot.errorCount.toString())
        DiagnosticValue(stringResource(R.string.audio_latency), stringResource(R.string.not_available))
    }
    DiagnosticSection(stringResource(R.string.diagnostics_energy)) {
        DiagnosticValue(stringResource(R.string.battery_temperature), snapshot.batteryTemperatureC?.let { "$it °C" } ?: stringResource(R.string.not_available))
        DiagnosticValue(stringResource(R.string.charging_state), if (snapshot.charging) stringResource(R.string.charging) else stringResource(R.string.not_charging))
        DiagnosticValue(stringResource(R.string.thermal_status), snapshot.thermalStatus)
    }
    DiagnosticSection(stringResource(R.string.diagnostics_advanced)) {
        DiagnosticValue(stringResource(R.string.low_memory), snapshot.lowMemory.toString())
        DiagnosticValue(stringResource(R.string.low_memory_threshold), "${snapshot.lowMemoryThresholdMb} MB")
        DiagnosticValue(stringResource(R.string.session_samples), sampleCount.toString())
        Text(stringResource(R.string.diagnostics_limitations), color = StonePalette.Muted, fontSize = 12.sp)
    }
}

@Composable internal fun DiagnosticMetricCard(icon: ImageVector, title: String, value: String,
    modifier: Modifier = Modifier) {
    Surface(modifier.height(92.dp), shape = RoundedCornerShape(18.dp), color = StonePalette.Chip) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = StonePalette.Yellow, modifier = Modifier.size(20.dp))
            Text(title, color = StonePalette.Muted, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(value, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable internal fun DiagnosticSection(title: String, initiallyOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(initiallyOpen) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        TextButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Black)
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (open) Column(content = content)
    }
}

internal fun diagnosticStatus(snapshot: com.zirakusa.stone.nativeapp.playback.PerformanceSnapshot): String = when {
    snapshot.thermalStatus in setOf("Grave", "Crítico", "Emergencia") -> "Crítico"
    snapshot.lowMemory || (snapshot.cpuPercent ?: 0.0) > 85 -> "Atención"
    else -> "Normal"
}

@Composable internal fun DiagnosticValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(value, color = StonePalette.Muted)
    }
}
