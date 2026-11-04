package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.feature.player.SleepTimerContent
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.playback.AudioMode
import com.zirakusa.stone.nativeapp.playback.HardwareAcceleration
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.data.transfer.LocalTransferUiState
import com.zirakusa.stone.nativeapp.playback.PerformanceDiagnostics
import kotlinx.coroutines.launch

private data class Language(val code: String, val label: String)
private val languages = listOf(Language("es", "Español"), Language("en", "English"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsPanel(theme: String, onTheme: (String) -> Unit, language: String,
    onLanguage: (String) -> Unit, store: StoneSettingsStore?, page: String?,
    onPage: (String?) -> Unit, onClose: () -> Unit, onFolder: () -> Unit,
    onScan: () -> Unit, onLibrarySheet: (String) -> Unit, playback: PlaybackViewModel?,
    onExport: () -> Unit, onImport: () -> Unit, onShare: () -> Unit,
    onExportPackage: () -> Unit = {}, onImportPackage: () -> Unit = {}, onTransfer: () -> Unit = {},
    transferState: LocalTransferUiState = LocalTransferUiState(),
    onHostTransfer: () -> Unit = {}, onReceiveTransfer: (String, String) -> Unit = { _, _ -> },
    onStopTransfer: () -> Unit = {},
    onShortcutChange: (String, Boolean) -> Unit = { _, _ -> }) {
    var shareAppUnavailable by remember { mutableStateOf(false) }
    BackHandler(enabled = page != null, onBack = { onPage(null) })
    ModalBottomSheet(onDismissRequest = onClose, containerColor = StonePalette.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(.88f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            if (page == null) {
                Text(stringResource(R.string.settings), fontSize = 27.sp, fontWeight = FontWeight.Black)
                SettingsGroup {
                    SettingsRow(Icons.Default.Folder, R.string.settings_stone_folder, "") { onClose(); onFolder() }
                    SettingsRow(Icons.Default.Refresh, R.string.settings_scan_library, "") { onClose(); onScan() }
                    SettingsRow(Icons.Default.Search, R.string.settings_scan_mode, "") { onPage("scan-mode") }
                    SettingsRow(Icons.Default.Checkroom, R.string.settings_theme, themeLabel(theme)) { onPage("theme") }
                    SettingsRow(Icons.Default.Language, R.string.settings_language,
                        languages.find { it.code == language }?.label.orEmpty()) { onPage("language") }
                    SettingsRow(Icons.Default.Apps, R.string.settings_home_shortcuts, "") { onPage("shortcuts") }
                    SettingsRow(Icons.Default.Headphones, R.string.settings_playback, "") { onPage("playback") }
                    SettingsRow(Icons.Default.Notifications, R.string.settings_notifications, "") { onPage("notifications") }
                    SettingsRow(Icons.Default.Timer, R.string.sleep_timer, "") { onPage("sleep") }
                    SettingsRow(Icons.Default.SwapHoriz, R.string.transfer_library, "") { onPage("library-transfer") }
                }
                SettingsGroup {
                    SettingsRow(Icons.Default.VisibilityOff, R.string.hidden_files, "") { onClose(); onLibrarySheet("hidden") }
                    SettingsRow(Icons.Default.Delete, R.string.recently_deleted, "") { onClose(); onLibrarySheet("deleted") }
                }
                SettingsGroup {
                    SettingsRow(Icons.Default.Share, R.string.share_app, "") { shareAppUnavailable = true }
                }
                SettingsGroup {
                    SettingsRow(Icons.Default.Archive, R.string.export_musp, "") { onClose(); onExportPackage() }
                    SettingsRow(Icons.Default.Unarchive, R.string.import_musp, "") { onClose(); onImportPackage() }
                }
                SettingsGroup {
                    SettingsRow(Icons.Default.ThumbUp, R.string.feedback, "") { onPage("feedback") }
                    SettingsRow(Icons.Default.Info, R.string.about_stone, "") { onPage("about") }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onPage(null) }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
                    Text(pageTitle(page), fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
                when (page) {
                    "theme" -> listOf("gray" to R.string.theme_gray,
                        "dark" to R.string.theme_dark).forEach { (value, label) ->
                        SettingsChoice(label, theme == value) { onTheme(value) }
                    }
                    "language" -> languages.forEach { item ->
                        SettingsChoiceText(item.label, language == item.code) { onLanguage(item.code) }
                    }
                    "shortcuts" -> HomeShortcutDetail(store, onShortcutChange)
                    "playback" -> PlaybackDetail(store, playback, { onPage("audio-mode") },
                        { onPage("hardware-acceleration") }, { onPage("audio-diagnostics") })
                    "audio-mode" -> AudioModeDetail(playback)
                    "hardware-acceleration" -> HardwareAccelerationDetail(playback)
                    "scan-mode" -> ScanModeDetail(store)
                    "feedback" -> FeedbackDetail()
                    "about" -> AboutDetail()
                    "notifications" -> NotificationDetail(store)
                    "sleep" -> SleepDetail(playback, onClose = { onPage(null) })
                    "library-transfer" -> LibraryTransferDetail(onShare, transferState, onHostTransfer,
                        onReceiveTransfer, onStopTransfer)
                    "wifi-transfer" -> TransferDetail(transferState, onHostTransfer,
                        onReceiveTransfer, onStopTransfer)
                    "audio-diagnostics" -> AudioDiagnosticsDetail(playback)
                }
            }
        }
    }
    if (shareAppUnavailable) AlertDialog(
        onDismissRequest = { shareAppUnavailable = false },
        title = { Text(stringResource(R.string.share_app)) },
        text = { Text(stringResource(R.string.share_app_unavailable)) },
        confirmButton = {
            TextButton(onClick = { shareAppUnavailable = false }) {
                Text(stringResource(R.string.dismiss))
            }
        })
}

@Composable private fun themeLabel(theme: String) = stringResource(when (theme) {
    "light" -> R.string.theme_light; "gray" -> R.string.theme_gray; else -> R.string.theme_dark
})

@Composable private fun pageTitle(page: String) = stringResource(when (page) {
    "theme" -> R.string.settings_theme; "language" -> R.string.settings_language
    "shortcuts" -> R.string.settings_home_shortcuts
    "playback" -> R.string.settings_playback; "audio-mode" -> R.string.audio_mode
    "hardware-acceleration" -> R.string.hardware_acceleration
    "scan-mode" -> R.string.settings_scan_mode
    "feedback" -> R.string.feedback; "about" -> R.string.about_stone
    "notifications" -> R.string.settings_notifications
    "sleep" -> R.string.sleep_timer; "wifi-transfer" -> R.string.wifi_transfer
    "library-transfer" -> R.string.transfer_library
    "audio-diagnostics" -> R.string.audio_diagnostics; else -> R.string.settings
})

@Composable private fun TransferDetail(state: LocalTransferUiState, host: () -> Unit,
    receive: (String, String) -> Unit, stop: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    Text(stringResource(R.string.wifi_transfer_intro), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 18.dp))
    Button(enabled = !state.busy, onClick = host, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Wifi, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.start_transfer))
    }
    if (state.address.isNotBlank()) {
        Text(stringResource(R.string.transfer_share_address, state.address), fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp))
        Text(stringResource(R.string.transfer_share_code, state.code), fontSize = 22.sp,
            fontWeight = FontWeight.Black, color = StonePalette.Yellow)
        Text(stringResource(R.string.transfer_waiting), color = StonePalette.Muted)
        TextButton(onClick = stop) { Text(stringResource(R.string.cancel)) }
    }
    HorizontalDivider(Modifier.padding(vertical = 18.dp))
    Text(stringResource(R.string.transfer_receive_title), fontWeight = FontWeight.Bold)
    OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
        label = { Text(stringResource(R.string.transfer_address)) }, singleLine = true,
        placeholder = { Text("192.168.1.20:43210") })
    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth().padding(top = 8.dp),
        label = { Text(stringResource(R.string.transfer_code)) }, singleLine = true)
    Button(enabled = !state.busy && address.isNotBlank() && code.length == 6,
        onClick = { receive(address, code) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(stringResource(R.string.transfer_receive))
    }
    if (state.busy) {
        LinearProgressIndicator(progress = { state.progress }, Modifier.fillMaxWidth().padding(top = 16.dp))
        Text(stringResource(R.string.transfer_working), color = StonePalette.Muted)
    }
    if (state.message.isNotBlank()) Text(state.message, color = StonePalette.Muted,
        modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
}

@Composable private fun LibraryTransferDetail(bluetooth: () -> Unit, state: LocalTransferUiState,
    host: () -> Unit, receive: (String, String) -> Unit, stop: () -> Unit) {
    var wifiSelected by remember { mutableStateOf(false) }
    SettingsGroup {
        SettingsRow(Icons.Default.Bluetooth, R.string.bluetooth_transfer, "") { bluetooth() }
        SettingsRow(Icons.Default.Wifi, R.string.wifi_transfer, "") {
            wifiSelected = true
        }
    }
    if (wifiSelected) TransferDetail(state, host, receive, stop)
}

@Composable private fun HomeShortcutDetail(store: StoneSettingsStore?,
    onChange: (String, Boolean) -> Unit) {
    val items = listOf(
        "search" to R.string.search_toggle,
        "folder" to R.string.settings_stone_folder,
        "scan" to R.string.settings_scan_library,
        "downloads" to R.string.downloads,
        "sort" to R.string.sort,
        "equalizer" to R.string.equalizer,
        "sleep" to R.string.sleep_timer,
    )
    var visible by remember(store) { mutableStateOf(items.associate { (key, _) ->
        key to (store?.shortcutVisible(key) ?: true)
    }) }
    Text(stringResource(R.string.home_shortcut_hint), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 10.dp))
    items.forEach { (key, label) ->
        ToggleSetting(label, R.string.home_shortcut_toggle_hint, visible[key] == true) { enabled ->
            store?.setShortcutVisible(key, enabled)
            visible = visible + (key to enabled)
            onChange(key, enabled)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FeedbackDetail() {
    var selected by remember { mutableStateOf(R.string.feedback_other) }
    var details by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) {
        attachments = it.take(4)
    }
    val categories = listOf(R.string.feedback_lyrics, R.string.feedback_ads,
        R.string.feedback_missing_file, R.string.feedback_unplayable,
        R.string.feedback_stops, R.string.feedback_slow, R.string.feedback_other)
    Text(stringResource(R.string.feedback_intro), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 14.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { category ->
            FilterChip(selected = selected == category, onClick = { selected = category },
                label = { Text(stringResource(category)) })
        }
    }
    Text(stringResource(R.string.feedback_details), fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    OutlinedTextField(value = details, onValueChange = { details = it.take(500) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), minLines = 5,
        supportingText = { Text("${details.length}/500") },
        placeholder = { Text(stringResource(R.string.feedback_details_hint)) })
    Text(stringResource(R.string.feedback_attachments), fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.size(82.dp)) {
        Text("+", fontSize = 28.sp)
    }
    if (attachments.isNotEmpty()) Text(stringResource(R.string.feedback_attachment_count, attachments.size),
        color = StonePalette.Muted, fontSize = 12.sp)
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Top) {
        Checkbox(checked = consent, onCheckedChange = { consent = it })
        Text(stringResource(R.string.feedback_consent), color = StonePalette.Muted,
            fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    }
    Button(onClick = { }, enabled = details.isNotBlank() && consent,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)) {
        Text(stringResource(R.string.feedback_submit))
    }
}

@Composable private fun AboutDetail() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(18.dp))
        androidx.compose.foundation.Image(painterResource(R.drawable.stone_penguin), stringResource(R.string.stone),
            Modifier.size(112.dp), contentScale = ContentScale.Fit)
        Text(stringResource(R.string.stone), fontSize = 24.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 14.dp))
        Text(stringResource(R.string.version_label, "1.5.0 experimental"), color = StonePalette.Muted,
            modifier = Modifier.padding(top = 10.dp))
        SettingsGroup {
            SettingsRow(Icons.Default.ThumbUp, R.string.rate_stone, "") { }
            SettingsRow(Icons.Default.Share, R.string.share_stone, "") {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Stone — reproductor de música local")
                }, context.getString(R.string.share_stone)))
            }
        }
        SettingsGroup {
            SettingsRow(Icons.Default.Policy, R.string.privacy_policy, "") { }
            SettingsRow(Icons.Default.Description, R.string.terms_service, "") { }
            SettingsRow(Icons.Default.Verified, R.string.open_source_licenses, "") { }
        }
        Text(stringResource(R.string.copyright_stone), color = StonePalette.Muted,
            fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp))
    }
}

@Composable private fun ScanModeDetail(store: StoneSettingsStore?) {
    val initial = store?.read()?.autoRescan ?: true
    var autoRescan by remember(store) { mutableStateOf(initial) }
    Text(stringResource(R.string.scan_mode_intro), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 12.dp))
    ToggleSetting(R.string.auto_rescan, R.string.auto_rescan_hint, autoRescan) { enabled ->
        store?.setBoolean("autoRescan", enabled)
        autoRescan = enabled
    }
    Text(stringResource(R.string.manual_scan_hint), color = StonePalette.Muted,
        modifier = Modifier.padding(top = 18.dp, bottom = 20.dp))
}

@Composable private fun SettingsChoice(label: Int, selected: Boolean, action: () -> Unit) =
    SettingsChoiceText(stringResource(label), selected, action)

@Composable private fun SettingsChoiceText(label: String, selected: Boolean, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp).clickable(onClick = action), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
            tint = if (selected) StonePalette.Yellow else StonePalette.Muted)
    }
}

@Composable private fun PlaybackDetail(store: StoneSettingsStore?, playback: PlaybackViewModel?,
    openAudioMode: () -> Unit, openHardwareAcceleration: () -> Unit, openDiagnostics: () -> Unit) {
    var config by remember { mutableStateOf(store?.read()) }
    val playbackState = playback?.state?.collectAsState()?.value
    PlaybackModeRow(playbackState?.audioMode ?: AudioMode.BALANCED, openAudioMode)
    HardwareAccelerationRow(playbackState?.hardwareAcceleration ?: HardwareAcceleration.AUTOMATIC,
        openHardwareAcceleration)
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = openDiagnostics),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Memory, null, tint = StonePalette.Yellow, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            Text(stringResource(R.string.audio_diagnostics), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.audio_diagnostics_hint), color = StonePalette.Muted, fontSize = 13.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = StonePalette.Muted)
    }
    ToggleSetting(R.string.play_with_others, R.string.play_with_others_hint, config?.playWithOthers == true) {
        playback?.setPlayWithOthers(it); config = store?.read()
    }
    Text(stringResource(R.string.gapless), fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp))
    Text(stringResource(R.string.gapless_hint), color = StonePalette.Muted)
    Text(stringResource(R.string.crossfade), fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp))
    Text(stringResource(R.string.crossfade_hint), color = StonePalette.Muted)
}

@Composable private fun AudioDiagnosticsDetail(playback: PlaybackViewModel?) {
    val context = LocalContext.current
    val mode = playback?.state?.collectAsState()?.value?.audioMode ?: AudioMode.BALANCED
    var snapshot by remember { mutableStateOf(PerformanceDiagnostics.read(context)) }
    var samples by remember { mutableStateOf<List<com.zirakusa.stone.nativeapp.playback.PerformanceSnapshot>>(emptyList()) }
    var summary by remember { mutableStateOf<com.zirakusa.stone.nativeapp.playback.DiagnosticSessionSummary?>(null) }
    var running by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(30_000L) }
    val scope = rememberCoroutineScope()
    Text(stringResource(R.string.audio_diagnostics_intro), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 16.dp))
    Button(enabled = !running, onClick = { snapshot = PerformanceDiagnostics.read(context) },
        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.refresh_metrics)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(30_000L to "30 s", 60_000L to "1 min", 300_000L to "5 min").forEach { (value, label) ->
            FilterChip(selected = duration == value, onClick = { duration = value }, label = { Text(label) })
        }
    }
    Button(enabled = !running, onClick = {
        running = true; samples = emptyList()
        scope.launch {
            summary = PerformanceDiagnostics.session(context, duration) { sample ->
                snapshot = sample; samples = samples + sample
            }
            running = false
        }
    }, modifier = Modifier.fillMaxWidth()) { Text(if (running) stringResource(R.string.diagnostics_running) else stringResource(R.string.start_diagnostics)) }
    summary?.let { result ->
        OutlinedButton(onClick = { com.zirakusa.stone.nativeapp.playback.DiagnosticsReportExporter.share(context, result) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.export_diagnostics))
        }
    }
    if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
    DiagnosticSection(stringResource(R.string.diagnostics_summary)) {
        DiagnosticValue(stringResource(R.string.audio_mode), mode.name)
        DiagnosticValue(stringResource(R.string.diagnostic_status), diagnosticStatus(snapshot))
        DiagnosticValue(stringResource(R.string.ram_used), "${snapshot.usedRamMb} MB")
        DiagnosticValue(stringResource(R.string.battery_level), if (snapshot.batteryPercent >= 0) "${snapshot.batteryPercent}%" else "No disponible")
    }
    DiagnosticSection(stringResource(R.string.diagnostics_cpu)) {
        DiagnosticValue(stringResource(R.string.cpu_stone), snapshot.cpuPercent?.let { "%.1f%%".format(it) } ?: "Calculando")
        DiagnosticValue(stringResource(R.string.cpu_threads), snapshot.threadCount.toString())
        DiagnosticValue(stringResource(R.string.cpu_cores), snapshot.cpuCount.toString())
    }
    DiagnosticSection(stringResource(R.string.diagnostics_memory)) {
        DiagnosticValue(stringResource(R.string.pss_total), "${snapshot.pssTotalMb} MB")
        DiagnosticValue(stringResource(R.string.pss_native), "${snapshot.pssNativeMb} MB")
        DiagnosticValue(stringResource(R.string.pss_dalvik), "${snapshot.pssDalvikMb} MB")
        DiagnosticValue(stringResource(R.string.process_heap), "${snapshot.processHeapMb} MB")
        DiagnosticValue(stringResource(R.string.native_heap), "${snapshot.nativeHeapMb} MB")
        DiagnosticValue(stringResource(R.string.ram_available), "${snapshot.availableRamMb} MB")
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
        DiagnosticValue(stringResource(R.string.session_samples), samples.size.toString())
        Text(stringResource(R.string.diagnostics_limitations), color = StonePalette.Muted, fontSize = 12.sp)
    }
}

@Composable private fun DiagnosticSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        TextButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Black)
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (open) Column(content = content)
    }
}

private fun diagnosticStatus(snapshot: com.zirakusa.stone.nativeapp.playback.PerformanceSnapshot): String = when {
    snapshot.thermalStatus in setOf("Grave", "Crítico", "Emergencia") -> "Crítico"
    snapshot.lowMemory || (snapshot.cpuPercent ?: 0.0) > 85 -> "Atención"
    else -> "Normal"
}

@Composable private fun DiagnosticValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(value, color = StonePalette.Muted)
    }
}

@Composable private fun HardwareAccelerationRow(mode: HardwareAcceleration, open: () -> Unit) {
    val label = stringResource(when (mode) {
        HardwareAcceleration.AUTOMATIC -> R.string.accel_automatic
        HardwareAcceleration.DISABLED -> R.string.accel_disabled
        HardwareAcceleration.DECODING -> R.string.accel_decoding
        HardwareAcceleration.FULL -> R.string.accel_full
        HardwareAcceleration.ADAPTIVE -> R.string.accel_adaptive
    })
    Row(Modifier.fillMaxWidth().heightIn(min = 82.dp).clickable(onClick = open),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Tune, null, tint = StonePalette.Yellow, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            Text(stringResource(R.string.hardware_acceleration), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(label, color = StonePalette.Muted, fontSize = 14.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = StonePalette.Muted)
    }
}

@Composable private fun PlaybackModeRow(mode: AudioMode, open: () -> Unit) {
    val label = stringResource(when (mode) {
        AudioMode.BATTERY -> R.string.audio_mode_battery
        AudioMode.BALANCED -> R.string.audio_mode_balanced
        AudioMode.HIFI -> R.string.audio_mode_hifi
    })
    Row(Modifier.fillMaxWidth().heightIn(min = 82.dp).clickable(onClick = open),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Tune, null, tint = StonePalette.Yellow, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            Text(stringResource(R.string.audio_mode), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(label, color = StonePalette.Muted, fontSize = 14.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = StonePalette.Muted)
    }
}

@Composable private fun AudioModeDetail(playback: PlaybackViewModel?) {
    val current = playback?.state?.collectAsState()?.value?.audioMode ?: AudioMode.BALANCED
    Text(stringResource(R.string.audio_mode_intro), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 10.dp))
    AudioMode.entries.forEach { mode ->
        val (title, detail, icon) = when (mode) {
            AudioMode.BATTERY -> Triple(R.string.audio_mode_battery, R.string.audio_mode_battery_info,
                Icons.Default.BatteryFull)
            AudioMode.BALANCED -> Triple(R.string.audio_mode_balanced, R.string.audio_mode_balanced_info,
                Icons.Default.Balance)
            AudioMode.HIFI -> Triple(R.string.audio_mode_hifi, R.string.audio_mode_hifi_info,
                Icons.Default.GraphicEq)
        }
        Row(Modifier.fillMaxWidth().clickable { playback?.setAudioMode(mode) }
            .padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = StonePalette.Yellow, modifier = Modifier.size(25.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(stringResource(title), fontWeight = FontWeight.Bold)
                Text(stringResource(detail), color = StonePalette.Muted, fontSize = 13.sp)
            }
            Icon(if (current == mode) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                null, tint = if (current == mode) StonePalette.Yellow else StonePalette.Muted)
        }
    }
}

@Composable private fun HardwareAccelerationDetail(playback: PlaybackViewModel?) {
    Text(stringResource(R.string.hardware_acceleration_intro), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 10.dp))
    val current = playback?.state?.collectAsState()?.value?.hardwareAcceleration
        ?: HardwareAcceleration.AUTOMATIC
    HardwareAcceleration.entries.forEach { mode ->
        val (title, detail, icon) = when (mode) {
            HardwareAcceleration.AUTOMATIC -> Triple(R.string.accel_automatic, R.string.accel_automatic_info, Icons.Default.Settings)
            HardwareAcceleration.DISABLED -> Triple(R.string.accel_disabled, R.string.accel_disabled_info, Icons.Default.Block)
            HardwareAcceleration.DECODING -> Triple(R.string.accel_decoding, R.string.accel_decoding_info, Icons.Default.Memory)
            HardwareAcceleration.FULL -> Triple(R.string.accel_full, R.string.accel_full_info, Icons.Default.Speed)
            HardwareAcceleration.ADAPTIVE -> Triple(R.string.accel_adaptive, R.string.accel_adaptive_info, Icons.Default.Tune)
        }
        Row(Modifier.fillMaxWidth().clickable { playback?.setHardwareAcceleration(mode) }
            .padding(vertical = 13.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = StonePalette.Yellow, modifier = Modifier.size(25.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(stringResource(title), fontWeight = FontWeight.Bold)
                Text(stringResource(detail), color = StonePalette.Muted, fontSize = 13.sp)
            }
            Icon(if (current == mode) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                null, tint = if (current == mode) StonePalette.Yellow else StonePalette.Muted)
        }
    }
    Text(stringResource(R.string.hardware_acceleration_note), color = StonePalette.Muted,
        fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 20.dp))
}

@Composable private fun NotificationDetail(store: StoneSettingsStore?) {
    var reminders by remember(store) { mutableStateOf(store?.read()?.musicReminders ?: false) }
    var newFiles by remember(store) { mutableStateOf(store?.read()?.notifyNewFiles ?: false) }
    var push by remember(store) { mutableStateOf(store?.read()?.pushNotifications ?: false) }
    ToggleSetting(R.string.music_reminders, R.string.music_reminders_hint, reminders) { enabled ->
        store?.setBoolean("musicReminders", enabled)
        reminders = enabled
    }
    if (reminders) {
        Text(stringResource(R.string.morning_reminder), fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 18.dp))
        Text(stringResource(R.string.morning_reminder_time), color = StonePalette.Muted)
        Text(stringResource(R.string.evening_reminder), fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp))
        Text(stringResource(R.string.evening_reminder_time), color = StonePalette.Muted)
        Text(stringResource(R.string.reminder_play_choice), fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 18.dp))
        Text(stringResource(R.string.reminder_not_implemented), color = StonePalette.Muted,
            fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
    }
    ToggleSetting(R.string.notify_new_files, R.string.notify_new_files_hint, newFiles) { enabled ->
        store?.setBoolean("notifyNewFiles", enabled); newFiles = enabled
    }
    Text(stringResource(R.string.notifications_unavailable), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 18.dp))
    ToggleSetting(R.string.push_notifications, R.string.push_notifications_hint, push) { enabled ->
        store?.setBoolean("pushNotifications", enabled); push = enabled
    }
    Text(stringResource(R.string.push_notifications_hint), color = StonePalette.Muted)
}

@Composable private fun SleepDetail(playback: PlaybackViewModel?, onClose: () -> Unit) {
    val playbackState = playback?.state?.collectAsState()?.value
    if (playback != null && playbackState != null) SleepTimerContent(playbackState, playback, onClose)
}

@Composable private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp), content = content)
}

@Composable private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int,
    value: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(66.dp).clickable(onClick = action), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = StonePalette.Yellow, modifier = Modifier.size(25.dp))
        Text(stringResource(label), Modifier.weight(1f).padding(horizontal = 18.dp), fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold)
        if (value.isNotEmpty()) Text(value, color = StonePalette.Muted)
        Icon(Icons.Default.ChevronRight, null, tint = StonePalette.Muted)
    }
}

@Composable private fun ToggleSetting(title: Int, subtitle: Int, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(stringResource(title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(subtitle), color = StonePalette.Muted, fontSize = 13.sp)
        }
        Switch(value, change)
    }
}
